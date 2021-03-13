package models

import eu.ehri.project.definitions.Ontology
import Description._
import models.json._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import forms._

case class IsadGIdentity(
  name: String,
  parallelFormsOfName: Seq[String] = Nil,
  ref: Option[String] = None,
  `abstract`: Option[String] = None,
  @models.relation(Ontology.ENTITY_HAS_DATE)
  dates: Seq[DatePeriodF] = Nil,
  unitDates: Seq[String] = Nil,
  levelOfDescription: Option[String] = None,
  physicalLocation: Seq[String] = Nil,
  extentAndMedium: Option[String] = None
) extends AttributeSet

case class IsadGContext(
  biographicalHistory: Option[String] = None,
  archivalHistory: Option[String] = None,
  acquisition: Option[String] = None
) extends AttributeSet

case class IsadGContent(
  scopeAndContent: Option[String] = None,
  appraisal: Option[String] = None,
  accruals: Option[String] = None,
  systemOfArrangement: Option[String] = None
) extends AttributeSet

case class IsadGConditions(
  conditionsOfAccess: Option[String] = None,
  conditionsOfReproduction: Option[String] = None,
  languageOfMaterials: Seq[String] = Nil,
  scriptOfMaterials: Seq[String] = Nil,
  physicalCharacteristics: Option[String] = None,
  findingAids: Seq[String] = Nil
) extends AttributeSet

case class IsadGMaterials(
  locationOfOriginals: Seq[String] = Nil,
  locationOfCopies: Seq[String] = Nil,
  relatedUnitsOfDescription: Seq[String] = Nil,
  separatedUnitsOfDescription: Seq[String] = Nil,
  publicationNote: Option[String] = None
) extends AttributeSet


case class IsadGControl(
  archivistNote: Option[String] = None,
  sources: Seq[String] = Nil,
  rulesAndConventions: Option[String] = None,
  datesOfDescriptions: Option[String] = None,
  processInfo: Seq[String] = Nil
)

object DocumentaryUnitDescriptionF {

  import Entity._
  import Ontology._
  import models.IsadG._

  implicit val documentaryUnitDescriptionFormat: Format[DocumentaryUnitDescriptionF] = (
    (__ \ TYPE).formatIfEquals(EntityType.DocumentaryUnitDescription) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ LANG_CODE).format[String] and
    (__ \ DATA \ IDENTIFIER).formatNullable[String] and
    __.format[IsadGIdentity]((
      (__ \ DATA \ TITLE).format[String] and
      (__ \ DATA \ PARALLEL_FORMS_OF_NAME).formatSeqOrSingle[String] and
      (__ \ DATA \ REF).formatNullable[String] and
      (__ \ DATA \ ABSTRACT).formatNullable[String] and
      (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).formatSeqOrEmpty[DatePeriodF] and
      (__ \ DATA \ UNIT_DATES).formatSeqOrSingle[String] and
      (__ \ DATA \ LEVEL_OF_DESCRIPTION).formatNullable[String] and
      (__ \ DATA \ PHYSICAL_LOCATION).formatSeqOrSingle[String] and
      (__ \ DATA \ EXTENT_MEDIUM).formatNullable[String]
    )(IsadGIdentity.apply, unlift(IsadGIdentity.unapply))) and
    (__ \ DATA).format[IsadGContext]((
      (__ \ ADMIN_BIOG).formatNullable[String] and
      (__ \ ARCH_HIST).formatNullable[String] and
      (__ \ ACQUISITION).formatNullable[String]
    )(IsadGContext.apply, unlift(IsadGContext.unapply))) and
    (__ \ DATA).format[IsadGContent]((
      (__ \ SCOPE_CONTENT).formatNullable[String] and
      (__ \ APPRAISAL).formatNullable[String] and
      (__ \ ACCRUALS).formatNullable[String] and
      (__ \ SYS_ARR).formatNullable[String]
    )(IsadGContent.apply, unlift(IsadGContent.unapply))) and
    (__ \ DATA).format[IsadGConditions]((
      (__ \ ACCESS_COND).formatNullable[String] and
      (__ \ REPROD_COND).formatNullable[String] and
      (__ \ LANG_MATERIALS).formatSeqOrSingle[String] and
      (__ \ SCRIPT_MATERIALS).formatSeqOrSingle[String] and
      (__ \ PHYSICAL_CHARS).formatNullable[String] and
      (__ \ FINDING_AIDS).formatSeqOrSingle[String]
    )(IsadGConditions.apply, unlift(IsadGConditions.unapply))) and
    (__ \ DATA).format[IsadGMaterials]((
      (__ \ LOCATION_ORIGINALS).formatSeqOrSingle[String] and
      (__ \ LOCATION_COPIES).formatSeqOrSingle[String] and
      (__ \ RELATED_UNITS).formatSeqOrSingle[String] and
      (__ \ SEPARATED_UNITS).formatSeqOrSingle[String] and
      (__ \ PUBLICATION_NOTE).formatNullable[String]
    )(IsadGMaterials.apply, unlift(IsadGMaterials.unapply))) and
    (__ \ DATA \ NOTES).formatSeqOrSingle[String] and
    (__ \ DATA).format[IsadGControl]((
      (__ \ ARCHIVIST_NOTE).formatNullable[String] and
      (__ \ SOURCES).formatSeqOrSingle[String] and
      (__ \ RULES_CONVENTIONS).formatNullable[String] and
      (__ \ DATES_DESCRIPTIONS).formatNullable[String] and
      (__ \ PROCESS_INFO).formatSeqOrSingle[String]
    )(IsadGControl.apply, unlift(IsadGControl.unapply))) and
    (__ \ DATA \ SOURCE_FILE_ID).formatNullable[String] and
    (__ \ DATA \ CREATION_PROCESS).formatWithDefault(CreationProcess.Manual) and
    (__ \ RELATIONSHIPS \ HAS_ACCESS_POINT).formatSeqOrEmpty[AccessPointF] and
    (__ \ RELATIONSHIPS \ HAS_MAINTENANCE_EVENT).formatSeqOrEmpty[MaintenanceEventF] and
    (__ \ RELATIONSHIPS \ HAS_UNKNOWN_PROPERTY).formatSeqOrEmpty[Entity]
  )(DocumentaryUnitDescriptionF.apply, unlift(DocumentaryUnitDescriptionF.unapply))

  implicit object Converter extends Writable[DocumentaryUnitDescriptionF] {
    val restFormat: Format[DocumentaryUnitDescriptionF] = documentaryUnitDescriptionFormat
  }
}

