package queries.sql

enum BinaryOperator(val ser: String):
  case Is extends BinaryOperator("IS")
  case Eq extends BinaryOperator("=")
  case Neq extends BinaryOperator("<>")
  case Gt extends BinaryOperator(">")
  case Geq extends BinaryOperator(">=")
  case Lt extends BinaryOperator("<")
  case Leq extends BinaryOperator("<=")

class BinaryExpression(left: SqlExpression, right: SqlExpression, operator: BinaryOperator) extends SqlExpression {
  override def serialize(): String = s"(${left.serialize()} ${operator.ser} ${right.serialize()})"
}
