package models

import base.{AnnotatableEntity, AccessibleEntity, Accessor, Formable}
import models.base.Persistable
import defines.EntityType
import play.api.libs.json.{Format, Json}
import models.json.{ClientConvertable, RestConvertable}


object AnnotationF {
  val BODY = "body"
  val FIELD = "field"
  val ANNOTATION_TYPE = "annotationType"
  val COMMENT = "comment"

  object AnnotationType extends Enumeration {
    type Type = Value
    val Comment = Value("comment")
    val Aggregation = Value("aggregation")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  lazy implicit val annotationFormat: Format[AnnotationF] = json.AnnotationFormat.restFormat


  implicit object Converter extends RestConvertable[AnnotationF] with ClientConvertable[AnnotationF] {
    lazy val restFormat = models.json.rest.annotationFormat
    lazy val clientFormat = models.json.client.annotationFormat
  }
}

case class AnnotationF(
  isA: EntityType.Value = EntityType.Annotation,
  id: Option[String],
  annotationType: Option[AnnotationF.AnnotationType.Type] = Some(AnnotationF.AnnotationType.Comment),
  body: String,
  field: Option[String] = None,
  comment: Option[String] = None
) extends Persistable


object Annotation {
  final val ANNOTATES_REL = "hasAnnotationTarget"
  final val ACCESSOR_REL = "hasAnnotation"
  final val SOURCE_REL = "hasAnnotationBody"
}

case class Annotation(val e: Entity) extends AccessibleEntity
  with AnnotatableEntity
  with Formable[AnnotationF] {

  def annotations: List[Annotation] = e.relations(Annotation.ANNOTATES_REL).map(Annotation(_))
  def accessor: Option[Accessor] = e.relations(Annotation.ACCESSOR_REL).headOption.map(Accessor(_))
  def source: Option[AnnotatableEntity] = e.relations(Annotation.SOURCE_REL)
      .headOption.flatMap(AnnotatableEntity.fromEntity(_))

  /**
   * Output a formatted label representation. TODO: Improve.
   * @return
   */
  def formatted: String = {
    "%s%s".format(
      formable.comment.map(c => s"$c\n\n").getOrElse(""),
      formable.body
    )
  }

  lazy val formable: AnnotationF = Json.toJson(e).as[AnnotationF]
  lazy val formableOpt: Option[AnnotationF] = Json.toJson(e).asOpt[AnnotationF]
}

