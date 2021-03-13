package models

import play.api.libs.json.{Format, Json}
import play.api.data.Form
import play.api.data.Forms._

/**
 * Subset of user data that is editable by admin users.
 */
case class AdminUserData(active: Boolean, staff: Boolean, verified: Boolean)

object AdminUserData {
  def fromUserProfile(user: UserProfile): AdminUserData = new AdminUserData(
    active = user.data.active,
    staff = user.account.exists(_.staff),
    verified = user.account.exists(_.verified)
  )

  implicit val format: Format[AdminUserData] = Json.format[AdminUserData]

  val form = Form(
    mapping(
      UserProfileF.ACTIVE -> boolean,
      "staff" -> boolean,
      "verified" -> boolean
    )(AdminUserData.apply)(AdminUserData.unapply)
  )
}

