package auth

import org.apache.commons.codec.digest.DigestUtils
import org.mindrot.jbcrypt.BCrypt

/**
 * Wrapper around a plain password string.
 */
object HashedPassword {
  def fromPlain(plain: String) = new HashedPassword(BCrypt.hashpw(plain.toString, BCrypt.gensalt()))
  def fromHashed(hashed: String) = new HashedPassword(hashed)
}

case class HashedPassword private(s: String) {
  def check(pw: String): Boolean =  BCrypt.checkpw(pw, s)

  /**
   * Legacy passwords from the Drupal 6 system were stored as
   * unsalted MD5. These were BCrypted when imported so the
   * incoming password needs MD5ing prior to checking.
   */
  def checkLegacy(pw: String): Boolean =  BCrypt.checkpw(DigestUtils.md5Hex(pw), s)

  override def toString: String = s
}
