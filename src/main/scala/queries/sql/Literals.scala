package queries.sql

object LiteralNull extends SqlExpression {
  override def serialize(): String = "NULL"
}

class LiteralBool(b: Boolean) extends SqlExpression {
  override def serialize(): String = b.toString.toUpperCase
}

class LiteralInt(b: Integer) extends SqlExpression {
  override def serialize(): String = b.toString
}
