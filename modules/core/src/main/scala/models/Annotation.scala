package models

import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import utils.EnumUtils


object AnnotationF {
  val BODY = "body"
  val FIELD = "field"
  val ANNOTATION_TYPE = "annotationType"
  val COMMENT = "comment"
  val IS_PUBLIC = "isPublic"

  object AnnotationType extends Enumeration {
    type Type = Value
    val Comment = Value("comment")
    val Aggregation = Value("aggregation")

    implicit val _format: Format[AnnotationType.Value] = EnumUtils.enumFormat(this)
  }

  import AnnotationF.{ANNOTATION_TYPE => ANNOTATION_TYPE_PROP}
  import Entity._
  import Ontology._

  implicit val annotationFormat: Format[AnnotationF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Annotation) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ ANNOTATION_TYPE_PROP).formatNullableWithDefault(Some(AnnotationType.Comment)) and
    (__ \ DATA \ BODY).format[String] and
    (__ \ DATA \ FIELD).formatNullable[String] and
    (__ \ DATA \ COMMENT).formatNullable[String] and
    (__ \ DATA \ IS_PROMOTABLE).formatWithDefault(false)
  )(AnnotationF.apply, unlift(AnnotationF.unapply))

  implicit object Converter extends Writable[AnnotationF] {
    lazy val restFormat: Format[AnnotationF] = annotationFormat
    lazy val clientFormat: Format[AnnotationF] = Json.format[AnnotationF]
  }
}

case class AnnotationF(
  isA: EntityType.Value = EntityType.Annotation,
  id: Option[String],
  annotationType: Option[AnnotationF.AnnotationType.Type] = Some(AnnotationF.AnnotationType.Comment),
  body: String,
  field: Option[String] = None,
  comment: Option[String] = None,
  isPromotable: Boolean = false
) extends ModelData with Persistable


object Annotation {
  import Entity._
  import Ontology._
  import EnumUtils.enumMapping

  private implicit val anyModelReads = Model.Converter.restReads
  private implicit val userProfileMetaReads = UserProfile.UserProfileResource.restReads
  private lazy implicit val systemEventReads = SystemEvent.SystemEventResource.restReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[Annotation] = (
    __.read[AnnotationF] and
    (__ \ RELATIONSHIPS \ ANNOTATION_ANNOTATES).lazyReadSeqOrEmpty[Annotation](metaReads) and
    (__ \ RELATIONSHIPS \ ANNOTATOR_HAS_ANNOTATION).readHeadNullable[UserProfile] and
    (__ \ RELATIONSHIPS \ ANNOTATION_HAS_SOURCE).lazyReadHeadNullable[Model](anyModelReads) and
    (__ \ RELATIONSHIPS \ ANNOTATES).lazyReadHeadNullable[Model](anyModelReads) and
    (__ \ RELATIONSHIPS \ ANNOTATES_PART).readHeadNullable[Entity] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty[Accessor] and
    (__ \ RELATIONSHIPS \ PROMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ DEMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Annotation.apply _)

  implicit object AnnotationResource extends ContentType[Annotation]  {
    val entityType = EntityType.Annotation
    val contentType = ContentTypes.Annotation
    val restReads: Reads[Annotation] = metaReads
  }

  /**
   * Filter annotations on individual fields
   */
  def fieldAnnotations(partId: Option[String], annotations: Seq[Annotation]): Seq[Annotation] =
    annotations.filter(_.targetParts.exists(p => partId.contains(p.id))).filter(_.data.field.isDefined)

  /**
   * Filter annotations on the item
   */
  def itemAnnotations(partId: Option[String], annotations: Seq[Annotation]): Seq[Annotation] =
    annotations.filter(_.targetParts.exists(p => partId.contains(p.id)))

  /**
   * Filter annotations on the item
   */
  def itemAnnotations(annotations: Seq[Annotation]): Seq[Annotation] =
      annotations.filter(_.targetParts.isEmpty).filter(_.data.field.isDefined)

  import AnnotationF.{ANNOTATION_TYPE => ANNOTATION_TYPE_PROP, _}

  val form: Form[AnnotationF] = Form(mapping(
    ISA -> ignored(EntityType.Annotation),
    ID -> optional(nonEmptyText),
    ANNOTATION_TYPE_PROP -> optional(enumMapping(AnnotationType)),
    BODY -> nonEmptyText(maxLength = 600),
    FIELD -> optional(nonEmptyText),
    COMMENT -> optional(nonEmptyText),
    IS_PUBLIC -> boolean
  )(AnnotationF.apply)(AnnotationF.unapply))

  val multiForm: Form[Seq[(String, AnnotationF)]] = Form(single(
    "annotation" -> seq(tuple(
      "id" -> nonEmptyText,
      "data" -> form.mapping
    ))
  ))
}

case class Annotation(
  data: AnnotationF,
  annotations: Seq[Annotation] = Nil,
  user: Option[UserProfile] = None,
  source: Option[Model] = None,
  target: Option[Model] = None,
  targetParts: Option[Entity] = None,
  accessors: Seq[Accessor] = Nil,
  promoters: Seq[UserProfile] = Nil,
  demoters: Seq[UserProfile] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends Model with Accessible with Promotable {

  type T = AnnotationF

  def isPromotable: Boolean = data.isPromotable
  def isOwnedBy(userOpt: Option[UserProfile]): Boolean = {
    (for {
      u <- userOpt
      creator <-user
    } yield u.id == creator.id).getOrElse(false)
  }

  def formatted: String = {
    s"${data.comment.map(c => s"$c\n\n").getOrElse("")}${data.body}"
  }

  override def toStringLang(implicit messages: Messages): String =
    Messages("annotation.label", user.map(_.toStringLang).getOrElse("unknown"),
      latestEvent.map(_.data.datetime).getOrElse("?"))
}
