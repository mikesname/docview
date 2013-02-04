package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile

case class VisibilityDAO(userProfile: Option[UserProfile]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/access".format(host, port, mount)

  def set(id: String, data: List[String]): Future[Either[RestError, Boolean]] = {
    val params = "?" + data.map(p => "%s=%s".format(RestPageParams.ACCESSOR_PARAM, p)).mkString("&")
    WS.url(enc(requestUrl, id, params)).withHeaders(authHeaders.toSeq: _*).post("").map { response =>
        checkError(response).right.map(r => true)
      }
  }
}