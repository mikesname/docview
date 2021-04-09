package controllers.portal

import javax.inject._
import services.cypher.CypherService
import controllers.AppComponents
import controllers.portal.base.PortalController
import forms.{HoneyPotForm, TimeCheckForm}
import play.api.{Application, Logger}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc._
import services.feedback.FeedbackService
import utils.PageParams

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Feedback @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  app: Application,
  feedbackService: FeedbackService,
  mailer: MailerClient,
  cypher: CypherService
) extends PortalController {

  val logger = Logger(classOf[Feedback])

  import play.api.data.Form
  import play.api.data.Forms._
  import forms.HoneyPotForm._
  import forms.TimeCheckForm._

  case class CheckFeedbackData(
    timestamp: String,
    blankCheck: String
  ) extends HoneyPotForm with TimeCheckForm

  private def checkFbForm = Form(
    mapping(
      TIMESTAMP -> nonEmptyText,
      BLANK_CHECK -> text.verifying(s => s.isEmpty)
    )(CheckFeedbackData.apply)(CheckFeedbackData.unapply)
      .verifying(blankFieldIsBlank)
      .verifying(formSubmissionTime(appComponents.config))
  )

  private def getCopyMail(feedbackType: Option[models.Feedback.Type.Value]): Seq[String] = {
    val defaultOpt = config.getOptional[Seq[String]]("ehri.portal.feedback.copyTo")
    ((for {
      ft <- feedbackType
      ct <- config.getOptional[Seq[String]](s"ehri.portal.feedback.$ft.copyTo")
    } yield ct) orElse defaultOpt).getOrElse(Seq.empty)
  }

  private def sendMessageEmail(feedback: models.Feedback)(implicit request: RequestHeader): Unit = {
    val text = feedback.text.map { msg =>
      s"""
        |From: ${feedback.name.getOrElse("Anonymous")} (${feedback.email.getOrElse("no email given")})
        |
        |$msg
      """.stripMargin
    }.getOrElse("No message provided")
    val recipients = getCopyMail(feedback.`type`)
    logger.debug(s"Sending feedback email to ${recipients.size} recipient(s)")
    val email = Email(
      subject = "EHRI Portal Feedback" + feedback.name.map(n => s" from $n").getOrElse(""),
      to = recipients,
      from = s"EHRI User <${config.get[String]("ehri.portal.emails.messages")}>",
      replyTo = feedback.email.toSeq,
      bodyText = Some(text),
      bodyHtml = Some(markdown.renderUntrustedMarkdown(text))
    )
    mailer.send(email)
  }

  def feedback: Action[AnyContent] = OptionalUserAction { implicit request =>
    if (conf.showFeedback) Ok(views.html.feedback.create(models.Feedback.form))
    else Redirect(controllers.portal.routes.Portal.index())
      .flashing("warning" -> "feedback.disabled")
  }

  def feedbackPost: Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val boundForm: Form[models.Feedback] = models.Feedback.form.bindFromRequest()

    def response(f: Form[models.Feedback]): Result =
      if (isAjax) BadRequest(f.errorsAsJson) else BadRequest(views.html.feedback.create(f))

    // check the anti-bot measures and immediately return the original
    // form. No feedback needed since they're (hopefully) a bot.
    if (checkFbForm.bindFromRequest().hasErrors) immediate(response(boundForm))
    else boundForm.fold(
      errorForm => immediate(response(errorForm)),
      feedback => {
        val moreFeedback = request.userOpt.map { user =>
          feedback.copy(userId = Some(user.id), name = Some(feedback.name.getOrElse(user.data.name)))
            .copy(email = feedback.email.orElse(user.account.map(_.email)))
        }.getOrElse(feedback)
          .copy(context = Some(models.FeedbackContext.fromRequest),
            mode = Some(app.mode))
        feedbackService.create(moreFeedback).map { id =>
          sendMessageEmail(moreFeedback)
          if (isAjax) Ok(id)
          else Redirect(controllers.portal.routes.Portal.index())
            .flashing("success" -> "feedback.thanks.message")
        }
      }
    )
  }

  def list(paging: PageParams): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackService.list(paging, params = Map("order" -> "-createdAt")).map { flist =>
      Ok(views.html.feedback.list(flist.copy(items = flist.filter(_.text.isDefined))))
    }
  }

  def deletePost(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackService.delete(id).map(_ => Redirect(controllers.portal.routes.Feedback.list())
      .flashing("success" -> "item.delete.confirmation"))
  }
}
