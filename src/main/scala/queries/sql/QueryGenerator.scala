package queries.sql

import scala.jdk.CollectionConverters._
import org.scalacheck.Gen
import queries.{IndexContext, QueryContext, QuerySerializable}

// TODO limit should not be parameter, additional parts need to be determined separately
class Select(index: String, fields: List[String], lim: Int) extends QuerySerializable {
  def getLim: Int = {
    this.lim
  }

  def serialize(): String = {
    val fieldNames = if (fields.isEmpty) "*" else fields.mkString("", ", ", "")
    s"SELECT $fieldNames FROM $index LIMIT $lim"
  }
}

object SelectQueryGenerator {
  /**
   * Create a [[org.scalacheck.Gen]] which produces queries aligning with the provided [[QueryContext]].
   *
   * @param context The query context to use for semantic analysis, e.g. queries shouldn't generally query columns
   *            that don't exist
   * @return A generator for queries satisfying that context
   */
  def make(context: QueryContext): Gen[Select] = {
    for {
      index <- Gen.oneOf(context.indices.toList)
      fields <- Gen.sequence(index.fields.keys.map(Gen.const))
      limit <- Gen.choose(0, 100)
    } yield Select(index.name, fields.asScala.toList, limit)
  }
}
