package models

import play.api.libs.json.Format
import utils.EnumUtils

case object IsadG {

  object LevelOfDescription extends Enumeration {
    type Type = Value
    val Fonds = Value("fonds")
    val Subfonds = Value("subfonds")
    val Collection = Value("collection")
    val Subcollection = Value("subcollection")
    val RecordGroup = Value("recordgrp")
    val SubRecordGroup =Value("subgrp")
    val Series = Value("series")
    val Subseries = Value("subseries")
    val File = Value("file")
    val Item = Value("item")
    val Other = Value("otherlevel")
    val Class = Value("class")

    implicit val _format: Format[IsadG.LevelOfDescription.Value] = EnumUtils.enumFormat(this)
  }

  /* ISAD(G)-based field set */
  val TITLE = "name"
  val PARALLEL_FORMS_OF_NAME = "parallelFormsOfName"
  val REF = "ref"
  val DATES = "dates"
  val UNIT_DATES = "unitDates"
  val LEVEL_OF_DESCRIPTION = "levelOfDescription"
  val PHYSICAL_LOCATION = "physicalLocation"
  val EXTENT_MEDIUM = "extentAndMedium"
  val PUB_STATUS = "publicationStatus"
  val ABSTRACT = "abstract"

  val IDENTITY_AREA = "identityArea"
  val DESCRIPTIONS_AREA = "descriptionsArea"
  val ADMINISTRATION_AREA = "administrationArea"

  val CONTEXT_AREA = "contextArea"
  val ADMIN_BIOG = "biographicalHistory"
  val ARCH_HIST = "archivalHistory"
  val ACQUISITION = "acquisition"

  val CONTENT_AREA = "contentArea"
  val SCOPE_CONTENT = "scopeAndContent"
  val APPRAISAL = "appraisal"
  val ACCRUALS = "accruals"
  val SYS_ARR = "systemOfArrangement"

  val CONDITIONS_AREA = "conditionsArea"
  val ACCESS_COND = "conditionsOfAccess"
  val REPROD_COND = "conditionsOfReproduction"
  val LANG_MATERIALS = "languageOfMaterial"
  val SCRIPT_MATERIALS = "scriptOfMaterial"
  val PHYSICAL_CHARS = "physicalCharacteristics"
  val FINDING_AIDS = "findingAids"

  val MATERIALS_AREA = "materialsArea"
  val LOCATION_ORIGINALS = "locationOfOriginals"
  val LOCATION_COPIES = "locationOfCopies"
  val RELATED_UNITS = "relatedUnitsOfDescription"
  val SEPARATED_UNITS = "separatedUnitsOfDescription"
  val PUBLICATION_NOTE = "publicationNote"

  val NOTES_AREA = "notesArea"
  val NOTES = "notes"

  val CONTROL_AREA = "controlArea"
  val ARCHIVIST_NOTE = "archivistNote"
  val RULES_CONVENTIONS = "rulesAndConventions"
  val DATES_DESCRIPTIONS = "datesOfDescriptions"
  val PROCESS_INFO ="processInfo"
  val SOURCES ="sources"

  val ACCESS_POINTS = "accessPoints"
}
