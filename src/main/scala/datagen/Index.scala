package datagen

import org.scalacheck.Gen

import java.util.UUID
import scala.collection.mutable

case class Index(
    context: IndexContext,
    data: Map[String, List[Null | Int | Boolean]]
)

val alphabet = "abcdefghijklmnopqrstuvwxyz".toList.map(c => c.toString)

def take[A](gen: Gen[A]): A = gen.sample.get
def listOf[A](gen: Gen[A], size: Int): List[A] =
  (0 to size).map(_ => gen.sample.get).toList

object IndexGenerator {
  def genIndex(): Index = {
    val indexName =
      "test_" + UUID.randomUUID().toString.replace("-", "_").substring(0, 8)

//    val fieldNames = take(Gen.atLeastOne(alphabet))
    val fieldNames = List("a", "b", "c", "d")
    val fields = fieldNames
      .map(field => (field, take(Gen.oneOf(OpenSearchDataType.values))))
      .toMap

    val data: mutable.Map[String, List[Null | Int | Boolean]] = mutable.Map()
    val rows = take(Gen.choose(10, 100))
    for (field, datatype) <- fields do
      data(field) = datatype match {
        case OpenSearchDataType.Boolean =>
          listOf(
            Gen.frequency((20, Gen.oneOf(false, true)), (1, Gen.const(null))),
            rows
          )
        case OpenSearchDataType.Integer => listOf(Gen.choose(-2000, 2000), rows)
      }

    Index(IndexContext(indexName, fields), data.toMap)
  }
}
