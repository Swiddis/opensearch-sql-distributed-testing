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

/** The `Expr` trait represents the top-level type for SQL expressions (e.g. the
  * argument to a `WHERE` clause, or a variable used in a `SELECT` clause).
  *
  * @tparam T
  *   The type that the expression evaluates to, used to avoid cases like
  *   `SELECT * WHERE -NULL`.
  */
sealed trait Expr[T]:
  def serialize(): String

case class Literal[T](value: T) extends Expr[T]:
  override def serialize(): String =
    value.toString.toUpperCase // Literals tend to be upper: NULL, FALSE, 1.5E6

case class Column[T](name: String) extends Expr[T]:
  override def serialize(): String = name

case class UnaryOp[A, B](op: String, arg: Expr[A]) extends Expr[B]:
  override def serialize(): String = op.replace("$1", arg.serialize())

case class BinaryOp[A, B](left: Expr[A], op: String, right: Expr[A])
    extends Expr[B]:
  // We always wrap binary ops in parentheses to make precedence "just work"
  override def serialize(): String =
    "(" + op
      .replace("$1", left.serialize())
      .replace("$2", right.serialize()) + ")"

/** `ExprGen` constructs ScalaCheck generators for `Expr`s.
  *
  * The main thing to keep in mind is that operations expect their arguments to
  * be defined as regex-style "$num" selectors, such as negation being "-$1".
  * This is done to allow operators with different formats, as "$1 IS NULL" can
  * also be treated as a unary operation, as with function calls.
  */
object ExprGen {
  def literal[T](gen: Gen[T]): Gen[Expr[T]] = gen.map(Literal(_))

  def column[T](names: Seq[String]): Gen[Expr[T]] =
    Gen.oneOf(names).map(Column(_))

  def unaryOp[A, B](ops: Seq[String], argGen: Gen[Expr[A]]): Gen[Expr[B]] =
    for {
      op <- Gen.oneOf(ops)
      arg <- argGen
    } yield UnaryOp(op, arg)

  def binaryOp[A, B](ops: Seq[String], argGen: Gen[Expr[A]]): Gen[Expr[B]] =
    for {
      left <- argGen
      op <- Gen.oneOf(ops)
      right <- argGen
    } yield BinaryOp(left, op, right)
}
