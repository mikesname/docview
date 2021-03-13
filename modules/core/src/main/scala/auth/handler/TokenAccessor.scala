package auth.handler

import com.google.inject.ImplementedBy
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{RequestHeader, Result}

/**
  * Authentication token accessor.
  *
  * Derived in large part from play2-auth:
  *
  * https://github.com/t2v/play2-auth.git
  *
  * Modified for Play 2.5+.
  */
@ImplementedBy(classOf[auth.handler.cookie.CookieTokenAccessor])
trait TokenAccessor {

  def signer: CookieSigner

  def extract(request: RequestHeader): Option[String]

  def put(token: String)(result: Result)(implicit request: RequestHeader): Result

  def delete(result: Result)(implicit request: RequestHeader): Result

  protected def verifyHmac(token: String): Option[String] = {
    val (hmac, value) = token.splitAt(40)
    if (safeEquals(signer.sign(value), hmac)) Some(value) else None
  }

  protected def sign(token: String): String = signer.sign(token) + token

  // Do not change this unless you understand the security issues behind timing attacks.
  // This method intentionally runs in constant time if the two strings have the same length.
  // If it didn't, it would be vulnerable to a timing attack.
  protected def safeEquals(a: String, b: String): Boolean = {
    if (a.length != b.length) {
      false
    } else {
      var equal = 0
      for (i <- Array.range(0, a.length)) {
        equal |= a(i) ^ b(i)
      }
      equal == 0
    }
  }
}
