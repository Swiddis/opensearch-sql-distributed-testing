package properties

import datagen.QueryContext
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import properties.PropertyUtils.multisetEquality
import queries.sql.{Aggregate, SelectQuery, SelectQueryGenerator, UnaryOp}

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
          query.withWhere(Some(UnaryOp("NOT ($1)", q)))
          // NULL is broken, as NOT NULL is truthy.
          // Reactivate when (if) NULL gets fixed.
//          query.withWhere(Some(UnaryOp("($1) IS NULL", q)))
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
    val gen = SelectQueryGenerator.fromWhere(queryContext)

    Prop.forAll(gen) { (query: SelectQuery) =>
      val parts = partitionOnWhere(query)
      val results = parts.map(q => client.runSqlQuery(q.serialize()))

      // If a partition errors, we just discard the case entirely (using implication ==>)
      // ScalaCheck will fail if the discard rate gets too high
      val isQuerySuccessful = results
        .map(res => res("status").num == 200)
        .reduce((l, r) => l && r)

      isQuerySuccessful ==> {
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

  def makeDistinctTlpWhereProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SelectQueryGenerator.distinctFromWhere(queryContext)

    Prop.forAll(gen) { (query: SelectQuery) =>
      val parts = partitionOnWhere(query)
      val results = parts.map(q => client.runSqlQuery(q.serialize()))

      // If a partition errors, we just discard the case entirely (using implication ==>)
      // ScalaCheck will fail if the discard rate gets too high
      val isQuerySuccessful = results
        .map(res => res("status").num == 200)
        .reduce((l, r) => l && r)

      isQuerySuccessful ==> {
        val (qRes, partRes) = (
          results.head("datarows").arr.toList,
          results.tail.flatMap(r => r("datarows").arr)
        )

        val partSizes =
          results.tail
            .map(p => p("datarows").arr.length.toString)
            .mkString(" U ")
        s"Set inequality: ${qRes.size} != $partSizes" |: Set(
          qRes.iterator
        ) != Set(partRes.iterator)
      }
    }
  }

  private def makeAggregateTlpProperty[T](
      client: PropTestClient,
      queryContext: QueryContext,
      aggregate: Aggregate,
      mapper: ujson.Value => T,
      reducer: (T, T) => T
  ): Prop = {
    val gen = aggregate match
      case Aggregate.MIN   => SelectQueryGenerator.minFromWhere(queryContext)
      case Aggregate.MAX   => SelectQueryGenerator.maxFromWhere(queryContext)
      case Aggregate.SUM   => SelectQueryGenerator.sumFromWhere(queryContext)
      case Aggregate.COUNT => SelectQueryGenerator.countFromWhere(queryContext)
      case _ =>
        throw IllegalArgumentException(s"unimplemented aggregate: $aggregate")

    Prop.forAll(gen) { (query: SelectQuery) =>
      val parts = partitionOnWhere(query)
      val results = parts.map(q => client.runSqlQuery(q.serialize()))

      // If a partition errors, we just discard the case entirely (using implication ==>)
      // ScalaCheck will fail if the discard rate gets too high
      val isQuerySuccessful = results
        .map(res => res("status").num == 200)
        .reduce((l, r) => l && r)

      isQuerySuccessful ==> {
        val (qRes, partRes) = (
          results.head("datarows").arr.toList,
          results.tail.flatMap(r => r("datarows").arr)
        )

        val (resultL, resultR) = (
          qRes.map(mapper).reduce(reducer),
          partRes.map(mapper).reduce(reducer)
        )
        s"$aggregate mismatch: $resultL != $resultR" |: resultL == resultR
      }
    }
  }

  def makeAggregateSumTlpProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    makeAggregateTlpProperty(
      client,
      queryContext,
      Aggregate.SUM,
      v => v.arr.head.num,
      (a, b) => a + b
    )
  }

  def makeAggregateMinTlpProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    makeAggregateTlpProperty(
      client,
      queryContext,
      Aggregate.MIN,
      v =>
        if v.arr.head.isNull then Double.PositiveInfinity else v.arr.head.num,
      (a, b) => Math.min(a, b)
    )
  }

  def makeAggregateMaxTlpProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    makeAggregateTlpProperty(
      client,
      queryContext,
      Aggregate.MAX,
      v =>
        if v.arr.head.isNull then Double.NegativeInfinity else v.arr.head.num,
      (a, b) => Math.max(a, b)
    )
  }

  def makeAggregateCountTlpProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    makeAggregateTlpProperty(
      client,
      queryContext,
      Aggregate.COUNT,
      v => v.arr.head.num,
      (a, b) => a + b
    )
  }

  def makeAggregateAvgTlpProperty(
      client: PropTestClient,
      queryContext: QueryContext
  ): Prop = {
    val gen = SelectQueryGenerator.avgFromWhere(queryContext)

    Prop.forAll(gen) { (query: SelectQuery) =>
      val where = query.where.get
      val field = query.fields.head.stripPrefix("AVG(").stripSuffix(")")
      val parts = List(
        query.withWhere(None),
        query.withFields(List(s"SUM($field)", s"COUNT($field)")),
        query
          .withFields(List(s"SUM($field)", s"COUNT($field)"))
          .withWhere(Some(UnaryOp("NOT ($1)", where)))
      )
      val results = parts.map(q => client.runSqlQuery(q.serialize()))

      // If a partition errors, we just discard the case entirely (using implication ==>)
      // ScalaCheck will fail if the discard rate gets too high
      val isQuerySuccessful = results
        .map(res => res("status").num == 200)
        .reduce((l, r) => l && r)

      isQuerySuccessful ==> {
        val (qRes, partRes) = (
          results.head("datarows").arr.toList,
          results.tail.flatMap(r => r("datarows").arr)
        )

        val (resultL, resultR) = (
          qRes.head.arr.head.num,
          partRes.map(v => v.arr.head.num).sum / partRes
            .map(v => v.arr.last.num)
            .sum
        )
        s"AVG mismatch: $resultL != $resultR\nqRes = $qRes\npRes = $partRes" |: resultL == resultR
      }
    }
  }
}
