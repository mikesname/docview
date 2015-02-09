package controllers.admin

import auth.AccountManager
import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.AdminController
import play.api.mvc.Controller

import com.google.inject._
import backend.{BackendReadable, Backend}
import defines.EntityType
import models.base.AnyModel
import backend.rest.SearchDAO


case class Admin @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup) extends AdminController {

  implicit val rd: BackendReadable[AnyModel] = AnyModel.Converter

   /**
   * Action for redirecting to any item page, given a raw id.
   */
  def get(id: String) = OptionalUserAction.async { implicit request =>
    implicit val rd: BackendReadable[AnyModel] = AnyModel.Converter
    SearchDAO.list(List(id)).map {
      case Nil => NotFound(views.html.errors.itemNotFound())
      case mm :: _ => views.admin.Helpers.linkToOpt(mm)
        .map(Redirect) getOrElse NotFound(views.html.errors.itemNotFound())
    }
  }

  /**
   * Action for redirecting to any item page, given a raw id.
   */
  def getType(`type`: String, id: String) = OptionalUserAction { implicit request =>
    views.admin.Helpers.linkToOpt(EntityType.withName(`type`), id)
      .map(Redirect)
      .getOrElse(NotFound(views.html.errors.itemNotFound()))
  }
}