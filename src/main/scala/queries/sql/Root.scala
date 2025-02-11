package queries.sql

import config.Testing
import datagen.OpenSearchDataType.Integer
import datagen.QueryContext
import org.scalacheck.Gen
import queries.QuerySerializable

enum Aggregate:
  case MIN, MAX, SUM, COUNT, AVG

case class SelectQuery(
    index: String,
    fields: List[String],
    selDistinct: Boolean,
    where: Option[SqlExpr[SqlBoolean]]
) extends QuerySerializable {
  override def serialize(): String = {
    val fieldNames = if (fields.isEmpty) "*" else fields.mkString("", ", ", "")
    val whereClause = where match {
      case None => ""
      case Some(ex) =>
        if ex.isConstant && Testing.config.disableConstantExprs then ""
        else "WHERE " + ex.serialize()
    }
    val distinct = if selDistinct then " DISTINCT" else ""
    s"SELECT$distinct $fieldNames FROM $index $whereClause"
  }

  override def toString: String = {
    this.serialize()
  }

  def withFields(replacementFields: List[String]): SelectQuery =
    SelectQuery(index, replacementFields, selDistinct, where)

  /** Create a new copy of this query, but replace the WHERE clause.
    */
  def withWhere(replacementWhere: Option[SqlExpr[SqlBoolean]]): SelectQuery =
    SelectQuery(index, fields, selDistinct, replacementWhere)

  def distinct(): SelectQuery =
    SelectQuery(index, fields, true, where)
}

object SelectQueryGenerator {

  /** Create a [[org.scalacheck.Gen]] which produces queries aligning with the
    * provided [[QueryContext]].
    *
    * @param context
    *   The query context to use for semantic analysis, e.g. queries shouldn't
    *   generally query columns that don't exist
    * @return
    *   A generator for queries satisfying that context
    */
  def fromWhere(context: QueryContext): Gen[SelectQuery] = {
    for {
      index <- Gen.oneOf(context.indices.toList)
      fields <- Gen.someOf(index.fields.keys)
      whereClause <- Gen.some(ContextExprGen.boolExpr(index, 3))
    } yield SelectQuery(index.name, fields.toList, false, whereClause)
  }

  /** Create a generator for SELECT DISTINCT queries
    */
  def distinctFromWhere(context: QueryContext): Gen[SelectQuery] = {
    for { q <- fromWhere(context) } yield q.distinct()
  }

  private def aggregateFromWhere(
      context: QueryContext,
      aggregate: Aggregate
  ): Gen[SelectQuery] = {
    for {
      index <- Gen.oneOf(context.indices.toList)
      // TODO handle empty case, for now we just force it in index generation
      field <- Gen.oneOf(index.fieldsWithType(Integer))
      whereClause <- Gen.some(ContextExprGen.boolExpr(index, 3))
    } yield SelectQuery(
      index.name,
      List(s"$aggregate($field)"),
      false,
      whereClause
    )
  }

  /** Create an aggregate query generator for SUM queries
    */
  def sumFromWhere(context: QueryContext): Gen[SelectQuery] = {
    aggregateFromWhere(context, Aggregate.SUM)
  }

  /** Create an aggregate query generator for COUNT queries
    */
  def countFromWhere(context: QueryContext): Gen[SelectQuery] = {
    aggregateFromWhere(context, Aggregate.COUNT)
  }

  /** Create an aggregate query generator for MIN queries
    */
  def minFromWhere(context: QueryContext): Gen[SelectQuery] = {
    aggregateFromWhere(context, Aggregate.MIN)
  }

  /** Create an aggregate query generator for MAX queries
    */
  def maxFromWhere(context: QueryContext): Gen[SelectQuery] = {
    aggregateFromWhere(context, Aggregate.MAX)
  }

  /** Create an aggregate query generator for AVG queries
    */
  def avgFromWhere(context: QueryContext): Gen[SelectQuery] = {
    aggregateFromWhere(context, Aggregate.AVG)
  }
}
