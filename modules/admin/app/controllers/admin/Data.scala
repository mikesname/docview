package controllers.admin

import auth.AccountManager
import controllers.base.AdminController
import models.base.AnyModel
import play.api.cache.CacheApi
import play.api.http.{ContentTypes, HeaderNames}
import play.api.i18n.MessagesApi
import play.api.libs.ws.WSClient
import javax.inject._

import backend.{DataApi, Readable}
import utils.MovedPageLookup
import views.MarkdownRenderer

import scala.concurrent.ExecutionContext


case class Data @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  ws: WSClient,
  executionContext: ExecutionContext
) extends AdminController {

  implicit val rd: Readable[AnyModel] = AnyModel.Converter

  private def passThroughHeaders(headers: Map[String, Seq[String]],
                                 filter: Seq[String] = Seq.empty): Seq[(String, String)] = {
    headers.filter(kv => if (filter.isEmpty) true else filter.contains(kv._1)).flatMap { case (k, seq) =>
      seq.map(s => k -> s)
    }.toSeq
  }

  def getItem(id: String) = OptionalUserAction.async { implicit request =>
    implicit val rd: Readable[AnyModel] = AnyModel.Converter
    userDataApi.fetch(List(id)).map {
      case Nil => NotFound(views.html.errors.itemNotFound())
      case mm :: _ => views.admin.Helpers.linkToOpt(mm)
        .map(Redirect) getOrElse NotFound(views.html.errors.itemNotFound())
    }
  }

  def getItemType(entityType: defines.EntityType.Value, id: String) = OptionalUserAction { implicit request =>
    views.admin.Helpers.linkToOpt(entityType, id)
      .map(Redirect)
      .getOrElse(NotFound(views.html.errors.itemNotFound()))
  }

  def getItemRawJson(entityType: defines.EntityType.Value, id: String) = OptionalUserAction.async { implicit request =>
    userDataApi.query(s"classes/$entityType/$id").map { r =>
      Ok(r.json)
    }
  }

  def forward(urlPart: String) = OptionalUserAction.async { implicit request =>
    val url = urlPart + (if(request.rawQueryString.trim.isEmpty) "" else "?" + request.rawQueryString)
    userDataApi.stream(url).map { sr =>
      val result:Status = Status(sr.headers.status)
      val rHeaders = passThroughHeaders(sr.headers.headers)
      val ct = sr.headers.headers.get(HeaderNames.CONTENT_TYPE)
        .flatMap(_.headOption).getOrElse(ContentTypes.JSON)
      result.chunked(sr.body).as(ct).withHeaders(rHeaders: _*)
    }
  }
}