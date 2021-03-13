package auth.handler.cookie

import auth.handler.AuthIdContainer

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration.Duration

/**
  * Authentication id container.
  *
  * Derived in large part from play2-auth:
  *
  * https://github.com/t2v/play2-auth.git
  *
  * Modified for Play 2.5+.
  */
class CookieIdContainer extends AuthIdContainer {

  override def startNewSession(userId: String, timeout: Duration): Future[String] = immediate(userId)

  override def remove(token: String): Future[Unit] = immediate(())

  override def get(token: String): Future[Option[String]] = immediate(Some(token))

  override def prolongTimeout(token: String, timeout: Duration): Future[Unit] = immediate{
    // Timeout unsupported
  }
}
