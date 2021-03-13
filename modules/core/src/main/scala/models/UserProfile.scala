package models

import eu.ehri.project.definitions.Ontology
import models.json._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, _}


object UserProfileF {

  val FIELD_PREFIX = "profile"

  final val PLACEHOLDER_TITLE = "[No Title Found]"

  val NAME = "name"
  val LOCATION = "location"
  val ABOUT = "about"
  val LANGUAGES = "languages"
  val IMAGE_URL = "imageUrl"
  val ACTIVE = "active"
  val STAFF = "staff"
  val URL = "url"
  val WORK_URL = "workUrl"
  val FIRST_NAMES = "firstNames"
  val LAST_NAME = "lastName"
  val TITLE = "title"
  val INSTITUTION = "institution"
  val ROLE = "role"
  val INTERESTS = "interests"

  import Entity._

  implicit val userProfileFormat: Format[UserProfileF] = (
    (__ \ TYPE).formatIfEquals(EntityType.UserProfile) and
      (__ \ ID).formatNullable[String] and
      (__ \ DATA \ IDENTIFIER).format[String] and
      (__ \ DATA \ NAME).format[String] and
      (__ \ DATA \ LOCATION).formatNullable[String] and
      (__ \ DATA \ ABOUT).formatNullable[String] and
      (__ \ DATA \ LANGUAGES).formatSeqOrSingle[String] and
      (__ \ DATA \ IMAGE_URL).formatNullable[String] and
      (__ \ DATA \ URL).formatNullable[String] and
      (__ \ DATA \ WORK_URL).formatNullable[String] and
      (__ \ DATA \ FIRST_NAMES).formatNullable[String] and
      (__ \ DATA \ LAST_NAME).formatNullable[String] and
      (__ \ DATA \ TITLE).formatNullable[String] and
      (__ \ DATA \ INSTITUTION).formatNullable[String] and
      (__ \ DATA \ ROLE).formatNullable[String] and
      (__ \ DATA \ INTERESTS).formatNullable[String] and
      (__ \ DATA \ ACTIVE).formatWithDefault(true) and
      (__ \ DATA \ STAFF).formatWithDefault(false)
    )(UserProfileF.apply, unlift(UserProfileF.unapply))

  implicit object Converter extends Writable[UserProfileF] {
    lazy val restFormat: Format[UserProfileF] = userProfileFormat
  }
}

case class UserProfileF(
  isA: EntityType.Value = EntityType.UserProfile,
  id: Option[String],
  identifier: String,
  name: String,
  location: Option[String] = None,
  about: Option[String] = None,
  languages: Seq[String] = Nil,
  imageUrl: Option[String] = None,
  url: Option[String] = None,
  workUrl: Option[String] = None,
  firstNames: Option[String] = None,
  lastName: Option[String] = None,
  title: Option[String] = None,
  institution: Option[String] = None,
  role: Option[String] = None,
  interests: Option[String] = None,
  active: Boolean = true,
  staff: Boolean = false
) extends ModelData with Persistable


object UserProfile {
  import Entity._
  import Ontology._
  import UserProfileF._

  private implicit val groupReads = Group.GroupResource.restReads
  private implicit val systemEventReads = SystemEvent.SystemEventResource.restReads

  implicit val metaReads: Reads[UserProfile] = (
    __.read[UserProfileF] and
    (__ \ RELATIONSHIPS \ ACCESSOR_BELONGS_TO_GROUP).lazyReadSeqOrEmpty(groupReads) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(UserProfile.quickApply _)

  implicit object UserProfileResource extends ContentType[UserProfile]  {
    val entityType = EntityType.UserProfile
    val contentType = ContentTypes.UserProfile
    val restReads: Reads[UserProfile] = metaReads
  }

  // Constructor, sans account and perms
  def quickApply(
     model: UserProfileF,
     groups: Seq[Group] = Nil,
     accessors: Seq[Accessor] = Nil,
     latestEvent: Option[SystemEvent],
     meta: JsObject) = new UserProfile(model, groups, accessors, latestEvent, meta)

  def quickUnapply(up: UserProfile) = Some((up.data, up.groups, up.accessors, up.latestEvent, up.meta))

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.UserProfile),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> nonEmptyText,
      LOCATION -> optional(text),
      ABOUT -> optional(text),
      LANGUAGES -> seq(nonEmptyText),
      IMAGE_URL -> optional(nonEmptyText.verifying(s => forms.isValidUrl(s))),
      URL -> optional(nonEmptyText.verifying(s => forms.isValidUrl(s))),
      WORK_URL -> optional(nonEmptyText.verifying(s => forms.isValidUrl(s))),
      FIRST_NAMES -> optional(text),
      LAST_NAME -> optional(text),
      TITLE -> optional(text),
      INSTITUTION -> optional(text),
      ROLE -> optional(text),
      INTERESTS -> optional(text),
      ACTIVE -> boolean,
      STAFF -> boolean
    )(UserProfileF.apply)(UserProfileF.unapply)
  )
}


case class UserProfile(
  data: UserProfileF,
  groups: Seq[Group] = Nil,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq.empty),
  account: Option[Account] = None,
  globalPermissions: Option[GlobalPermissionSet] = None,
  itemPermissions: Option[ItemPermissionSet] = None
) extends Model
  with Accessor
  with Accessible {

  type T = UserProfileF

  override def toStringLang(implicit messages: Messages): String = data.name

  def hasPermission(ct: ContentTypes.Value, p: PermissionType.Value): Boolean = {
    globalPermissions.exists(gp =>
      if (gp.has(ct, p)) true
      else {
        itemPermissions.exists(ip => ip.contentType == ct && ip.has(p))
      })
  }

  def followerCount: Int = meta.fields.find(_._1 == "followers").flatMap(_._2.asOpt[Int]).getOrElse(0)
  def followingCount: Int = meta.fields.find(_._1 == "following").flatMap(_._2.asOpt[Int]).getOrElse(0)
}
