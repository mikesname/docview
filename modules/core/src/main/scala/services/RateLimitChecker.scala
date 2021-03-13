package services

import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.mvc.Request

import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration

case class RateLimitChecker @Inject()(cache: SyncCacheApi) {
  private def logger = Logger(getClass)

  /**
    * Check a particular remote address doesn't exceed a rate limit for a
    * given action. Whenever this function is called the user's
    */
  def checkHits[A](limit: Int, duration: FiniteDuration)(implicit request: Request[A]): Boolean = {
    val ip = request.remoteAddress
    val key = request.path + ip
    val count = cache.get(key).getOrElse(0)
    logger.debug(s"Check rate limit: Limit $limit, timeout $duration, ip: $ip, key: $key, current: $count")
    if (count < limit) {
      cache.set(key, count + 1, duration)
      true
    } else {
      logger.warn(s"Rate limit refusal for IP $ip at ${request.path}")
      false
    }
  }
}
