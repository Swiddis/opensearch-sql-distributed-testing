package properties

import scala.util.Try

object ResultFormatter {
  private val EMPTY = "error = [No error information provided]"

  private def prettyErrorReport(err: ujson.Value): String = {
    val lines = List(
      ("reason", err("reason").strOpt),
      ("details", err("details").strOpt),
      ("type", err("type").strOpt)
    ).filter(l => l._2.isDefined).map(l => s"error.${l._1} = ${l._2.get}")
    if lines.isEmpty then EMPTY
    else {
      lines.mkString("\n")
    }
  }

  /** Assemble the error data for a given query and result, to be supplied as
    * ScalaCheck evidence. Note that this assumes the given request will fail
    * the checked property, and includes error information even for successful
    * queries.
    *
    * @param query
    *   The query which was run
    * @param result
    *   The result of running that query
    */
  def formatErrorDetail(query: String, result: ujson.Obj): String = {
    val errorReport: String =
      Try(prettyErrorReport(result("error").obj)).getOrElse(EMPTY)
    s"query = $query\n$errorReport"
  }
}
