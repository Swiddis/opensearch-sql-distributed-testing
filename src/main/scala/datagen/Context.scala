package datagen

import cats.data.NonEmptyList

/** The subset of datatypes that we support for query generation
  *
  * @see
  *   https://opensearch.org/docs/latest/search-plugins/sql/datatypes/
  */
enum OpenSearchDataType:
  // TODO very small subset for now; this must be exhaustively used in a few places so we shouldn't add to it too quickly
  //  Some usages:
  //  - Expr types in [[queries.sql.Expr]]
  //  - Data generators in [[datagen.Index]]
  //  - Index mapping definitions in Main
  case Boolean, Integer

case class IndexContext(name: String, fields: Map[String, OpenSearchDataType]):
  def fieldsWithType(t: OpenSearchDataType): Seq[String] = {
    this.fields.filter((_, v) => v == t).keys.toList
  }

/** Contains information that helps with useful semantic query generation. For
  * instance, if we trivially generate random queries that match the ANTLR
  * grammar, they'll never match a useful index with real fields. The context is
  * used to give the queries some room to operate in.
  *
  * @param indices
  *   A list of indices available to use, e.g. for SELECT or JOIN queries.
  */
class QueryContext(val indices: NonEmptyList[IndexContext])
