package properties

import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.generic.Requests

/** Wrapper class around [[OpenSearchClient]] which simplifies the interface for
  * property implementations
  * @param inner
  *   the inner OpenSearch client
  */
class PropTestClient(client: OpenSearchClient) {

  /** Submit a SQL query directly to the provided client
    *
    * @param query
    *   A query
    * @param language
    *   The plugin route to use for the query
    * @return
    *   The result of the query as an arbitrary/untyped Json object
    */
  private def runRawQuery(
      query: String,
      language: "ppl" | "sql"
  ): ujson.Obj = {
    val untypedClient = this.client.generic()
    val requestBody = Map("query" -> query)
    val request = Requests
      .builder()
      .endpoint(s"/_plugins/_$language")
      .method("POST")
      .json(ujson.write(requestBody))
      .build()
    val response = untypedClient.execute(request)
    val responseBody = response.getBody.get()

    ujson.read(responseBody.bodyAsString()).obj
  }

  def runSqlQuery(query: String): ujson.Obj = runRawQuery(query, "sql")

  def runPplQuery(query: String): ujson.Obj = runRawQuery(query, "ppl")
}
