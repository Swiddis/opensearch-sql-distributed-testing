package datagen

import org.scalacheck.Gen

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable

case class Index(
    context: IndexContext,
    data: Map[String, List[Null | Int | Boolean]]
)

val alphabet = "abcdefghijklmnopqrstuvwxyz".toList.map(c => c.toString)

def take[A](gen: Gen[A]): A = gen.sample.get
def listOf[A](gen: Gen[A], size: Int): List[A] =
  (0 to size).map(_ => gen.sample.get).toList

@tailrec
def sampleFields(): Map[String, OpenSearchDataType] = {
  val fieldNames = take(Gen.atLeastOne(alphabet))
  val fields = fieldNames
    .map(field => (field, take(Gen.oneOf(OpenSearchDataType.values))))
    .toMap
  // Require at least one integer field to give the numeric TLP checks (e.g. aggregates) something to work with
  if fields.values
      .map(f => f == OpenSearchDataType.Integer)
      .reduce((a, b) => a || b)
  then fields
  else sampleFields()
}

object IndexGenerator {

  /** Generate an [[Index]] with some sample data, using the fields available
    * from OpenSearchDataType
    */
  def genIndex(): Index = {
    val indexName =
      "test_" + UUID.randomUUID().toString.replace("-", "_").substring(0, 8)

    val fields = sampleFields()

    val data: mutable.Map[String, List[Null | Int | Boolean]] = mutable.Map()
    val rows = take(Gen.choose(10, 100))
    for (field, datatype) <- fields do
      data(field) = datatype match {
        case OpenSearchDataType.Boolean =>
          listOf(
            // TODO null handling is broken, reactivate when fixed
//            Gen.frequency((20, Gen.oneOf(false, true)), (1, Gen.const(null))),
            Gen.oneOf(false, true),
            rows
          )
        case OpenSearchDataType.Integer => listOf(Gen.choose(-2000, 2000), rows)
      }

    Index(IndexContext(indexName, fields), data.toMap)
  }
}
