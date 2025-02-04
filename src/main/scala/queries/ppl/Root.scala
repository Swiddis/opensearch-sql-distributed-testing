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
      case None     => ""
      case Some(ex) => "| WHERE " + ex.serialize()
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
