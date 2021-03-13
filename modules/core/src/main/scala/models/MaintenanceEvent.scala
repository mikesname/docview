package models

import models.json._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._


case class MaintenanceEventF(
  isA: EntityType.Value = EntityType.MaintenanceEvent,
  id: Option[String],
  date: Option[String] = None,
  source: Option[String] = None,
  agentType: Option[String] = None,
  eventType: Option[String] = None,
  order: Option[Int] = None
) extends ModelData with Persistable {
  override def toString: String = this match {
    case MaintenanceEventF(_, _, Some(dt), Some(src), _, _, _) => s"$dt: $src"
    case MaintenanceEventF(_, _, Some(dt), _, _, Some(et), _) => s"$dt: $et"
    case MaintenanceEventF(_, _, _, Some(src), _, _, _) => src
    case MaintenanceEventF(_, _, _, _, _, Some(et), _) => et
    case _ => "Unknown maintenance event"
  }
}

object MaintenanceEventF {
  val DATE = "date"
  val SOURCE = "source"
  val AGENT_TYPE = "agentType"
  val EVENT_TYPE = "eventType"
  val ORDER = "order"

  import Entity._

  implicit val maintenanceEventFormat: Format[MaintenanceEventF] = (
    (__ \ TYPE).formatIfEquals(EntityType.MaintenanceEvent) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ DATE).formatHeadOrSingleNullable[String] and
    (__ \ DATA \ SOURCE).formatNullable[String] and
    (__ \ DATA \ AGENT_TYPE).formatNullable[String] and
    (__ \ DATA \ EVENT_TYPE).formatNullable[String] and
    (__ \ DATA \ ORDER).formatNullable[Int]
  )(MaintenanceEventF.apply, unlift(MaintenanceEventF.unapply))

  val form = Form(mapping(
    ISA -> ignored(EntityType.MaintenanceEvent),
    ID -> optional(nonEmptyText),
    DATE -> optional(nonEmptyText),
    SOURCE -> optional(nonEmptyText),
    AGENT_TYPE -> optional(nonEmptyText),
    EVENT_TYPE -> optional(nonEmptyText),
    ORDER -> optional(number)
  )(MaintenanceEventF.apply)(MaintenanceEventF.unapply))
}

object MaintenanceEvent {

  import Entity.META

  implicit val metaReads: Reads[MaintenanceEvent] = (
    __.read[MaintenanceEventF] and
    (__ \ META).readWithDefault(Json.obj())
  )(MaintenanceEvent.apply _)

  implicit object Converter extends Readable[MaintenanceEvent] {
    val restReads: Reads[MaintenanceEvent] = metaReads
  }
}

case class MaintenanceEvent(
  data: MaintenanceEventF,
  meta: JsObject = JsObject(Seq())
) extends Model {

  type T = MaintenanceEventF

  override def toStringLang(implicit messages: Messages): String = data.toString
}
