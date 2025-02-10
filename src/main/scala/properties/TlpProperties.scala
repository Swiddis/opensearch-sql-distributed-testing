package properties

import datagen.QueryContext
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import queries.sql.{
  Literal,
  SelectQuery,
  SelectQueryGenerator,
  SqlNull,
  UnaryOp
}

import scala.util.boundary
import scala.util.boundary.break

/** Bundles property constructors for properties related to Ternary Logic
  * Partitioning. Check out the primer doc for details: `[root]/docs/primer.md`.
  */
object TlpProperties {
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
        List(
          query.withWhere(None),
          query.withWhere(Some(Literal(true))),
          query.withWhere(Some(Literal(false))),
          query.withWhere(Some(Literal(SqlNull())))
        )
    }
  }

  // Test if lists are equal, ignoring order.
  private def multisetEquality[A](left: List[A], right: List[A]): Boolean = {
    if (left.length != right.length) {
      false
    } else {
      val count1 = left.groupBy(identity).view.mapValues(_.size).toMap
      val count2 = right.groupBy(identity).view.mapValues(_.size).toMap
      count1 == count2
    }
  }

  def makeSqlTlpWhereProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SelectQueryGenerator.from(queryContext)

    Prop.forAll(gen) { (query: SelectQuery) =>
      boundary:
        val parts = partitionOnWhere(query)
        val results = parts.map(q => client.runSqlQuery(q.serialize()))

        // TODO if a partition errors, we just ignore the case entirely.
        //  When CrashProperties starts failing less, let's replace this with actual error handling.
        if results
            .map(res => res("status").num != 200)
            .reduce((l, r) => l || r)
        then break("ignored error" |: true)

        val (qRes, partRes) = (
          results.head("datarows").arr.toList,
          results.tail.flatMap(r => r("datarows").arr)
        )

        val partSizes =
          results.tail
            .map(p => p("datarows").arr.length.toString)
            .mkString(" + ")
        s"${qRes.size} != $partSizes" |: multisetEquality(qRes, partRes)
    }
  }
}
