package models

import eu.ehri.project.definitions.Ontology
import models.json._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, _}


object VocabularyType extends Enumeration {
  type Type = Value
}

object VocabularyF {
  val NAME = "name"
  val ALLOW_PUBLIC = Ontology.IS_PROMOTABLE
  val DESCRIPTION = "description"

  import Entity._

  implicit val vocabularyFormat: Format[VocabularyF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Vocabulary) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ NAME).formatNullable[String] and
    (__ \ DATA \ DESCRIPTION).formatNullable[String] and
    (__ \ DATA \ ALLOW_PUBLIC).formatWithDefault(false)
  )(VocabularyF.apply, unlift(VocabularyF.unapply))

  implicit object Converter extends Writable[VocabularyF] {
    lazy val restFormat: Format[VocabularyF] = vocabularyFormat
  }
}


case class VocabularyF(
  isA: EntityType.Value = EntityType.Vocabulary,
  id: Option[String],
  identifier: String,
  name: Option[String],
  description: Option[String],
  isPromotable: Boolean = false
) extends ModelData with Persistable


object Vocabulary {
  import Entity._
  import Ontology._
  import VocabularyF._

  private implicit val systemEventReads = SystemEvent.SystemEventResource.restReads

  implicit val metaReads: Reads[Vocabulary] = (
    __.read[VocabularyF] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PROMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ DEMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Vocabulary.apply _)

  implicit object VocabularyResource extends ContentType[Vocabulary]  {
    val entityType = EntityType.Vocabulary
    val contentType = ContentTypes.Vocabulary
    val restReads: Reads[Vocabulary] = metaReads
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Vocabulary),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> optional(nonEmptyText),
      DESCRIPTION -> optional(nonEmptyText),
      ALLOW_PUBLIC -> default(boolean, true)
    )(VocabularyF.apply)(VocabularyF.unapply)
  )
}


case class Vocabulary(
  data: VocabularyF,
  accessors: Seq[Accessor] = Nil,
  promoters: Seq[UserProfile] = Nil,
  demoters: Seq[UserProfile] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends Model
  with Accessible
  with Promotable
  with Holder[Concept] {

  type T = VocabularyF

  override def isPromotable: Boolean = data.isPromotable

  override def toStringLang(implicit messages: Messages): String = data.name.getOrElse(id)
}
