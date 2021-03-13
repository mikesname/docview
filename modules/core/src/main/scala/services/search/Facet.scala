package services.search

import play.api.libs.json._
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNumber



case object FacetDisplay extends Enumeration {
  val Choice = Value("choice")
  val List = Value("list")
  val DropDown = Value("dropdown")
  val Boolean = Value("boolean")
  val Date = Value("date")
  val Hidden = Value("hidden")
}


sealed trait Facet {
  def value: String
  def name: Option[String]
  def applied: Boolean
  def count: Int
}

case class FieldFacet(
  value: String,
  name: Option[String] = None,
  count: Int = 0,
  applied: Boolean = false
) extends Facet

case class QueryFacet(
  value: String,
  name: Option[String] = None,
  count: Int = 0,
  range: QueryPoint,
  applied: Boolean = false
) extends Facet

object Facet {
  implicit def facetWrites: Writes[Facet] = Writes[Facet] { f =>
    Json.obj(
      "count" -> JsNumber(f.count),
      "value" -> JsString(f.value),
      "name" -> Json.toJson(f.name),
      "applied" -> JsBoolean(f.applied)
    )
  }
}

