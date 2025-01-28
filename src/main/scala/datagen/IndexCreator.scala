package datagen

import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.{OpenSearchException, Refresh}
import org.opensearch.client.opensearch._types.mapping.{
  BooleanProperty,
  IntegerNumberProperty,
  Property,
  TypeMapping
}
import org.opensearch.client.opensearch.core.{BulkRequest, BulkResponse}
import org.opensearch.client.opensearch.core.bulk.{
  BulkOperation,
  IndexOperation
}
import org.opensearch.client.opensearch.indices.{
  CreateIndexRequest,
  CreateIndexResponse
}

import scala.collection.mutable
import scala.jdk.CollectionConverters.{MapHasAsJava, SeqHasAsJava}

object IndexCreator {

  /** Converts a map of columnar data into a sequence of JSON objects. Each key
    * in the map represents a field name, and the values are lists representing
    * column values. Rows are constructed by taking the value at the same index
    * from each list.
    *
    * @param data
    *   A map where keys are field names, and values are lists of data for each
    *   field.
    * @return
    *   A sequence of JSON objects representing rows of data.
    * @throws IllegalArgumentException
    *   if the lists for each field have inconsistent lengths.
    */
  private def asJsonObjects(
      data: Map[String, List[Null | Int | Boolean]]
  ): IndexedSeq[ujson.Obj] = {
    val rowCount = data.values.map(_.length).headOption.getOrElse(0)
    require(
      data.values.forall(_.length == rowCount),
      "All columns must have the same length"
    )

    (0 until rowCount).map { rowIndex =>
      val obj = ujson.Obj()
      data.foreach { case (field, values) =>
        values(rowIndex) match {
          case null           => obj(field) = ujson.Null
          case value: Int     => obj(field) = value
          case value: Boolean => obj(field) = value
        }
      }
      obj
    }
  }

  /** Converts a ujson.Obj instance to a Java-compatible Map. This is necessary
    * for interaction with the OpenSearch client, which requires Java
    * collections to serialize documents.
    *
    * @param value
    *   A ujson.Obj containing an OpenSearch-indexable object.
    * @return
    *   A Java Map containing the native Java representation of the object's
    *   contents.
    */
  private def unboxObj(value: ujson.Obj): java.util.Map[String, Any] = {
    val obj = value.obj

    val result: mutable.Map[String, Any] = mutable.Map()
    obj.foreach((k, v) => result.put(k, v.value))

    result.asJava
  }

  /** Initializes an OpenSearch index based on the provided [[Index]]
    * definition. This method creates the index with the specified name and
    * field mappings.
    *
    * @param client
    *   An OpenSearch client.
    * @param index
    *   The Index object containing the index name and field definitions.
    */
  private def initializeIndex(
      client: OpenSearchClient,
      index: Index
  ): Either[OpenSearchException, CreateIndexResponse] = {
    val mapping = TypeMapping.Builder()
    index.context.fields.foreach((field, datatype) => {
      datatype match {
        case OpenSearchDataType.Boolean =>
          mapping.properties(
            field,
            Property
              .Builder()
              .boolean_(BooleanProperty.Builder().build())
              .build()
          )
        case OpenSearchDataType.Integer =>
          mapping.properties(
            field,
            Property
              .Builder()
              .integer(IntegerNumberProperty.Builder().build())
              .build()
          )
      }
    })

    val request = new CreateIndexRequest.Builder()
      .index(index.context.name)
      .mappings(mapping.build())
      .build()

    try {
      Right(client.indices().create(request))
    } catch {
      case e: OpenSearchException => Left(e)
    }
  }

  /** Populates an OpenSearch index with data from the provided Index object.
    * This method converts the columnar data to JSON objects and uses bulk
    * operations to insert the data into the index.
    *
    * @param client
    *   An OpenSearch client.
    * @param index
    *   The Index object containing the index name and data to be inserted.
    */
  private def populateIndex(
      client: OpenSearchClient,
      index: Index
  ): Either[OpenSearchException, BulkResponse] = {
    val records = asJsonObjects(index.data)

    val bulkReqOps: IndexedSeq[BulkOperation] =
      records.zipWithIndex.map((rec, idx) => {
        BulkOperation
          .Builder()
          .index(
            IndexOperation
              .Builder()
              .id(idx.toString)
              .document(unboxObj(rec))
              .build()
          )
          .build()
      })

    val bulkReq = BulkRequest
      .Builder()
      .index(index.context.name)
      .operations(bulkReqOps.toList.asJava)
      .refresh(Refresh.WaitFor)
      .build()

    try {
      Right(client.bulk(bulkReq))
    } catch {
      case e: OpenSearchException => Left(e)
    }
  }

  /** Creates the provided [[Index]] in OpenSearch and populates it with its
    * data.
    *
    * @param client
    *   An OpenSearch client.
    * @param index
    *   The index definition, containing context (name, fields) and data to
    *   insert.
    */
  def createIndex(
      client: OpenSearchClient,
      index: Index
  ): Either[OpenSearchException, Unit] = {
    for {
      _ <- initializeIndex(client, index)
      _ <- populateIndex(client, index)
    } yield ()
  }
}
