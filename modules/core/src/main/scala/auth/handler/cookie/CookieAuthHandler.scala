package auth.handler.cookie

import auth.handler.{AuthHandler, AuthIdContainer, TokenAccessor}
import javax.inject.Inject
import models.Account
import play.api.mvc.RequestHeader
import play.api.{Configuration, Environment, Mode}
import services.accounts.AccountManager

import scala.concurrent.{ExecutionContext, Future}

/**
  * Authentication handler.
  *
  * Derived in large part from play2-auth:
  *
  * https://github.com/t2v/play2-auth.git
  *
  * Modified for Play 2.5+.
  */
case class CookieAuthHandler @Inject()(
  env: Environment,
  idContainer: AuthIdContainer,
  tokenAccessor: TokenAccessor,
  accountManager: AccountManager,
  config: Configuration,
  executionContext: ExecutionContext
) extends AuthHandler {

  implicit val ec: ExecutionContext = executionContext

  import scala.concurrent.duration._

  override def sessionTimeout: FiniteDuration =
    config.get[FiniteDuration]("auth.session.timeout")

  override def restoreAccount(implicit request: RequestHeader): Future[(Option[Account], ResultUpdater)] = {
    (for {
      token <- extractToken(request)
    } yield for {
      Some(userId) <- idContainer.get(token)
      userOpt <- accountManager.findById(userId)
      _ <- idContainer.prolongTimeout(token, sessionTimeout)
    } yield userOpt -> tokenAccessor.put(token) _
      ) getOrElse {
      Future.successful(Option.empty -> identity)
    }
  }

  private[auth] def extractToken(request: RequestHeader): Option[String] = {
    if (env.mode == Mode.Test) {
      request.headers.get("PLAY2_AUTH_TEST_TOKEN") orElse tokenAccessor.extract(request)
    } else {
      tokenAccessor.extract(request)
    }
  }
}
