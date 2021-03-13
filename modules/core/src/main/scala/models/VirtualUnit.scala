package models

import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import services.data.Constants


object VirtualUnitF {

  val INCLUDE_REF = "includeRef"

  import Entity._
  import Ontology._

  implicit val virtualUnitFormat: Format[VirtualUnitF] = (
    (__ \ TYPE).formatIfEquals(EntityType.VirtualUnit) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).formatSeqOrEmpty[DocumentaryUnitDescriptionF]
  )(VirtualUnitF.apply, unlift(VirtualUnitF.unapply))

  implicit object Converter extends Writable[VirtualUnitF] {
    val restFormat: Format[VirtualUnitF] = virtualUnitFormat
  }
}

case class VirtualUnitF(
  isA: EntityType.Value = EntityType.VirtualUnit,
  id: Option[String] = None,
  identifier: String,
  @models.relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: Seq[DocumentaryUnitDescriptionF] = Nil
) extends ModelData
  with Persistable
  with Described {

  type D = DocumentaryUnitDescriptionF

  override def description(did: String): Option[DocumentaryUnitDescriptionF]
      = descriptions.find(d => d.id.isDefined && d.id.get == did)
}

object VirtualUnit {

  import Entity._
  import models.VirtualUnitF._
  import Ontology._

  implicit val metaReads: Reads[VirtualUnit] = (
    __.read[VirtualUnitF](virtualUnitFormat) and
    (__ \ RELATIONSHIPS \ VC_INCLUDES_UNIT).readSeqOrEmpty(DocumentaryUnit.DocumentaryUnitResource.restReads) and
    (__ \ RELATIONSHIPS \ VC_HAS_AUTHOR).readHeadNullable(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ VC_IS_PART_OF).lazyReadHeadNullable(metaReads) and
    (__ \ RELATIONSHIPS \ DOC_IS_CHILD_OF).readHeadNullable[Repository] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(VirtualUnit.apply _)


  implicit object VirtualUnitResource extends ContentType[VirtualUnit]  {
    val entityType = EntityType.VirtualUnit
    val contentType = ContentTypes.VirtualUnit
    implicit val restReads: Reads[VirtualUnit] = metaReads

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
      ISA -> ignored(EntityType.VirtualUnit),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText,
      "descriptions" -> seq(DocumentaryUnitDescription.form.mapping)
    )(VirtualUnitF.apply)(VirtualUnitF.unapply)
  )
}

case class VirtualUnit(
  data: VirtualUnitF,
  includedUnits: Seq[DocumentaryUnit] = List.empty,
  author: Option[Accessor] = None,
  parent: Option[VirtualUnit] = None,
  holder: Option[Repository] = None,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends Model
  with Hierarchical[VirtualUnit]
  with Holder[VirtualUnit]
  with DescribedModel
  with Accessible {

  type T = VirtualUnitF

  override def toStringLang(implicit messages: Messages): String = {
    if (data.descriptions.nonEmpty) super.toStringLang(messages)
    else includedUnits.headOption.map(_.toStringLang(messages)).getOrElse(id)
  }

  def allDescriptions: Seq[DocumentaryUnitDescriptionF] =
    includedUnits.flatMap(_.descriptions) ++ data.descriptions

  def asDocumentaryUnit: DocumentaryUnit = new DocumentaryUnit(
    new DocumentaryUnitF(
      id = data.id,
      identifier = data.identifier,
      descriptions = if(descriptions.isEmpty) allDescriptions else descriptions
    ),
    holder = holder,
    accessors = accessors,
    latestEvent = latestEvent,
    meta = meta
  )
}
