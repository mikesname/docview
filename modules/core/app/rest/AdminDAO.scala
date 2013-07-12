package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile


case class AdminDAO(userProfile: Option[UserProfile]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/admin".format(host, port, mount)

  def createNewUserProfile: Future[Either[RestError, UserProfile]] = {
    WS.url(enc(requestUrl, "createDefaultUserProfile")).withHeaders(headers.toSeq: _*).post("").map { response =>
        checkErrorAndParse(response)(UserProfile.Converter.restReads)
      }
  }
}