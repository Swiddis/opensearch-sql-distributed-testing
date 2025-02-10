package queries.sql

import config.Testing
import datagen.QueryContext
import org.scalacheck.Gen
import queries.QuerySerializable

case class SelectQuery(
    index: String,
    fields: List[String],
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
    s"SELECT $fieldNames FROM $index $whereClause"
  }

  override def toString: String = {
    this.serialize()
  }

  /** Create a new copy of this query, but replace the WHERE clause.
    */
  def withWhere(replacementWhere: Option[SqlExpr[SqlBoolean]]): SelectQuery =
    SelectQuery(this.index, this.fields, replacementWhere)
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
  def from(context: QueryContext): Gen[SelectQuery] = {
    for {
      index <- Gen.oneOf(context.indices.toList)
      fields <- Gen.someOf(index.fields.keys)
      whereClause <- Gen.some(ContextExprGen.boolExpr(index, 3))
    } yield SelectQuery(index.name, fields.toList, whereClause)
  }
}
