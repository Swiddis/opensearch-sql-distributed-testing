package queries.sql

enum UnaryOperator(val ser: String):
  case Negate extends UnaryOperator("-")
  case Not extends UnaryOperator("NOT ")

class UnaryExpression(inner: SqlExpression, operator: UnaryOperator) extends SqlExpression {
  override def serialize(): String = s"${operator.ser}${inner.serialize()}"
}
