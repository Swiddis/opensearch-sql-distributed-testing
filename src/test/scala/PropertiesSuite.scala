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
    // TODO when we fix the 200 status tests (ignored currently), increase minSuccessfulTests and re-enable discard ratios
    super.scalaCheckTestParameters
      .withMinSuccessfulTests(100)
      .withWorkers(workerCount())
//      .withMaxDiscardRatio(0.5)

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

  property("Sanity: SQL 200 status".ignore) {
    CrashProperties.makeSqlQuerySuccessProperty(propClient, queryContext)
  }

  property("Sanity: PPL 200 status".ignore) {
    CrashProperties.makePplQuerySuccessProperty(propClient, queryContext)
  }

  property("TLP: SQL select-where") {
    SqlTlpProperties.makeSimpleTlpWhereProperty(propClient, queryContext)
  }

  property("TLP: SQL select-distinct-where") {
    SqlTlpProperties.makeDistinctTlpWhereProperty(propClient, queryContext)
  }

  property("TLP: PPL source-where".ignore) {
    PplTlpProperties.makeSimpleTlpWhereProperty(propClient, queryContext)
  }

  property("NoREC: SQL select-where") {
    SqlNoRecProperties.makeSqlNoRecWhereProperty(propClient, queryContext)
  }

  property("TLP: SQL sum") {
    SqlTlpProperties.makeAggregateSumTlpProperty(propClient, queryContext)
  }

  property("TLP: SQL min") {
    SqlTlpProperties.makeAggregateMinTlpProperty(propClient, queryContext)
  }

  property("TLP: SQL max") {
    SqlTlpProperties.makeAggregateMaxTlpProperty(propClient, queryContext)
  }

  property("TLP: SQL count") {
    SqlTlpProperties.makeAggregateCountTlpProperty(propClient, queryContext)
  }

  property("TLP: SQL avg") {
    SqlTlpProperties.makeAggregateAvgTlpProperty(propClient, queryContext)
  }
}
