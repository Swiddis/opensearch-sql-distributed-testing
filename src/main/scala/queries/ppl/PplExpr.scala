package queries.ppl

import org.scalacheck.Gen

/** PPL defines two types of null: NULL and MISSING. This is similar to how JS
  * does null and undefined.
  */
class PplNull(val flavor: "NULL" | "MISSING"):
  override def toString: String = flavor

/** While SQL logic is ternary, PPL logic is quaternary: there are two null
  * values in addition to the true and false values.
  */
type PplBoolean = PplNull | Boolean

/** The `Expr` trait represents the top-level type for PPL expressions (e.g. the
  * argument to a `WHERE` clause, or a variable used in a `FIELDS` clause).
  *
  * @tparam T
  *   The type that the expression evaluates to, used to avoid cases like `WHERE
  *   -NULL`.
  */
sealed trait PplExpr[T]:
  def serialize(): String

case class Literal[T](value: T) extends PplExpr[T]:
  override def serialize(): String =
    value.toString.toUpperCase // Literals tend to be upper: NULL, FALSE, 1.5E6

case class Column[T](name: String) extends PplExpr[T]:
  override def serialize(): String = name

case class UnaryOp[A, B](op: String, arg: PplExpr[A]) extends PplExpr[B]:
  override def serialize(): String = op.replace("$1", arg.serialize())

case class BinaryOp[A, B](left: PplExpr[A], op: String, right: PplExpr[A])
    extends PplExpr[B]:
  /** To avoid generating too many redundant parentheses, we only add
    * parentheses around the sides of binary operations where order is more
    * likely to matter.
    *
    * @see
    *   https://github.com/opensearch-project/sql/issues/3272
    */
  private def serializeWithMaybeParens(ex: PplExpr[A]): String = {
    ex match {
      case BinaryOp(left, op, right) => "(" + ex.serialize() + ")"
      case _                         => ex.serialize()
    }
  }

  override def serialize(): String =
    op
      .replace("$1", serializeWithMaybeParens(left))
      .replace("$2", serializeWithMaybeParens(right))

/** `ExprGen` constructs ScalaCheck generators for `Expr`s.
  *
  * The main thing to keep in mind is that operations expect their arguments to
  * be defined as regex-style "$num" selectors, such as negation being "-$1".
  * This is done to allow operators with different formats, as "$1 IS NULL" can
  * also be treated as a unary operation, as with function calls.
  */
object ExprGen {
  def literal[T](gen: Gen[T]): Gen[PplExpr[T]] = gen.map(Literal(_))

  def column[T](names: Seq[String]): Gen[PplExpr[T]] =
    Gen.oneOf(names).map(Column(_))

  def unaryOp[A, B](
      ops: Seq[String],
      argGen: Gen[PplExpr[A]]
  ): Gen[PplExpr[B]] =
    for {
      op <- Gen.oneOf(ops)
      arg <- argGen
    } yield UnaryOp(op, arg)

  def binaryOp[A, B](
      ops: Seq[String],
      argGen: Gen[PplExpr[A]]
  ): Gen[PplExpr[B]] =
    for {
      left <- argGen
      op <- Gen.oneOf(ops)
      right <- argGen
    } yield BinaryOp(left, op, right)
}
