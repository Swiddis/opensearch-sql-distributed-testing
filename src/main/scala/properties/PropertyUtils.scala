package properties

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
}
