package queries.sql

import datagen.{IndexContext, OpenSearchDataType}
import org.scalacheck.Gen

/** ContextExprGen is a set of wrappers around `ExprGen` that uses the current
  * `IndexContext` to construct expressions respecting the requested type.
  */
object ContextExprGen {

  /** Produce a generator for integer expressions.
    */
  def intExpr(context: IndexContext, depth: Int): Gen[SqlExpr[Int]] = {
    if (depth <= 0) {
      // We duplicate literal as a convenient fallback if no relevant fields are available
      val availableFields = context.fieldsWithType(OpenSearchDataType.Integer)
      val literal = ExprGen.literal(Gen.choose(-1000, 1000))

      Gen.oneOf(
        literal,
        if availableFields.nonEmpty then ExprGen.column(availableFields)
        else literal
      )
    } else {
      val next = intExpr(context, depth - 1)

      Gen.oneOf(
        next,
        ExprGen.unaryOp(List("-$1", "ABS($1)", "SIGN($1)"), next),
        ExprGen.binaryOp(
          List(
            "$1 + $2",
            "$1 - $2",
            "$1 / $2"
            // TODO disabled for now to reduce chance of integer overflow
            //  Can figure out how to reactivate later, but it's not clear how many bugs will
            //  depend purely on multiplication evaluation and not be catchable otherwise
            /*"$1 * $2"*/
          ),
          next
        )
      )
    }
  }

  /** Produce a generator for boolean expressions
    */
  def boolExpr(context: IndexContext, depth: Int): Gen[SqlExpr[SqlBoolean]] = {
    if (depth <= 0) {
      val availableFields = context.fieldsWithType(OpenSearchDataType.Boolean)
      // TODO null handling is broken, don't generate it for now
//      val literal = ExprGen.literal(Gen.oneOf(false, true, SqlNull()))
      val literal =
        ExprGen.literal(Gen.oneOf(false, true).asInstanceOf[SqlBoolean])

      Gen.oneOf(
        literal,
        if availableFields.nonEmpty then ExprGen.column(availableFields)
        else literal
      )
    } else {
      val next = boolExpr(context, depth - 1)

      Gen.oneOf(
        next,
        ExprGen.unaryOp(
          List(
            // NOT is fundamentally broken, disable for now.
            // Tracking: https://github.com/opensearch-project/sql/issues/3266
            // "NOT $1",
            "$1 IS NULL",
            "$1 IS NOT NULL"
          ),
          next
        ),
        ExprGen
          .binaryOp(List("$1 = $2", "$1 <> $2", "$1 AND $2", "$1 OR $2"), next),
        ExprGen.binaryOp(
          List(
            "$1 = $2",
            "$1 <> $2",
            "$1 > $2",
            "$1 < $2",
            "$1 >= $2",
            "$1 <= $2"
          ),
          intExpr(context, depth - 1)
        )
      )
    }
  }
}
