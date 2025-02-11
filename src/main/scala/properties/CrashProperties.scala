package properties

import datagen.QueryContext
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import queries.ppl.{SourceQuery, SourceQueryGenerator}
import queries.sql.{SelectQuery, SelectQueryGenerator}

object CrashProperties {
  def makeSqlQuerySuccessProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SelectQueryGenerator.fromWhere(queryContext)

    Prop.forAll(gen) { (q: SelectQuery) =>
      {
        val query = q.serialize()
        val result = client.runSqlQuery(query)
        val errorReport: String =
          ResultFormatter.formatErrorDetail(query, result)
        errorReport |: result("status").num.toInt == 200
      }
    }
  }

  def makePplQuerySuccessProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SourceQueryGenerator.from(queryContext)

    Prop.forAll(gen) { (q: SourceQuery) =>
      {
        val query = q.serialize()
        val result = client.runPplQuery(query)
        val errorReport: String =
          ResultFormatter.formatErrorDetail(query, result)
        errorReport |: result.obj.get("error").isEmpty
      }
    }
  }
}
