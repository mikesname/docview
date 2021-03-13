package models

import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology

import java.net.URL
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject
import services.data.Constants
import utils.EnumUtils


object DocumentaryUnitF {

  object CopyrightStatus extends Enumeration {
    val Yes = Value("yes")
    val No = Value("no")
    val Unknown = Value("unknown")

    implicit val format: Format[CopyrightStatus.Value] = EnumUtils.enumFormat(this)
  }

  object Scope extends Enumeration {
    val High = Value("high")
    val Medium = Value("medium")
    val Low = Value("low")

    implicit val format: Format[Scope.Value] = utils.EnumUtils.enumFormat(this)
  }

  val OTHER_IDENTIFIERS = "otherIdentifiers"
  val PUBLICATION_STATUS = "publicationStatus"
  val SCOPE = "scope"
  val COPYRIGHT = "copyrightStatus"

  import Entity._
  import eu.ehri.project.definitions.Ontology._

  implicit val documentaryUnitFormat: Format[DocumentaryUnitF] = (
    (__ \ TYPE).formatIfEquals(EntityType.DocumentaryUnit) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ OTHER_IDENTIFIERS).formatSeqOrSingleNullable[String] and
    (__ \ DATA \ PUBLICATION_STATUS).formatNullable[PublicationStatus.Value] and
    (__ \ DATA \ COPYRIGHT).formatNullableWithDefault(Some(CopyrightStatus.Unknown)) and
    (__ \ DATA \ SCOPE).formatNullable[Scope.Value] and
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).formatSeqOrEmpty[DocumentaryUnitDescriptionF]
  )(DocumentaryUnitF.apply, unlift(DocumentaryUnitF.unapply))

  implicit object Converter extends Writable[DocumentaryUnitF] {
    val restFormat: Format[DocumentaryUnitF] = documentaryUnitFormat
  }
}

case class DocumentaryUnitF(
  isA: EntityType.Value = EntityType.DocumentaryUnit,
  id: Option[String] = None,
  identifier: String,
  otherIdentifiers: Option[Seq[String]] = None,
  publicationStatus: Option[PublicationStatus.Value] = None,
  copyrightStatus: Option[DocumentaryUnitF.CopyrightStatus.Value] = Some(DocumentaryUnitF.CopyrightStatus.Unknown),
  scope: Option[DocumentaryUnitF.Scope.Value] = Some(DocumentaryUnitF.Scope.Low),

  @models.relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: Seq[DocumentaryUnitDescriptionF] = Nil
) extends ModelData
  with Persistable
  with Described {

  type D = DocumentaryUnitDescriptionF

  override def description(did: String): Option[DocumentaryUnitDescriptionF] =
    descriptions.find(d => d.id.isDefined && d.id.get == did)
}

object DocumentaryUnit {
  import Entity._
  import DescribedModel._
  import models.DocumentaryUnitF._
  import eu.ehri.project.definitions.Ontology.{OTHER_IDENTIFIERS => _, _}
  import EnumUtils.enumMapping

  implicit val metaReads: Reads[DocumentaryUnit] = (
    __.read[DocumentaryUnitF](documentaryUnitFormat) and
    (__ \ RELATIONSHIPS \ DOC_HELD_BY_REPOSITORY).readHeadNullable[Repository] and
    (__ \ RELATIONSHIPS \ DOC_IS_CHILD_OF).lazyReadHeadNullable(metaReads) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(DocumentaryUnit.apply _)


  implicit object DocumentaryUnitResource extends ContentType[DocumentaryUnit]  {
    val entityType = EntityType.DocumentaryUnit
    val contentType = ContentTypes.DocumentaryUnit
    implicit val restReads: Reads[DocumentaryUnit] = metaReads

    /**
     * When displaying doc units we need the
     * repositories urlPattern to create an external link. However this
     * is not a mandatory property and thus not returned by the REST
     * interface by default, unless we specify it explicitly.
     */
    override def defaultParams = Seq(
      Constants.INCLUDE_PROPERTIES_PARAM -> RepositoryF.URL_PATTERN,
      Constants.INCLUDE_PROPERTIES_PARAM -> Isdiah.OTHER_FORMS_OF_NAME,
      Constants.INCLUDE_PROPERTIES_PARAM -> Isdiah.PARALLEL_FORMS_OF_NAME,
      Constants.INCLUDE_PROPERTIES_PARAM -> RepositoryF.LOGO_URL
    )
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.DocumentaryUnit),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText,
      OTHER_IDENTIFIERS -> optional(seq(nonEmptyText)),
      PUBLICATION_STATUS -> optional(enumMapping(models.PublicationStatus)),
      COPYRIGHT -> optional(enumMapping(CopyrightStatus)),
      SCOPE -> optional(enumMapping(Scope)),
      DESCRIPTIONS -> seq(DocumentaryUnitDescription.form.mapping)
    )(DocumentaryUnitF.apply)(DocumentaryUnitF.unapply)
  )
}

case class DocumentaryUnit(
  data: DocumentaryUnitF,
  holder: Option[Repository] = None,
  parent: Option[DocumentaryUnit] = None,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends Model
  with DescribedModel
  with Hierarchical[DocumentaryUnit]
  with Holder[DocumentaryUnit]
  with Accessible {

  type T = DocumentaryUnitF

  def url: Option[URL] = (for {
    desc <- descriptions
    url <- desc.externalLink(this)
  } yield new URL(url)).headOption
}
