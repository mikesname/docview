package auth.handler

import auth.handler.cookie.CookieAuthHandler
import com.google.inject.ImplementedBy
import models.Account
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration


/**
  * Authentication handler.
  *
  * Derived in large part from play2-auth:
  *
  * https://github.com/t2v/play2-auth.git
  *
  * Modified for Play 2.5+.
  */
@ImplementedBy(classOf[CookieAuthHandler])
trait AuthHandler {

  protected def tokenAccessor: TokenAccessor

  protected def idContainer: AuthIdContainer

  def sessionTimeout: Duration

  type ResultUpdater = Result => Result

  def restoreAccount(implicit request: RequestHeader): Future[(Option[Account], ResultUpdater)]

  def newSession(userId: String): Future[String] = idContainer.startNewSession(userId, sessionTimeout)

  def login(userId: String, result: => Future[Result])(implicit request: RequestHeader, executionContext: ExecutionContext): Future[Result] = for {
    token <- idContainer.startNewSession(userId, sessionTimeout)
    r <- result
  } yield tokenAccessor.put(token)(r)

  def logout(result: => Future[Result])(implicit request: RequestHeader, executionContext: ExecutionContext): Future[Result] = {
    tokenAccessor.extract(request).foreach(s => idContainer.remove(s))
    result.map(tokenAccessor.delete)
  }
}
