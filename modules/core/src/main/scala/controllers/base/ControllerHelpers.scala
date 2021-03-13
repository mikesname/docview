package controllers.base

import play.api.mvc._


trait ControllerHelpers extends play.api.i18n.I18nSupport {


  protected implicit def config: play.api.Configuration

  /**
    * Session key for last page prior to login
    */
  protected val ACCESS_URI: String = "access_uri"

  /**
    * Check if a request is Ajax.
    */
  protected def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH").exists(_.toUpperCase == "XMLHTTPREQUEST")

}
