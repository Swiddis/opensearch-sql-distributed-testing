package properties

import datagen.QueryContext
import org.opensearch.client.opensearch.OpenSearchClient
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import properties.ResultFormatter.prettyErrorReport
import queries.ppl.{SourceQuery, SourceQueryGenerator}
import queries.sql.{SelectQuery, SelectQueryGenerator}

import scala.util.Try

object CrashProperties {
  def makeSqlQuerySuccessProperty(
      client: OpenSearchClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SelectQueryGenerator.from(queryContext)
    val propClient = PropTestClient(client)

    Prop.forAll(gen) { (q: SelectQuery) =>
      {
        val query = q.serialize()
        val result = propClient.runSqlQuery(query)
        val errorReport: String =
          ResultFormatter.formatErrorDetail(query, result)
        errorReport |: result("status").num.toInt == 200
      }
    }
  }

  def makePplQuerySuccessProperty(
      client: OpenSearchClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SourceQueryGenerator.from(queryContext)
    val propClient = PropTestClient(client)

    Prop.forAll(gen) { (q: SourceQuery) =>
      {
        val query = q.serialize()
        val result = propClient.runPplQuery(query)
        val errorReport: String =
          ResultFormatter.formatErrorDetail(query, result)
        errorReport |: result.obj.get("error").isEmpty
      }
    }
  }
}
