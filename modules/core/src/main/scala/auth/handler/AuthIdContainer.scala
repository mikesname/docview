package auth.handler

import scala.concurrent.Future
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
trait AuthIdContainer {

  def startNewSession(userId: String, timeout: Duration): Future[String]

  def remove(token: String): Future[Unit]

  def get(token: String): Future[Option[String]]

  def prolongTimeout(token: String, timeout: Duration): Future[Unit]
}
