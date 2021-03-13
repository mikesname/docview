package models

import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class DataContentTypeF(
  id: Option[String],
  isA: EntityType.Value = EntityType.ContentType
) extends ModelData

object DataContentTypeF {
  implicit val format: Format[DataContentTypeF] = (
    (__ \ Entity.ID).formatNullable[String] and
    (__ \ Entity.TYPE).formatIfEquals(EntityType.ContentType)
  )(DataContentTypeF.apply, unlift(DataContentTypeF.unapply))
}

/**
  * A data type limiting the scope of a permission grant.
  * Instances correspond to e.g. "DocumentaryUnit", "Repository" etc
  */
case class DataContentType(
  data: DataContentTypeF,
  meta: JsObject
) extends Model {
  type T = DataContentTypeF
}


object DataContentType {
  import Entity._
  implicit val format: Format[DataContentType] = (
    __.format[DataContentTypeF] and
      (__ \ META).formatWithDefault(Json.obj())
    )(DataContentType.apply, unlift(DataContentType.unapply))
  implicit object Converter extends Readable[DataContentType] {
    val restReads: Format[DataContentType] = format
  }
}
