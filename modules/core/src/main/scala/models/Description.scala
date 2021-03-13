package models

import play.api.libs.json.Format

trait Description extends ModelData {
  def name: String

  def languageCode: String

  def languageCode2: String = i18n.lang3to2lookup.getOrElse(languageCode, languageCode)

  def accessPoints: Seq[AccessPointF]

  def maintenanceEvents: Seq[MaintenanceEventF]

  def creationProcess: Description.CreationProcess.Value

  def unknownProperties: Seq[Entity]

  def displayText: Option[String]

  def isRightToLeft: Boolean = languageCode == "heb" || languageCode == "ara"

  def localId: Option[String] = id.flatMap(Description.localId)
}

object Description {

  val SOURCE_FILE_ID = "sourceFileId"
  val LANG_CODE = "languageCode"
  val CREATION_PROCESS = "creationProcess"
  val ACCESS_POINTS = "accessPoints"
  val UNKNOWN_DATA = "unknownData"
  val MAINTENANCE_EVENTS = "maintenanceEvents"

  val DESCRIPTION_DELIMITER = "."

  object CreationProcess extends Enumeration {
    type Type = Value
    val Import: Type = Value("IMPORT")
    val Manual: Type = Value("MANUAL")

    implicit val format: Format[CreationProcess.Value] = utils.EnumUtils.enumFormat(this)
  }

  /**
    * Somewhat gnarly function to get the first value from
    * a set of descriptions that is available, along with an
    * indication of it
    * @param prim The primary description
    * @param descriptions A set of 'alternate' descriptions
    * @param f A function to extract an optional string from a description
    * @tparam T The description type
    * @return A tuple of the value and whether it was found in the primary description
    */
  def firstValue[T](prim: T, descriptions: Seq[T], f: T => Option[String]): (Option[String], Boolean) = {
    if (f(prim).isDefined) (f(prim), true)
    else descriptions.find(d => f(d).isDefined).map { backup =>
      (f(backup), false)
    }.getOrElse((None, false))
  }

  /**
    * Helper for iterating over each description with a list of the other
    * descriptions that are also available.
    * @param descriptions The full list of descriptions
    * @tparam T A description type
    * @return A sequence of each element and its alterates
    */
  def iterateWithAlternates[T](descriptions: Seq[T]): Iterable[(T, Seq[T])] = for {
    i <- descriptions.indices
    elem = descriptions(i)
    before = descriptions.take(i)
    after = descriptions.drop(i + 1)
  } yield (elem, before ++ after)

  /**
    * The 'local' ID part of an individual description is the
    * section after a period.
    *
    * @param id the full id, including that described item
    * @return the local part of the id, unique to a description
    */
  def localId(id: String): Option[String] =
    if (id.contains(DESCRIPTION_DELIMITER)) Some(id.substring(id.indexOf(DESCRIPTION_DELIMITER) + 1))
    else None
}

