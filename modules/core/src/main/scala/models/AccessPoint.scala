package models

import models.json.JsPathExtensions
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject
import play.api.i18n.Messages
import utils.EnumUtils


object AccessPointF {

  val TYPE = "type"
  val NAME = "name"
  val DESCRIPTION = "description"

  object AccessPointType extends Enumeration {
    type Type = Value
    val CreatorAccess = Value("creator")
    val PersonAccess = Value("person")
    val FamilyAccess = Value("family")
    val CorporateBodyAccess = Value("corporateBody")
    val SubjectAccess = Value("subject")
    val PlaceAccess = Value("place")
    val GenreAccess = Value("genre")

    implicit val format: Format[AccessPointType.Value] = EnumUtils.enumFormat(this)

    def exceptCreator: ValueSet = values.filterNot(_ == CreatorAccess)
  }

  import Entity.{TYPE => ETYPE,_}

  implicit val accessPointFormat: Format[AccessPointF] = (
    (__ \ ETYPE).formatIfEquals(EntityType.AccessPoint) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ TYPE).formatWithDefault(AccessPointType.SubjectAccess) and
    (__ \ DATA \ NAME).format[String] and
    (__ \ DATA \ DESCRIPTION).formatNullable[String]
  )(AccessPointF.apply, unlift(AccessPointF.unapply))

  implicit object Converter extends Writable[AccessPointF] {
    lazy val restFormat: Format[AccessPointF] = accessPointFormat
  }
}

case class AccessPointF(
  isA: EntityType.Value = EntityType.AccessPoint,
  id: Option[String],
  accessPointType: AccessPointF.AccessPointType.Value,
  name: String,
  description: Option[String] = None
) extends ModelData with Persistable {

  /**
   * Given a set of links, see if we can find one with this access point
   * as a body.
   */
  def linkFor(links: Seq[Link]): Option[Link] = links.find(_.bodies.exists(body => body.data.id == id))

  /**
   * Given a set of links, see if we can find one with this access point
   * as a body.
   */
  def linksFor(links: Seq[Link]): Seq[Link] = links.filter(_.bodies.exists(body => body.data.id == id))

  /**
   * Given an item and a set of links, see if we can resolve the
   * opposing target item.
   */
  def target(item: Model, links: Seq[Link]): Option[(Link,Model)] = linkFor(links).flatMap { link =>
    link.opposingTarget(item).map { target =>
      (link, target)
    }
  }
}

object AccessPoint {
  import Entity._
  import AccessPointF.{TYPE => ETYPE, _}
  import EnumUtils.enumMapping

  implicit val metaReads: Reads[AccessPoint] = (
    __.read[AccessPointF] and
      (__ \ META).readWithDefault(Json.obj())
    )(AccessPoint.apply _)

  implicit object Converter extends Readable[AccessPoint] {
    val restReads: Reads[AccessPoint] = metaReads
  }


  def linksOfType(links: Seq[Link], `type`: AccessPointF.AccessPointType.Value): Seq[Link]
      = links.filter(_.bodies.exists(body => body.data.accessPointType == `type`))

  val form = Form(mapping(
    ISA -> ignored(EntityType.AccessPoint),
    ID -> optional(nonEmptyText),
    ETYPE -> enumMapping(AccessPointType),
    NAME -> nonEmptyText, // TODO: Validate this server side
    DESCRIPTION -> optional(nonEmptyText)
  )(AccessPointF.apply)(AccessPointF.unapply))
}


case class AccessPoint(
  data: AccessPointF,
  meta: JsObject = JsObject(Seq())
) extends Model {

  type T = AccessPointF

  override def toStringLang(implicit messages: Messages) = s"Access Point: ($id)"
}
