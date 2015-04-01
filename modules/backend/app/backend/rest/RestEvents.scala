package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import utils._
import backend.{BackendReadable, Events, ApiUser}
import defines.EntityType


/**
 * Data Access Object for Action-related requests.
 */
trait RestEvents extends Events with RestDAO {

  implicit def apiUser: ApiUser
  implicit def executionContext: ExecutionContext

  private def requestUrl = s"$baseUrl/${EntityType.SystemEvent}"

  override def history[A](id: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty)(implicit rd: BackendReadable[A]): Future[RangePage[A]] = {
    val url: String = enc(requestUrl, "for", id)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(rd.restReads)
  }

  override def versions[A](id: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]] = {
    val url: String = enc(requestUrl, "versions", id)
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  override def listEvents[A](params: RangeParams, filters: SystemEventParams)(implicit rd: BackendReadable[A]): Future[RangePage[A]] = {
    val url = enc(requestUrl, "list")
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(rd.restReads)
  }

  override def listEventsByUser[A](userId: String, params: RangeParams, filters: SystemEventParams)(implicit rd: BackendReadable[A]): Future[RangePage[A]] = {
    val url: String = enc(requestUrl, "byUser", userId)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(rd.restReads)
  }

  override def listEventsForUser[A](userId: String, params: RangeParams, filters: SystemEventParams)(implicit rd: BackendReadable[A]): Future[RangePage[A]] = {
    val url: String = enc(requestUrl, "forUser", userId)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(rd.restReads)
  }

  override def subjectsForEvent[A](id: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]] = {
    val url: String = enc(requestUrl, id, "subjects")
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }
}