case class DocumentaryUnitDescriptionF(
  isA: EntityType.Value = EntityType.DocumentaryUnitDescription,
  id: Option[String] = None,
  languageCode: String,
  identifier: Option[String] = None,
  identity: IsadGIdentity,
  context: IsadGContext = IsadGContext(),
  content: IsadGContent = IsadGContent(),
  conditions: IsadGConditions = IsadGConditions(),
  materials: IsadGMaterials = IsadGMaterials(),
  notes: Seq[String] = Nil,
  control: IsadGControl = IsadGControl(),
  sourceFileId: Option[String] = None,
  creationProcess: CreationProcess.Value = CreationProcess.Manual,
  @models.relation(Ontology.HAS_ACCESS_POINT)
  accessPoints: Seq[AccessPointF] = Nil,
  @models.relation(Ontology.HAS_MAINTENANCE_EVENT)
  maintenanceEvents: Seq[MaintenanceEventF] = Nil,
  @models.relation(Ontology.HAS_UNKNOWN_PROPERTY)
  unknownProperties: Seq[Entity] = Nil
) extends ModelData with Persistable with Description with Temporal {

  def name: String = identity.name
  def dates: Seq[DatePeriodF] = identity.dates

  def displayText: Option[String] = identity.`abstract` orElse content.scopeAndContent

  def externalLink(item: DocumentaryUnit): Option[String] = identity.ref orElse {
    for {
      holder <- item.holder
      pattern <- holder.data.urlPattern
    } yield pattern.replaceAll("\\{identifier\\}", item.data.identifier)
  }
}

object DocumentaryUnitDescription {
  import Entity._
  import models.IsadG._
  import utils.EnumUtils.enumMapping

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.DocumentaryUnitDescription),
      ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      Entity.IDENTIFIER -> optional(nonEmptyText),
      IDENTITY_AREA -> mapping(
        TITLE -> nonEmptyText,
        PARALLEL_FORMS_OF_NAME -> seq(nonEmptyText),
        REF -> optional(text),
        ABSTRACT -> optional(nonEmptyText),
        DATES -> seq(DatePeriod.form.mapping),
        UNIT_DATES -> seq(nonEmptyText),
        LEVEL_OF_DESCRIPTION -> optional(text),
        PHYSICAL_LOCATION -> seq(nonEmptyText),
        EXTENT_MEDIUM -> optional(nonEmptyText)
      )(IsadGIdentity.apply)(IsadGIdentity.unapply),
      CONTEXT_AREA -> mapping(
        ADMIN_BIOG -> optional(text),
        ARCH_HIST -> optional(text),
        ACQUISITION -> optional(text)
      )(IsadGContext.apply)(IsadGContext.unapply),
      CONTENT_AREA -> mapping(
        SCOPE_CONTENT -> optional(text),
        APPRAISAL -> optional(text),
        ACCRUALS -> optional(text),
        SYS_ARR -> optional(text)
      )(IsadGContent.apply)(IsadGContent.unapply),
      CONDITIONS_AREA -> mapping(
        ACCESS_COND -> optional(text),
        REPROD_COND -> optional(text),
        LANG_MATERIALS -> seq(nonEmptyText),
        SCRIPT_MATERIALS -> seq(nonEmptyText),
        PHYSICAL_CHARS -> optional(text),
        FINDING_AIDS -> seq(nonEmptyText)
      )(IsadGConditions.apply)(IsadGConditions.unapply),
      MATERIALS_AREA -> mapping(
        LOCATION_ORIGINALS -> seq(nonEmptyText),
        LOCATION_COPIES -> seq(nonEmptyText),
        RELATED_UNITS -> seq(nonEmptyText),
        SEPARATED_UNITS -> seq(nonEmptyText),
        PUBLICATION_NOTE -> optional(text)
      )(IsadGMaterials.apply)(IsadGMaterials.unapply),
      NOTES -> seq(nonEmptyText),
      CONTROL_AREA -> mapping(
        ARCHIVIST_NOTE -> optional(text),
        SOURCES -> seq(nonEmptyText),
        RULES_CONVENTIONS -> optional(text),
        DATES_DESCRIPTIONS -> optional(text),
        PROCESS_INFO -> seq(nonEmptyText)
      )(IsadGControl.apply)(IsadGControl.unapply),
      SOURCE_FILE_ID -> optional(nonEmptyText),
      CREATION_PROCESS -> default(enumMapping(CreationProcess), CreationProcess.Manual),
      ACCESS_POINTS -> seq(AccessPoint.form.mapping),
      MAINTENANCE_EVENTS -> seq(MaintenanceEventF.form.mapping),
      UNKNOWN_DATA -> seq(entityForm)
    )(DocumentaryUnitDescriptionF.apply)(DocumentaryUnitDescriptionF.unapply)
  )
}
