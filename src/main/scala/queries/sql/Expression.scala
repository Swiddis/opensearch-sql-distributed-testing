package queries.sql

import org.scalacheck.Gen
import queries.{QueryContext, QuerySerializable}

abstract class SqlExpression extends QuerySerializable

val genLiteralNull = Gen.const(LiteralNull)
val genLiteralBool = Gen.oneOf(LiteralBool(true), LiteralBool(false))
val genLiteralInt = for {
  n <- Gen.choose(Integer.MIN_VALUE, Integer.MAX_VALUE)
} yield LiteralInt(n)

object ExpressionGenerator {
  /**
   * A generator for everything that doesn't depend on generating nested expressions
   */
  private def makeNestless(context: QueryContext): Gen[SqlExpression] = {
    Gen.oneOf(
      genLiteralNull,
      genLiteralBool,
      genLiteralInt,
    )
  }

  /**
   * Based on the provided depth limit, create a generator that can also generate nested expressions
   */
  private def makeNested(context: QueryContext, depthLimit: Integer): Gen[SqlExpression] = {
    if depthLimit <= 0 then {
      return ExpressionGenerator.makeNestless(context)
    }
    val genNext = ExpressionGenerator.makeNested(context, depthLimit - 1)
    Gen.frequency(
      (5, ExpressionGenerator.makeNestless(context)),
      (1, for {
        next <- Gen.lzy(genNext)
        operator <- Gen.oneOf(UnaryOperator.values)
      } yield UnaryExpression(next, operator)),
      (1, for {
        left <- Gen.lzy(genNext)
        right <- Gen.lzy(genNext)
        operator <- Gen.oneOf(BinaryOperator.values)
      } yield BinaryExpression(left, right, operator))
    )
  }

  def make(context: QueryContext): Gen[SqlExpression] = {
    // TODO should depth limit be dynamic/configurable? For the moment 3 should be plenty.
    ExpressionGenerator.makeNested(context, depthLimit = 3)
  }
}
