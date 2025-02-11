import cats.data.NonEmptyList
import config.workerCount
import datagen.{IndexContext, IndexCreator, IndexGenerator, QueryContext}
import org.scalacheck.Test
import org.apache.hc.core5.http.HttpHost
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder
import properties.{
  CrashProperties,
  PplTlpProperties,
  PropTestClient,
  SqlNoRecProperties,
  SqlTlpProperties
}

import scala.compiletime.uninitialized

class PropertiesSuite extends munit.ScalaCheckSuite {

  var client: OpenSearchClient = uninitialized
  var propClient: PropTestClient = uninitialized
  var queryContext: QueryContext = uninitialized

  override def scalaCheckTestParameters: Test.Parameters =
    super.scalaCheckTestParameters
      .withMinSuccessfulTests(1000)
      .withWorkers(workerCount())
      .withMaxDiscardRatio(0.1)

  override def beforeAll(): Unit = {
    client = openSearchClient()
    propClient = PropTestClient(client)
  }

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    val indexContext = generateIndexContext(client)
    queryContext = QueryContext(NonEmptyList(indexContext, List()))
    println(s"Using index: $indexContext")
  }

  def openSearchClient(): OpenSearchClient = {
    val hosts = Array(HttpHost("http", "localhost", 9200))
    val transport = ApacheHttpClient5TransportBuilder
      .builder(hosts*)
      .setMapper(JacksonJsonpMapper())
      .build()
    OpenSearchClient(transport)
  }

  def generateIndexContext(client: OpenSearchClient): IndexContext = {
    val index = IndexGenerator.genIndex()
    IndexCreator.createIndex(client, index)
    index.context
  }

  property("receives a 200 status for valid SQL queries") {
    CrashProperties.makeSqlQuerySuccessProperty(propClient, queryContext)
  }

  property("receives a 200 status for valid PPL queries") {
    CrashProperties.makePplQuerySuccessProperty(propClient, queryContext)
  }

  property("simple SQL SELECT-WHERE statements satisfy TLP") {
    SqlTlpProperties.makeSimpleTlpWhereProperty(propClient, queryContext)
  }

  property("simple SQL SELECT-DISTINCT-WHERE statements satisfy TLP") {
    SqlTlpProperties.makeDistinctTlpWhereProperty(propClient, queryContext)
  }

  property("simple PPL SOURCE-WHERE statements satisfy TLP".ignore) {
    PplTlpProperties.makeSimpleTlpWhereProperty(propClient, queryContext)
  }

  property("simple SQL SELECT-WHERE statements satisfy NoREC") {
    SqlNoRecProperties.makeSqlNoRecWhereProperty(propClient, queryContext)
  }

  property("aggregate SUM SQL statements satisfy TLP") {
    SqlTlpProperties.makeAggregateSumTlpProperty(propClient, queryContext)
  }

  property("aggregate MIN SQL statements satisfy TLP") {
    SqlTlpProperties.makeAggregateMinTlpProperty(propClient, queryContext)
  }

  property("aggregate MAX SQL statements satisfy TLP") {
    SqlTlpProperties.makeAggregateMaxTlpProperty(propClient, queryContext)
  }

  property("aggregate COUNT SQL statements satisfy TLP") {
    SqlTlpProperties.makeAggregateCountTlpProperty(propClient, queryContext)
  }

  property("aggregate AVG SQL statements satisfy TLP") {
    SqlTlpProperties.makeAggregateAvgTlpProperty(propClient, queryContext)
  }
}
