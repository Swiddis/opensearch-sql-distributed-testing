import cats.data.NonEmptyList
import org.scalacheck.Prop
import org.scalacheck.Prop.{all, propBoolean}
import org.scalacheck.Test
import org.apache.hc.core5.http.HttpHost
import org.opensearch.client.ResponseException
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient.ClientOptions
import org.opensearch.client.opensearch.generic.{OpenSearchClientException, Requests}
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder
import queries.sql.{Select, SelectQueryGenerator}
import queries.{IndexContext, OpenSearchDataType, QueryContext}

import scala.util.Try

/**
 * Submit a SQL query directly to the provided client
 *
 * @param client An OpenSearch client
 * @param query A SQL query
 * @return The result of the query as an arbitrary/untyped Json object
 */
def runRawSqlQuery(client: OpenSearchClient, query: String): ujson.Value = {
  val untypedClient = client.generic()
  val requestBody = Map("query" -> query)
  val request = Requests
    .builder()
    .endpoint("/_plugins/_sql")
    .method("POST")
    .json(ujson.write(requestBody))
    .build()
  val response = untypedClient.execute(request)
  val responseBody = response.getBody.get()

  ujson.read(responseBody.bodyAsString())
}

/**
 * Makes an educated guess on a good number of threads to use for property checking. A decent handful of threads per
 * processor since we're bound by blocking I/O.
 *
 * In a perfect world ScalaCheck would support async task execution and not need this, but the work of implementing that
 * sort of runner is unlikely to be significantly better spent than just using a worker number that's "good enough".
 *
 * @return The number of workers to use per property check run.
 */
def workerCount(): Int = {
  // Threads are cheap so we can afford a few per CPU
  val maxProcessorThreads = 64 * Runtime.getRuntime.availableProcessors
  val defaultOpenSearchSearchQueueLimit = 1 // Upper bound to avoid 429 rejections
  Math.min(maxProcessorThreads, defaultOpenSearchSearchQueueLimit)
}

/**
 * Load an OpenSearch client to be used in testing.
 *
 * @return The OpenSearch client.
 */
def openSearchClient(): OpenSearchClient = {
  // Hardcoded for now -- this is where to add logic to load one of multiple clusters from a config file, if supporting
  // multiple cluster configs.
  val hosts = Array(HttpHost("http", "localhost", 9200))
  val transport = ApacheHttpClient5TransportBuilder
    .builder(hosts*)
    .setMapper(JacksonJsonpMapper())
    .build()
  OpenSearchClient(transport)
}

// TODO this should be dynamic, and create an actual context on the cluster
// For now we hardcode based on sample data
def createContext(): IndexContext = {
  IndexContext(
    name="opensearch_dashboards_sample_data_ecommerce",
    fields=Map(
    "category" -> OpenSearchDataType.Text,
    "currency" -> OpenSearchDataType.Keyword,
    "customer_first_name" -> OpenSearchDataType.Text,
    "day_of_week_i" -> OpenSearchDataType.Integer,
    "manufacturer" -> OpenSearchDataType.Text,
    "order_date" -> OpenSearchDataType.Date,
    "products.base_price" -> OpenSearchDataType.HalfFloat,
    "total_quantity" -> OpenSearchDataType.Integer,
    "type" -> OpenSearchDataType.Keyword,
    "user" -> OpenSearchDataType.Keyword,
    ),
  )
}

def prettyErrorReport(err: ujson.Value): String = {
  val lines = List(
    ("reason", err("reason").strOpt),
    ("details", err("details").strOpt),
    ("type", err("type").strOpt),
  ).filter(l => l._2.isDefined).map(l => s"error.${l._1} = ${l._2.get}")
  if lines.isEmpty then
    "error = [No error information provided]"
   else {
    lines.mkString("\n")
  }
}

@main def run(): Unit = {
  val workers = workerCount()
  val client = openSearchClient()

  val iContext = createContext()
  val qContext = QueryContext(NonEmptyList(iContext, List()))
  val qGen = SelectQueryGenerator.from(qContext)

  val queryNonErroringProperty = Prop.forAll(qGen) {
    (q: Select) => {
      val query = q.serialize()
      val result = runRawSqlQuery(client, query)
      val errorReport: String = Try("\n" + prettyErrorReport(result("error").obj)).getOrElse("")
      s"query = $query" + errorReport |: result("status").num.toInt == 200
    }
  }

  val selectFloatCmpResult = Test.check(
    Test.Parameters.defaultVerbose.withWorkers(workers),
    queryNonErroringProperty
  )
}
