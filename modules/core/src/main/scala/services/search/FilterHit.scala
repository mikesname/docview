package services.search

import models.EntityType
import play.api.libs.json.{Format, Json}

/**
  * Class representing a search engine filter hit
  */
case class FilterHit(
  id: String,
  did: String,
  name: String,
  `type`: EntityType.Value,
  parent: Option[String] = None,
  gid: Long
)

object FilterHit {
  implicit val fmt: Format[FilterHit] = Json.format[FilterHit]
}
