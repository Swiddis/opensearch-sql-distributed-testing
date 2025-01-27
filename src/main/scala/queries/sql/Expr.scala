package queries.sql

import org.scalacheck.Gen


/**
 * Convenient unit type, in order to have a version of null with toString defined so we can use it in boolean `Expr`s.
 * See: [[SqlBoolean]].
 */
class SqlNull:
  override def toString: String = "NULL"

/**
 * SQL really uses ternary logic: NULL behaves weirdly with the two booleans, and the 3 values generate their own truth
 * table. This is the principle on which Ternary Logic Partitioning is built. It's useful to have a dedicated type here.
 */
type SqlBoolean = SqlNull | Boolean

/**
 * The `Expr` trait represents the top-level type for SQL expressions (e.g. the argument to a `WHERE` clause, or a
 * variable used in a `SELECT` clause).
 *
 * @tparam T The type that the expression evaluates to, used to avoid cases like `SELECT * WHERE -NULL`.
 */
sealed trait Expr[T]:
  def serialize(): String

  /**
   * Useful for filtering constant clause generation, since `WHERE FALSE`-like queries tend to not be very useful.
   *
   * TODO instead of having this be a flag, we should use a ExprProperties class. Will keep this until we need more props.
   *
   * @return whether the current expression evaluates to a constant.
   */
  def isConstant: Boolean

case class Literal[T](value: T) extends Expr[T]:
  override def serialize(): String = value.toString.toUpperCase // Literals tend to be upper: NULL, FALSE, 1.5E6
  override def isConstant: Boolean = true

case class Column[T](name: String) extends Expr[T]:
  override def serialize(): String = name
  override def isConstant: Boolean = false

case class UnaryOp[A, B](op: String, arg: Expr[A]) extends Expr[B]:
  override def serialize(): String = op.replace("$1", arg.serialize())
  override def isConstant: Boolean = arg.isConstant

case class BinaryOp[A, B](left: Expr[A], op: String, right: Expr[A]) extends Expr[B]:
  // We always wrap binary ops in parentheses to make precedence "just work"
  override def serialize(): String =
    "(" + op.replace("$1", left.serialize()).replace("$2", right.serialize()) + ")"
  override def isConstant: Boolean = left.isConstant && right.isConstant

/**
 * `ExprGen` constructs ScalaCheck generators for `Expr`s.
 *
 * The main thing to keep in mind is that operations expect their arguments to be defined as regex-style "$num"
 * selectors, such as negation being "-$1". This is done to allow operators with different formats, as "$1 IS NULL" can
 * also be treated as a unary operation, as with function calls.
 */
object ExprGen {
  def literal[T](gen: Gen[T]): Gen[Expr[T]] = gen.map(Literal(_))

  def column[T](names: Seq[String]): Gen[Expr[T]] = Gen.oneOf(names).map(Column(_))

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
