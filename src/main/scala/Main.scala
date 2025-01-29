import cats.data.NonEmptyList
import datagen.{IndexContext, IndexCreator, IndexGenerator, QueryContext}
import org.scalacheck.Prop
import org.scalacheck.Test
import org.apache.hc.core5.http.HttpHost
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder
import properties.{CrashProperties, PropTestClient}

/** Makes an educated guess on a good number of threads to use for property
  * checking. A decent handful of threads per processor since we're bound by
  * blocking I/O.
  *
  * In a perfect world ScalaCheck would support async task execution and not
  * need this, but the work of implementing that sort of runner is unlikely to
  * be significantly better spent than just using a worker number that's "good
  * enough".
  *
  * @return
  *   The number of workers to use per property check run.
  */
def workerCount(): Int = {
  // Turns out there's so many bugs being found that we currently crash the cluster by having ScalaCheck shrink
  // concurrently. Until there's fewer bugs, let's just keep the tests running serially.
  1
}

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

/** Runs a ScalaCheck property with custom parameters.
  *
  * This method configures ScalaCheck to use a specific number of worker threads
  * and sets a minimum number of successful tests. It's designed to provide a
  * standardized way of running property-based tests across the project.
  *
  * @param property
  *   The ScalaCheck property to be tested.
  * @return
  *   The result of the property check.
  */
def check(property: Prop): Test.Result = {
  val workers = workerCount()
  Test.check(
    Test.Parameters.defaultVerbose
      .withWorkers(workers)
      .withMinSuccessfulTests(1000),
    property
  )
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

  for (property <- properties) check(property(propClient, qContext))
}

@main def run(): Unit = {
  val properties = List(
    CrashProperties.makeSqlQuerySuccessProperty,
    CrashProperties.makePplQuerySuccessProperty
  )

  runPropertyBatch(properties)
}
