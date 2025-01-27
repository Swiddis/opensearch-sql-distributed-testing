package queries.sql

import scala.jdk.CollectionConverters._
import org.scalacheck.Gen
import queries.{QueryContext, QuerySerializable}

// TODO limit should not be parameter, additional parts need to be determined separately
class Select(index: String, fields: List[String], where: Option[Expr[SqlBoolean]]) extends QuerySerializable {
  override def serialize(): String = {
    val fieldNames = if (fields.isEmpty) "*" else fields.mkString("", ", ", "")
    val whereClause = where match {
      case None => ""
      case Some(ex) => "WHERE " + ex.serialize()
    }
    s"SELECT $fieldNames FROM $index $whereClause"
  }
}

object SelectQueryGenerator {
  /**
   * Create a [[org.scalacheck.Gen]] which produces queries aligning with the provided [[QueryContext]].
   *
   * @param context The query context to use for semantic analysis, e.g. queries shouldn't generally query columns
   *            that don't exist
   * @return A generator for queries satisfying that context
   */
  def from(context: QueryContext): Gen[Select] = {
    for {
      index <- Gen.oneOf(context.indices.toList)
      fields <- Gen.someOf(index.fields.keys)
      whereClause <- Gen.option(ContextExprGen.boolExpr(index, 3))
    } yield Select(index.name, fields.toList, whereClause)
  }
}
