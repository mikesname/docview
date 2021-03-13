package models

import models.json._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject


object CountryF {

  val ABSTRACT = "abstract"
  val HISTORY = "report" // FIXME: Rename to "history"
  val SITUATION = "situation"
  val DATA_SUMMARY = "dataSummary"
  val DATA_EXTENSIVE = "dataExtensive"

  import Entity._

  lazy implicit val countryFormat: Format[CountryF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Country) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ ABSTRACT).formatNullable[String] and
    (__ \ DATA \ HISTORY).formatNullable[String] and
    (__ \ DATA \ SITUATION).formatNullable[String] and
    (__ \ DATA \ DATA_SUMMARY).formatNullable[String] and
    (__ \ DATA \ DATA_EXTENSIVE).formatNullable[String]
  )(CountryF.apply, unlift(CountryF.unapply))

  implicit object Converter extends Writable[CountryF] {
    lazy val restFormat: Format[CountryF] = countryFormat
  }
}

case class CountryF(
  isA:EntityType.Value = EntityType.Country,
  id: Option[String],
  identifier: String,
  abs: Option[String],
  history: Option[String],
  situation: Option[String],                   
  summary: Option[String],
  extensive: Option[String]                   
) extends ModelData with Persistable {

  def displayText: Option[String] = abs orElse situation
}


object Country {
  import eu.ehri.project.definitions.Ontology._
  import Entity._
  import CountryF._

  implicit val metaReads: Reads[Country] = (
    __.read[CountryF](countryFormat) and
    // Latest event
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Country.apply _)

  implicit object CountryResource extends ContentType[Country]  {
    val entityType = EntityType.Country
    val contentType = ContentTypes.Country
    val restReads: Reads[Country] = metaReads
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Country),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=2,maxLength=2), // ISO 2-letter field
      ABSTRACT -> optional(nonEmptyText),
      HISTORY -> optional(nonEmptyText),
      SITUATION -> optional(nonEmptyText),
      DATA_SUMMARY -> optional(nonEmptyText),
      DATA_EXTENSIVE -> optional(nonEmptyText)
    )(CountryF.apply)(CountryF.unapply)
  )
}


// Stub
case class Country(
  data: CountryF,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends Model
  with Accessible
  with Holder[Repository] {

  type T = CountryF

  override def toStringLang(implicit messages: Messages): String = i18n.countryCodeToName(id)
}
