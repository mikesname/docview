package models

import play.api.i18n.Messages


trait Described extends ModelData {

  type D <: Description

  def descriptions: Seq[D]

  /**
    * Get a description by ID
    * @param id The description ID
    * @return A description matching that ID, optionally empty
    */
  def description(id: String): Option[D] = descriptions.find(_.id.contains(id))

  /**
    * Get a description with an optional ID, falling back on the first
    * appropriate one for the given (implicit) language code.
    * @param id The (optional) description ID
    * @param messages The current language
    * @return A description matching that ID, or the first found with that language.
    */
  def primaryDescription(id: Option[String])(implicit messages: Messages): Option[D] =
    id.fold(primaryDescription(messages))(s => primaryDescription(s))

  /**
    * Get the first description for the current language
    * @param messages The current language
    * @return The first description found with a matching language code
    */
  def primaryDescription(implicit messages: Messages): Option[D] = {
    val code3 = i18n.lang2to3lookup.getOrElse(messages.lang.language, messages.lang.language)
    descriptions.find(_.languageCode == code3).orElse(descriptions.headOption)
  }

  /**
    * List descriptions with the current language first, if possible.
    *
    * @param messages the current i18n messages
    * @return a list of descriptions
    */
  def currentLangFirstDescriptions(implicit messages: Messages): Seq[D] = {
    val code3 = i18n.lang2to3lookup.getOrElse(messages.lang.language, messages.lang.language)
    val (matchLang, others) = descriptions.partition(_.languageCode == code3)
    matchLang ++ others
  }

  /**
    * List descriptions ordered by their local identifier.
    *
    * @return a list of descriptions
    */
  def orderedDescriptions: Seq[D] = descriptions.sortBy(_.localId)

  /**
    * List ordered descriptions with a boolean indicated that
    * one matches the given local ID. The selected description
    * is the first if none match the ID.
    *
    * @param localId the selected local ID
    * @return a
    */
  def descriptionsWithSelected(localId: Option[String] = None)(implicit messages: Messages): Seq[(D, Boolean)] = {
    val descs = orderedDescriptions
    // try and find a matching local ID, if we're given one
    val didx = descs.indexWhere(d => d.localId == localId)
    val idx = if (localId.isDefined && didx > -1) didx else {
      // Try and find the first description with the current language
      val cidx = descs.indexWhere(d => d.languageCode2 == messages.lang.code)
      // Or fall back to the first description
      if (cidx > -1) cidx else 0
    }

    descs.zipWithIndex.map { case (desc, i) => desc -> (i == idx) }
  }

  /**
    * Get a description with the given ID, falling back on the first
    * appropriate one for the given (implicit) language code.
    *
    * @param id The description ID
    * @param messages The current language
    * @return A description matching that ID, or the first found with that language.
    */
  def primaryDescription(id: String)(implicit messages: Messages): Option[D] =
    description(id).orElse(primaryDescription(messages))

  def accessPoints: Seq[AccessPointF] =
    descriptions.flatMap(_.accessPoints)

  def maintenanceEvents: Seq[MaintenanceEventF] =
    descriptions.flatMap(_.maintenanceEvents).distinct.sortBy(_.date)
}

