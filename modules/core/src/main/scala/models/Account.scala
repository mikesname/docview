package models

import java.time.ZonedDateTime
import auth.HashedPassword

case class Account(
  id: String,
  email: String,
  verified: Boolean = false,
  staff: Boolean = false,
  active: Boolean = true,
  allowMessaging: Boolean = true,
  created: Option[ZonedDateTime] = None,
  lastLogin: Option[ZonedDateTime] = None,
  password: Option[HashedPassword] = None,
  isLegacy: Boolean = false
) {
  def hasPassword: Boolean = password.isDefined
}