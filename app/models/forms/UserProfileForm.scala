package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{UserProfileF, Entity}
import defines.EntityType

/**
 * UserProfile model form.
 */
object UserProfileForm {

  import UserProfileF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.UserProfile),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> nonEmptyText,
      LOCATION -> optional(text),
      ABOUT -> optional(text),
      LANGUAGES -> optional(list(nonEmptyText))
    )(UserProfileF.apply)(UserProfileF.unapply)
  )
}
