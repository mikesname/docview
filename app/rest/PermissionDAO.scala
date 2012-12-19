package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.Entity
import models.UserProfile
import models.base.AccessibleEntity
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.{ PermissionSet, ItemPermissionSet, Group }
import models.base.Accessor
import com.codahale.jerkson.Json
import defines._
import models.UserProfileRepr
import play.api.mvc.Response
import play.api.http.HeaderNames
import play.api.http.ContentTypes

object PermissionDAO

case class PermissionDAO[T <: Accessor](val accessor: UserProfileRepr) extends RestDAO {

  import play.api.http.Status._

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/permission".format(baseUrl)

  private val authHeaders: Map[String, String] = headers + (
    AUTH_HEADER_NAME -> accessor.id
  )

  def get: Future[Either[RestError, PermissionSet[UserProfileRepr]]] = {
    WS.url(enc(requestUrl)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => PermissionSet(accessor, r.json))
    }
  }

  def get(user: T): Future[Either[RestError, PermissionSet[T]]] = {
    WS.url(enc("%s/%s".format(requestUrl, user.id)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => PermissionSet[T](user, r.json))
      }
  }

  def getItem(id: String): Future[Either[RestError, ItemPermissionSet[UserProfileRepr]]] = {
    WS.url(enc("%s/%s/%s".format(requestUrl, accessor.id, id)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => ItemPermissionSet(accessor, r.json))
      }
  }

  def getItem(user: T, id: String): Future[Either[RestError, ItemPermissionSet[T]]] = {
    WS.url(enc("%s/%s/%s".format(requestUrl, user.id, id)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => ItemPermissionSet[T](user, r.json))
      }
  }

  def set(user: T, data: Map[String, List[String]]): Future[Either[RestError, PermissionSet[T]]] = {
    WS.url(enc("%s/%s".format(requestUrl, user.id)))
      .withHeaders(authHeaders.toSeq: _*).post(Json.generate(data)).map { response =>
        checkError(response).right.map(r => PermissionSet[T](user, r.json))
      }
  }

  def addGroup(groupId: String, userId: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc("%s/%s/%s/%s".format(baseUrl,
      EntityType.Group, groupId, userId)))
      .withHeaders(authHeaders.toSeq: _*).post(Map[String, List[String]]()).map { response =>
        checkError(response).right.map(r => r.status == OK)
      }
  }

  def removeGroup(groupId: String, userId: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc("%s/%s/%s/%s".format(baseUrl,
      EntityType.Group, groupId, userId)))
      .withHeaders(authHeaders.toSeq: _*).delete.map { response =>
        checkError(response).right.map(r => r.status == OK)
      }
  }
}