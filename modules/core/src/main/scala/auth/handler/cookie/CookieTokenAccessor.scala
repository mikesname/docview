package auth.handler.cookie

import javax.inject.Inject
import auth.handler.TokenAccessor
import config.AppConfig
import play.api.Configuration
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Cookie, DiscardingCookie, RequestHeader, Result}

/**
  * Authentication token accessor.
  *
  * Derived in large part from play2-auth:
  *
  * https://github.com/t2v/play2-auth.git
  *
  * Modified for Play 2.5+.
  */
case class CookieTokenAccessor @Inject()()(val signer: CookieSigner,
                                           config: Configuration, conf: AppConfig) extends TokenAccessor {

  val cookieName: String = config.getOptional[String]("auth.session.cookieName").getOrElse("PLAY2AUTH_SESS_ID")
  val cookieSecureOption: Boolean = conf.https
  val cookieHttpOnlyOption: Boolean = true
  val cookieDomainOption: Option[String] = None
  val cookiePathOption: String = "/"
  val cookieMaxAge: Option[Int] = None

  override def put(token: String)(result: Result)(implicit request: RequestHeader): Result = {
    val c = Cookie(cookieName, sign(token), cookieMaxAge, cookiePathOption,
      cookieDomainOption, cookieSecureOption, cookieHttpOnlyOption)
    result.withCookies(c)
  }

  override def extract(request: RequestHeader): Option[String] = {
    request.cookies.get(cookieName).flatMap(c => verifyHmac(c.value))
  }

  override def delete(result: Result)(implicit request: RequestHeader): Result = {
    result.discardingCookies(DiscardingCookie(cookieName))
  }
}
