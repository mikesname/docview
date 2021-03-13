package auth.handler.cache

import java.security.SecureRandom
import javax.inject.Inject

import auth.handler.AuthIdContainer
import play.api.cache.SyncCacheApi

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration._
import scala.util.Random

/**
  * Authentication id container.
  *
  * Derived in large part from play2-auth:
  *
  * https://github.com/t2v/play2-auth.git
  *
  * Modified for Play 2.5+.
  */
class CacheIdContainer @Inject()(cacheApi: SyncCacheApi) extends AuthIdContainer {

  private val tokenSuffix = ":token"
  private val userIdSuffix = ":userId"
  private final val table = "abcdefghijklmnopqrstuvwxyz1234567890_.~*'()"
  private val random = new Random(new SecureRandom())

  override def startNewSession(userId: String, timeout: Duration): Future[String] = immediate {
    removeByUserId(userId)
    val token = generate
    store(token, userId, timeout)
    token
  }

  override def remove(token: String): Future[Unit] = immediate {
    lookup(token).foreach(unsetUserId)
    unsetToken(token)
  }

  override def get(token: String): Future[Option[String]] = immediate(lookup(token))

  override def prolongTimeout(token: String, timeout: Duration): Future[Unit] = immediate {
    lookup(token).foreach(t => store(token, t, timeout))
  }


  @tailrec
  private final def generate: String = {
    val token = Iterator.continually(random.nextInt(table.length)).map(table).take(64).mkString
    if (lookup(token).isDefined) generate else token
  }

  private def removeByUserId(userId: String): Unit = {
    cacheApi.get[String](userId + userIdSuffix).foreach(unsetToken)
    unsetUserId(userId)
  }

  private def unsetToken(token: String): Unit = cacheApi.remove(token + tokenSuffix)

  private def unsetUserId(userId: String): Unit = cacheApi.remove(userId + userIdSuffix)

  private def lookup(token: String): Option[String] = cacheApi.get[String](token + tokenSuffix)

  private[auth] def store(token: String, userId: String, timeout: Duration): Unit = {
    cacheApi.set(token + tokenSuffix, userId, timeout)
    cacheApi.set(userId + userIdSuffix, token, timeout)
  }
}
