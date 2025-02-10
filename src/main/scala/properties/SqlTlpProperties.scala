package properties

import datagen.QueryContext
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import properties.PropertyUtils.{finalizeTlpResult, multisetEquality}
import queries.sql.{SelectQuery, SelectQueryGenerator, UnaryOp}

/** Bundles property constructors for properties related to Ternary Logic
  * Partitioning. Check out the primer doc for details: `[root]/docs/primer.md`.
  */
object SqlTlpProperties {
  private def partitionOnWhere(query: SelectQuery): List[SelectQuery] = {
    query.where match {
      case Some(q) =>
        List(
          query.withWhere(None),
          query,
          query.withWhere(Some(UnaryOp("NOT ($1)", q))),
          query.withWhere(Some(UnaryOp("($1) IS NULL", q)))
        )
      case None =>
        throw IllegalArgumentException(
          "partitionOnWhere requested for missing WHERE clause. This is a bug"
        )
    }
  }

  def makeSimpleTlpWhereProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SelectQueryGenerator.from(queryContext)

    Prop.forAll(gen) { (query: SelectQuery) =>
      val parts = partitionOnWhere(query)
      val results = parts.map(q => client.runSqlQuery(q.serialize()))

      // If a partition errors, we just discard the case entirely (using implication ==>)
      // ScalaCheck will fail if the discard rate gets too high
      val isQuerySuccessful = results
        .map(res => res("status").num == 200)
        .reduce((l, r) => l && r)

      isQuerySuccessful ==> finalizeTlpResult(results)
//          val (qRes, partRes) = (
//            results.head("datarows").arr.toList,
//            results.tail.flatMap(r => r("datarows").arr)
//          )
//
//          val partSizes =
//            results.tail
//              .map(p => p("datarows").arr.length.toString)
//              .mkString(" + ")
//          s"${qRes.size} != $partSizes" |: multisetEquality(qRes, partRes)
//        }
    }
  }
}
