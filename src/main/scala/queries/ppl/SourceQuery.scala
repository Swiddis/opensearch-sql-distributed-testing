package queries.ppl

import datagen.QueryContext
import org.scalacheck.Gen
import queries.QuerySerializable

case class SourceQuery(
    index: String,
    where: Option[Expr[PplBoolean]]
) extends QuerySerializable {
  override def serialize(): String = {
    val whereClause = where match {
      case None => ""
      case Some(ex) => {
        val ser = ex.serialize()
        // PPL can't handle outer-level parentheses for WHERE clauses, so we remove them if we're using a binary
        // operator which generates them by default
        // Tracking: https://github.com/opensearch-project/sql/issues/3272
        if ex.isInstanceOf[BinaryOp[?, ?]] then
          "| WHERE " + ser.substring(1, ser.length - 1)
        else "| WHERE " + ser
      }
    }
    s"SOURCE = $index $whereClause"
  }

  override def toString: String = {
    this.serialize()
  }
}

object SourceQueryGenerator {

  /** Create a [[org.scalacheck.Gen]] which produces queries aligning with the
    * provided [[QueryContext]].
    *
    * @param context
    *   The query context to use for semantic analysis, e.g. queries shouldn't
    *   generally query columns that don't exist
    * @return
    *   A generator for queries satisfying that context
    */
  def from(context: QueryContext): Gen[SourceQuery] = {
    for {
      index <- Gen.oneOf(context.indices.toList)
      whereClause <- Gen.some(ContextExprGen.boolExpr(index, 3))
    } yield SourceQuery(index.name, whereClause)
  }
}
