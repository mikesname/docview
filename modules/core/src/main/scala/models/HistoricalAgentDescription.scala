package models

import models.json._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import Description._


case class IsaarDetail(
  datesOfExistence: Option[String] = None,
  history: Option[String] = None,
  places: Seq[String] = Nil,
  legalStatus: Seq[String] = Nil,
  functions: Seq[String] = Nil,
  mandates: Seq[String] = Nil,
  internalStructure: Option[String] = None,
  generalContext: Option[String] = None
) extends AttributeSet

case class IsaarControl(
  descriptionIdentifier: Option[String] = None,
  institutionIdentifier: Option[String] = None,
  rulesAndConventions: Option[String] = None,
  levelOfDetail: Option[String] = None,
  datesCDR: Option[String] = None,
  sources: Seq[String] = Nil,
  maintenanceNotes: Option[String] = None
) extends AttributeSet



object HistoricalAgentDescriptionF {

  import Entity._
  import HistoricalAgentF._
  import Isaar._
  import eu.ehri.project.definitions.Ontology._

  implicit val historicalAgentDescriptionFormat: Format[HistoricalAgentDescriptionF] = (
    (__ \ TYPE).formatIfEquals(EntityType.HistoricalAgentDescription) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ LANG_CODE).format[String] and
    (__ \ DATA \ ENTITY_TYPE).formatWithDefault(HistoricalAgentType.CorporateBody) and
    (__ \ DATA \ AUTHORIZED_FORM_OF_NAME).formatWithDefault(UNNAMED_PLACEHOLDER) and
    (__ \ DATA \ OTHER_FORMS_OF_NAME).formatSeqOrSingle[String] and
    (__ \ DATA \ PARALLEL_FORMS_OF_NAME).formatSeqOrSingle[String] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).formatSeqOrEmpty[DatePeriodF] and
    (__ \ DATA).format[IsaarDetail]((
      (__ \ DATES_OF_EXISTENCE).formatNullable[String] and
      (__ \ HISTORY).formatNullable[String] and
      (__ \ PLACES).formatSeqOrSingle[String] and
      (__ \ LEGAL_STATUS).formatSeqOrSingle[String] and
      (__ \ FUNCTIONS).formatSeqOrSingle[String] and
      (__ \ MANDATES).formatSeqOrSingle[String] and
      (__ \ INTERNAL_STRUCTURE).formatNullable[String] and
      (__ \ GENERAL_CONTEXT).formatNullable[String]
    )(IsaarDetail.apply, unlift(IsaarDetail.unapply))) and
    (__ \ DATA).format[IsaarControl]((
      (__ \ DESCRIPTION_IDENTIFIER).formatNullable[String] and
      (__ \ INSTITUTION_IDENTIFIER).formatNullable[String] and
      (__ \ RULES_CONVENTIONS).formatNullable[String] and
      (__ \ LEVEL_OF_DETAIL).formatNullable[String] and
      (__ \ DATES_CVD).formatNullable[String] and
      (__ \ SOURCES).formatSeqOrSingle[String] and
      (__ \ MAINTENANCE_NOTES).formatNullable[String]
    )(IsaarControl.apply, unlift(IsaarControl.unapply))) and
    (__ \ DATA \ CREATION_PROCESS).formatWithDefault(CreationProcess.Manual) and
    (__ \ RELATIONSHIPS \ HAS_ACCESS_POINT).formatSeqOrEmpty[AccessPointF] and
    (__ \ RELATIONSHIPS \ HAS_MAINTENANCE_EVENT).formatSeqOrEmpty[MaintenanceEventF] and
    (__ \ RELATIONSHIPS \ HAS_UNKNOWN_PROPERTY).formatSeqOrEmpty[Entity]
  )(HistoricalAgentDescriptionF.apply, unlift(HistoricalAgentDescriptionF.unapply))

  implicit object Converter extends Writable[HistoricalAgentDescriptionF]  {
    val restFormat: Format[HistoricalAgentDescriptionF] = historicalAgentDescriptionFormat
  }
}

case class HistoricalAgentDescriptionF(
  isA: EntityType.Value = EntityType.HistoricalAgentDescription,
  id: Option[String],
  languageCode: String,
  entityType: Isaar.HistoricalAgentType.Value,
  name: String,
  otherFormsOfName: Seq[String] = Nil,
  parallelFormsOfName: Seq[String] = Nil,
  @models.relation(Ontology.ENTITY_HAS_DATE)
  dates: Seq[DatePeriodF] = Nil,
  details: IsaarDetail,
  control: IsaarControl,
  creationProcess: CreationProcess.Value = CreationProcess.Manual,
  @models.relation(Ontology.HAS_ACCESS_POINT)
  accessPoints: Seq[AccessPointF] = Nil,
  @models.relation(Ontology.HAS_MAINTENANCE_EVENT)
  maintenanceEvents: Seq[MaintenanceEventF] = Nil,
  @models.relation(Ontology.HAS_UNKNOWN_PROPERTY)
  unknownProperties: Seq[Entity] = Nil
) extends ModelData
  with Persistable
  with Description
  with Temporal {

  override def displayText: Option[String] =
    details.history orElse details.generalContext orElse details.internalStructure
}

object HistoricalAgentDescription {
  import Isaar._
  import Entity._
  import utils.EnumUtils.enumMapping

  val form: Form[HistoricalAgentDescriptionF] = Form(
    mapping(
      ISA -> ignored(EntityType.HistoricalAgentDescription),
      ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      ENTITY_TYPE -> enumMapping(HistoricalAgentType),
      AUTHORIZED_FORM_OF_NAME -> nonEmptyText,
      OTHER_FORMS_OF_NAME -> seq(nonEmptyText),
      PARALLEL_FORMS_OF_NAME -> seq(nonEmptyText),
      DATES -> seq(DatePeriod.form.mapping),
      DESCRIPTION_AREA -> mapping(
        DATES_OF_EXISTENCE -> optional(text),
        HISTORY -> optional(text),
        PLACES -> seq(text),
        LEGAL_STATUS -> seq(text),
        FUNCTIONS -> seq(text),
        MANDATES -> seq(text),
        INTERNAL_STRUCTURE -> optional(text),
        GENERAL_CONTEXT -> optional(text)
      )(IsaarDetail.apply)(IsaarDetail.unapply),
      CONTROL_AREA -> mapping(
        DESCRIPTION_IDENTIFIER -> optional(text),
        INSTITUTION_IDENTIFIER -> optional(text),
        RULES_CONVENTIONS -> optional(text),
        LEVEL_OF_DETAIL -> optional(text),
        DATES_CVD -> optional(text),
        SOURCES -> seq(nonEmptyText),
        MAINTENANCE_NOTES -> optional(text)
      )(IsaarControl.apply)(IsaarControl.unapply),
      CREATION_PROCESS -> default(enumMapping(CreationProcess), CreationProcess.Manual),
      ACCESS_POINTS -> seq(AccessPoint.form.mapping),
      MAINTENANCE_EVENTS -> seq(MaintenanceEventF.form.mapping),
      UNKNOWN_DATA -> seq(forms.entityForm)
    )(HistoricalAgentDescriptionF.apply)(HistoricalAgentDescriptionF.unapply)
  )
}
