package models

import defines.{PermissionType,ContentType}
import acl._
import models.base._

import base.Persistable
import defines.EntityType
import play.api.libs.json.{Format, Json}
import defines.EnumUtils.enumWrites
import models.json.{ClientConvertable, RestConvertable}


object UserProfileF {

  val FIELD_PREFIX = "profile"

  final val PLACEHOLDER_TITLE = "[No Title Found]"

  val NAME = "name"
  val LOCATION = "location"
  val ABOUT = "about"
  val LANGUAGES = "languages"

  lazy implicit val userProfileFormat: Format[UserProfileF] = json.UserProfileFormat.restFormat

  implicit object Converter extends RestConvertable[UserProfileF] with ClientConvertable[UserProfileF] {
    lazy val restFormat = models.json.rest.userProfileFormat
    lazy val clientFormat = models.json.client.userProfileFormat
  }
}

case class UserProfileF(
  isA: EntityType.Value = EntityType.UserProfile,
  id: Option[String],
  identifier: String,
  name: String,
  location: Option[String] = None,
  about: Option[String] = None,
  languages: Option[List[String]] = None
) extends Persistable


object UserProfile {
  // Have to provide a single arg constructor
  // to provide a builder function for the generic views.
  def apply(e: Entity) = new UserProfile(e)
}

case class UserProfile(
  val e: Entity,
  val account: Option[sql.User] = None,
  val globalPermissions: Option[GlobalPermissionSet[UserProfile]] = None,
  val itemPermissions: Option[ItemPermissionSet[UserProfile]] = None) extends AccessibleEntity
  with Accessor with NamedEntity with Formable[UserProfileF] {

  def hasPermission(ct: ContentType.Value, p: PermissionType.Value): Boolean = {
    globalPermissions.map { gp =>
      if (gp.has(ct, p)) true
      else {
        itemPermissions.map { ip =>
          ip.contentType == ct && ip.has(p)
        }.getOrElse(false)
      }
    }.getOrElse(false)
  }

  lazy val formable: UserProfileF = Json.toJson(e).as[UserProfileF]
  lazy val formableOpt: Option[UserProfileF] = Json.toJson(e).asOpt[UserProfileF]
}
