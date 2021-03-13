package models

import play.api.libs.json.Format
import utils.EnumUtils

object PublicationStatus extends Enumeration {
	type Status = Value
	val Draft = Value("Draft")
  val Published = Value("Published")

  implicit val _format: Format[PublicationStatus.Value] = EnumUtils.enumFormat(this)
}