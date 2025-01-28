package datagen

import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.Refresh
import org.opensearch.client.opensearch._types.mapping.{
  BooleanProperty,
  IntegerNumberProperty,
  Property,
  TypeMapping
}
import org.opensearch.client.opensearch.core.BulkRequest
import org.opensearch.client.opensearch.core.bulk.{
  BulkOperation,
  IndexOperation
}
import org.opensearch.client.opensearch.indices.CreateIndexRequest
import queries.OpenSearchDataType

import scala.collection.mutable
import scala.jdk.CollectionConverters.{MapHasAsJava, SeqHasAsJava}

object IndexCreator {
  private def asJsonObjects(
      data: Map[String, List[Null | Int | Boolean]]
  ): IndexedSeq[ujson.Value] = {
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

  private def unboxObj(value: ujson.Value): java.util.Map[String, Any] = {
    val obj = value.obj

    val result: mutable.Map[String, Any] = mutable.Map()
    obj.foreach((k, v) => result.put(k, v.value))

    result.asJava
  }

  def createIndex(client: OpenSearchClient, index: Index): Unit = {
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

    client.indices().create(request.build())
    System.out.println(s"Created index: ${index.context.name}")
    System.out.println(s"Fields: ${index.context.fields}")

    val records = asJsonObjects(index.data)
    records.zipWithIndex.foreach((row, i) => {
      System.out.println(s"$i: ${row.obj.asJava}")
    })

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
    client.bulk(bulkReq)
  }
}
