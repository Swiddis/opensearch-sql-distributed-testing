package properties

import cats.data.NonEmptyList
import ujson.Obj
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean

object PropertyUtils {
  // Test if lists are equal, ignoring order.
  def multisetEquality[A](left: List[A], right: List[A]): Boolean = {
    if (left.length != right.length) {
      false
    } else {
      val count1 = left.groupBy(identity).view.mapValues(_.size).toMap
      val count2 = right.groupBy(identity).view.mapValues(_.size).toMap
      count1 == count2
    }
  }

  /** Given a list of TLP query results, create a final prop that tests that the
    * first result in the list unifies to the latter results
    */
  def finalizeTlpResult(results: List[Obj]): Prop = {
    val (qRes, partRes) = (
      results.head("datarows").arr.toList,
      results.tail.flatMap(r => r("datarows").arr)
    )

    val partSizes =
      results.tail
        .map(p => p("datarows").arr.length.toString)
        .mkString(" + ")
    s"${qRes.size} != $partSizes" |: multisetEquality(qRes, partRes)
  }
}
