import cats.data.NonEmptyList
import config.workerCount
import datagen.{IndexContext, IndexCreator, IndexGenerator, QueryContext}
import org.scalacheck.Prop
import org.scalacheck.Test
import org.apache.hc.core5.http.HttpHost
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder
import properties.observability.PropLogger
import properties.{CrashProperties, PropTestClient, SqlTlpProperties}

/** Load an OpenSearch client to be used in testing.
  *
  * @return
  *   The OpenSearch client.
  */
def openSearchClient(): OpenSearchClient = {
  // TODO Hardcoded for now -- this is where to add logic to load one of multiple clusters from a config file, if supporting
  // multiple cluster configs.
  val hosts = Array(HttpHost("http", "localhost", 9200))
  val transport = ApacheHttpClient5TransportBuilder
    .builder(hosts*)
    .setMapper(JacksonJsonpMapper())
    .build()
  OpenSearchClient(transport)
}

/** Generate an [[IndexContext]] linking to an index with some sample data in
  * it.
  *
  * @param client
  *   An OpenSearch client to push the index data to.
  * @return
  *   The [[IndexContext]] containing the index's metadata.
  */
def generateIndexContext(client: OpenSearchClient): IndexContext = {
  val index = IndexGenerator.genIndex()
  IndexCreator.createIndex(client, index)
  index.context
}

/** Executes a batch of property-based tests against an OpenSearch cluster.
  *
  * TODO we currently just output failures to STDOUT, later let's make a result
  * report
  *
  * @param properties
  *   A list of functions that take an OpenSearchClient and a QueryContext, and
  *   construct a ScalaCheck Prop using these parameters.
  */
def runPropertyBatch(
    properties: List[(PropTestClient, QueryContext) => Prop]
): Unit = {
  val client = openSearchClient()
  val propClient = PropTestClient(client)

  val iContext = generateIndexContext(client)
  val qContext = QueryContext(NonEmptyList(iContext, List()))
  System.out.println(s"Running batch using index: $iContext")

  for (property <- properties)
    Test.check(
      Test.Parameters.defaultVerbose
        .withWorkers(workerCount())
        .withMinSuccessfulTests(100)
        .withTestCallback(PropLogger),
      property(propClient, qContext)
    )
}

@main def run(): Unit = {
  val properties = List(
    CrashProperties.makeSqlQuerySuccessProperty,
    CrashProperties.makePplQuerySuccessProperty
//    TlpProperties.makeSqlTlpWhereProperty,
  )

  runPropertyBatch(properties)
}
