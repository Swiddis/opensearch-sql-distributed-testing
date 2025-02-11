package properties

import datagen.QueryContext
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import queries.sql.{SelectQuery, SelectQueryGenerator}

object SqlNoRecProperties {
  def makeSqlNoRecWhereProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SelectQueryGenerator.fromWhere(queryContext)

    Prop.forAll(gen) { (q: SelectQuery) =>
      {
        val optimizing =
          if q.fields.nonEmpty then q.withFields(List(q.fields.head)) else q
        val nonOptimizing =
          q.withFields(List(q.where.get.serialize())).withWhere(None)

        val (oq, noq) = (optimizing.serialize(), nonOptimizing.serialize())
        val resultOq = client.runSqlQuery(oq)
        val resultNoq = client.runSqlQuery(noq)

        val hadError =
          resultOq("status").num != 200 || resultNoq("status").num != 200

        !hadError ==> {
          val oqLen = resultOq("datarows").arr.length
          val noqLen = resultNoq("datarows").arr
            .map(v => {
              if v.arr.head.boolOpt.getOrElse(false) then 1 else 0
            })
            .sum
          s"$oqLen != $noqLen\nOptimizing:     ${optimizing.serialize()}\nNon-Optimizing: ${nonOptimizing.serialize()}"
            |: oqLen == noqLen
        }
      }
    }
  }
}
