package queries

/**
 * Serializable represents query components which can be eventually formatted for sending to the cluster.
 */
trait QuerySerializable:
  /**
   * Serialize this type as a sanitized string, safe to be submitted as a query part to the database.
   * Kept as a separate method from `toString` to distinguish queries from debug strings.
   *
   * @return The raw query string.
   */
  def serialize(): String
