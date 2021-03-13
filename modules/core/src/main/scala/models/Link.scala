package models

import play.api.libs.json._
import models.json._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import utils.EnumUtils


object LinkF {

  val LINK_TYPE = "type"
  val LINK_FIELD = "field"
  val DESCRIPTION = "description"
  val DATES = "dates"

  object LinkType extends Enumeration {
    type Type = Value
    val Identity = Value("identity")
    val Associative = Value("associative")
    val Family = Value("family")
    val Hierarchical = Value("hierarchical")
    val Temporal = Value("temporal")
    val Copy = Value("copy")

    implicit val _fmt: Format[LinkType.Value] = EnumUtils.enumFormat(this)
  }

  object LinkField extends Enumeration {
    type Field = Value
    val LocationOfOriginals = Value(IsadG.LOCATION_ORIGINALS)
    val LocationOfCopies = Value(IsadG.LOCATION_COPIES)
    val RelatedUnits = Value(IsadG.RELATED_UNITS)

    implicit val _fmt: Format[LinkField.Value] = utils.EnumUtils.enumFormat(this)
  }

  object LinkCopyType extends Enumeration {
    val CopyRepositoryToOriginalRepository = Value("copyRepositoryToOriginalRepository")
    val CopyCollectionToOriginalCollection = Value("copyCollectionToOriginalCollection")
    val CopyCollectionToOriginalRepository = Value("copyCollectionToOriginalRepository")
    val CopyRepositoryToOriginalCollection = Value("copyRepositoryToOriginalCollection")

    implicit val _fmt: Format[LinkCopyType.Value] = utils.EnumUtils.enumFormat(this)
  }

  import Entity._
  import Ontology._
  import play.api.libs.functional.syntax._

  implicit val linkFormat: Format[LinkF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Link) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ LINK_TYPE).formatWithDefault(LinkType.Associative) and
    (__ \ DATA \ LINK_FIELD).formatNullable[LinkField.Field] and
    (__ \ DATA \ DESCRIPTION).formatNullable[String] and
    (__ \ DATA \ IS_PROMOTABLE).formatWithDefault(false) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).formatSeqOrEmpty[DatePeriodF]
  )(LinkF.apply, unlift(LinkF.unapply))

  implicit object Converter extends Writable[LinkF] {
    lazy val restFormat: Format[LinkF] = linkFormat
  }
}

case class LinkF(
  isA: EntityType.Value = EntityType.Link,
  id: Option[String],
  linkType: LinkF.LinkType.Type,
  linkField: Option[LinkF.LinkField.Field] = None,
  description: Option[String] = None,
  isPromotable: Boolean = false,
  @models.relation(Ontology.ENTITY_HAS_DATE)
  dates: Seq[DatePeriodF] = Nil
) extends ModelData with Persistable with Temporal


object Link {
  import Entity._
  import Ontology._
  import play.api.libs.functional.syntax._
  import EnumUtils.enumMapping

  private implicit val userProfileMetaReads = models.UserProfile.UserProfileResource.restReads
  private implicit val accessPointReads = models.AccessPoint.Converter.restReads
  private implicit val systemEventReads = SystemEvent.SystemEventResource.restReads

  implicit val metaReads: Reads[Link] = (
    __.read[LinkF] and
    (__ \ RELATIONSHIPS \ LINK_HAS_TARGET).lazyReadSeqOrEmpty(Model.Converter.restReads) and
    (__ \ RELATIONSHIPS \ LINK_HAS_SOURCE).lazyReadHeadNullable(Model.Converter.restReads) and
    (__ \ RELATIONSHIPS \ LINK_HAS_LINKER).readHeadNullable(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ LINK_HAS_BODY).readSeqOrEmpty[AccessPoint] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PROMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ DEMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Link.apply _)

  implicit object LinkResource extends ContentType[Link]  {
    val entityType = EntityType.Link
    val contentType = ContentTypes.Link
    val restReads: Reads[Link] = metaReads
  }

  import LinkF._

  val form = Form(mapping(
    Entity.ISA -> ignored(EntityType.Link),
    Entity.ID -> optional(nonEmptyText),
    LINK_TYPE -> default(enumMapping(LinkType), LinkType.Associative),
    LINK_FIELD -> optional(enumMapping(LinkField)),
    DESCRIPTION -> optional(nonEmptyText), // TODO: Validate this server side
    Ontology.IS_PROMOTABLE -> default(boolean, false),
    DATES -> seq(DatePeriod.form.mapping)
  )(LinkF.apply)(LinkF.unapply))

  val multiForm = Form(    single(
    "link" -> list(tuple(
      "id" -> nonEmptyText,
      "data" -> form.mapping,
      "accessPoint" -> optional(nonEmptyText)
    ))
  ))

  def formWithCopyOptions(copy: Boolean, src: Model, dest: Model)(implicit messages: Messages): Form[LinkF] =
    if (!copy) form
    else form.fill(LinkF(
      id = None,
      linkType = LinkType.Copy,
      linkField = copyLinkField(src, dest),
      description = copyLinkDescription(src, dest)
    ))

  def copyLinkType(src: Model, dest: Model): Option[LinkCopyType.Value] =
    (src, dest) match {
      case (r1: Repository, r: Repository) => Some(LinkCopyType.CopyRepositoryToOriginalRepository)
      case (d1: DocumentaryUnit, d2: DocumentaryUnit) => Some(LinkCopyType.CopyCollectionToOriginalCollection)
      case (d: DocumentaryUnit, r: Repository) => Some(LinkCopyType.CopyCollectionToOriginalRepository)
      case (r1: Repository, d: DocumentaryUnit) => Some(LinkCopyType.CopyRepositoryToOriginalCollection)
      case _ => None
    }

  def copyLinkField(src: Model, dest: Model): Option[LinkField.Value] =
    copyLinkType(src, dest).map(_ => LinkField.LocationOfOriginals)

  def copyLinkDescription(src: Model, dest: Model)(implicit messages: Messages): Option[String] =
    copyLinkType(src, dest).map( t => Messages(s"link.copy.preset.$t", src.toStringLang, dest.toStringLang))
}

case class Link(
  data: LinkF,
  targets: Seq[Model] = Nil,
  source: Option[Model] = None,
  linker: Option[Accessor] = None,
  bodies: Seq[AccessPoint] = Nil,
  accessors: Seq[Accessor] = Nil,
  promoters: Seq[UserProfile] = Nil,
  demoters: Seq[UserProfile] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends Model
  with Accessible
  with Promotable {

  type T = LinkF

  def isDirectional: Boolean = source.isDefined

  def destination: Option[Model] = source match {
    case None => None
    case Some(m) => targets.find(_.id != m.id)
  }
  def isPromotable: Boolean = data.isPromotable
  def opposingTarget(item: Model): Option[Model] = opposingTarget(item.id)
  def opposingTarget(itemId: String): Option[Model] = targets.find(_.id != itemId)

  /**
    * Targets sorted first by item type and then by id
    */
  def sortedTargets: Seq[Model] = targets.sortBy(t => (t.isA, t.id))
}
