package controllers.generic

import config.AppConfig
import controllers.base.CoreActionBuilders
import play.api.mvc.Request
import services.data.Constants


/**
  * Base trait for controllers that deal with the dataApi.
  */
trait Write extends CoreActionBuilders {

  protected def conf: AppConfig

  /**
    * Extract a log message from an incoming request params
    */
  def getLogMessage(implicit request: Request[_]): Option[String] = {
    import play.api.data.Form
    import play.api.data.Forms._
    Form(single(Constants.LOG_MESSAGE_PARAM -> optional(nonEmptyText(maxLength = conf.logMessageMaxLength))))
      .bindFromRequest().value.flatten
  }
}
