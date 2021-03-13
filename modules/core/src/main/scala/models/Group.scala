package models

import play.api.libs.json._
import models.json._
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import eu.ehri.project.definitions.Ontology
import play.api.libs.json.JsObject


object GroupF {

  val NAME = "name"
  val DESCRIPTION = "description"

  import Entity._

  implicit val groupFormat: Format[GroupF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Group) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ NAME).format[String] and
    (__ \ DATA \ DESCRIPTION).formatNullable[String]
  )(GroupF.apply, unlift(GroupF.unapply))

  implicit object Converter extends Writable[GroupF] {
    lazy val restFormat: Format[GroupF] = groupFormat
  }
}

case class GroupF(
  isA: EntityType.Value = EntityType.Group,
  id: Option[String],
  identifier: String,
  name: String,
  description: Option[String] = None
) extends ModelData with Persistable

object Group {
  import GroupF._
  import Entity._
  import Ontology._

  private lazy implicit val systemEventReads = SystemEvent.SystemEventResource.restReads

  implicit val metaReads: Reads[Group] = (
    __.read[GroupF] and
    (__ \ RELATIONSHIPS \ ACCESSOR_BELONGS_TO_GROUP).lazyReadSeqOrEmpty(metaReads) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Group.apply _)

  implicit object GroupResource extends ContentType[Group]  {
    val entityType = EntityType.Group
    val contentType = ContentTypes.Group
    val restReads: Reads[Group] = metaReads
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Group),
      ID -> optional(text),
      IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      DESCRIPTION -> optional(nonEmptyText)
    )(GroupF.apply)(GroupF.unapply)
  )
}


case class Group(
  data: GroupF,
  groups: Seq[Group] = Nil,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends Model
  with Accessor
  with Accessible {

  type T = GroupF

  override def toStringLang(implicit messages: Messages): String = data.name
}

