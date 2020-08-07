package controllers.portal.base

import akka.http.scaladsl.model.Uri
import auth.handler.AuthHandler
import config.AppConfig
import controllers.base.{ControllerHelpers, CoreActionBuilders}
import controllers.{AppComponents, renderError}
import cookies.{SessionPreferences, SessionPrefs}
import lifecycle.ItemLifecycle
import models.{EntityType, EventType, Model, UserProfile}
import models.view.{MessagingInfo, UserDetails}
import play.api.http.{ContentTypes, HeaderNames}
import play.api.mvc.{Result, _}
import play.api.{Configuration, Logger}
import services.accounts.AccountManager
import services.data.{ApiUser, DataApi}
import services.search.{SearchEngine, SearchItemResolver}
import utils._
import views.html.MarkdownRenderer
import views.html.errors.{itemNotFound, maintenance, pageNotFound}

import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


trait PortalController
  extends CoreActionBuilders
  with ControllerHelpers
  with SessionPreferences[SessionPrefs] {

  private def logger = Logger(getClass)

  // Abstract controller components, injected into super classes
  def appComponents: AppComponents

  // Implicits hoisted to class scope so as to be provided to views
  protected implicit def conf: AppConfig = appComponents.conf
  protected implicit def markdown: MarkdownRenderer = appComponents.markdown
  protected implicit def config: Configuration = appComponents.config

  protected def accounts: AccountManager = appComponents.accounts
  protected def dataApi: DataApi = appComponents.dataApi
  protected def authHandler: AuthHandler = appComponents.authHandler
  protected def itemLifecycle: ItemLifecycle = appComponents.itemLifecycle

  protected def searchEngine: SearchEngine = appComponents.searchEngine
  protected def searchResolver: SearchItemResolver = appComponents.searchResolver

  // By default, all controllers require auth unless ehri.portal.secured
  // is set to false in the config, which it is by default.
  override def staffOnly: Boolean = config.getOptional[Boolean]("ehri.portal.secured").getOrElse(true)
  override def verifiedOnly: Boolean = config.getOptional[Boolean]("ehri.portal.secured").getOrElse(true)

  /**
   * The user's default preferences. The `SessionPreferences` trait generates
   * a preferences object from a request object's cookie, falling back to this
   * if the cookie is invalid or doesn't exist. It will then generate an
   * **implicit** `preferences` object that can be picked up by views.
   */
  protected val defaultPreferences = new SessionPrefs

  /**
   * Ensure that functions requiring an optional user in scope
   * can retrieve it automatically from a user details object.
   */
  implicit def userFromDetails(implicit details: UserDetails): Option[UserProfile] = details.userOpt

  private def userWatchCacheKey(userId: String) = s"$userId-watchlist"

  protected def clearWatchedItemsCache(userId: String): Unit = appComponents.cacheApi.remove(userWatchCacheKey(userId))

  /**
   * Activity event types that we think the user would care about.
   */
  val activityEventTypes = List(
    EventType.deletion,
    EventType.creation,
    EventType.modification,
    EventType.modifyDependent,
    EventType.createDependent,
    EventType.deleteDependent,
    EventType.link,
    EventType.annotation,
    EventType.watch
  )

  /**
   * Activity item types that we think the user might care about.
   */
  val activityItemTypes = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.Country,
    EntityType.HistoricalAgent
  )

  /**
    * A redirect target after a successful user login.
    */
  override def loginSucceeded(request: RequestHeader): Future[Result] = {
    logger.debug(s"Access URI: ${request.session.get(ACCESS_URI)}")
    val uri = request.session.get(ACCESS_URI)
      .filterNot(Uri(_).path.toString() == controllers.portal.account.routes.Accounts.login().url)
      .filterNot(Uri(_).path.toString() == controllers.portal.account.routes.Accounts.signup().url)
      .getOrElse(controllers.portal.users.routes.UserProfiles.profile().url)
    logger.debug(s"Redirecting logged-in user to: $uri")
    immediate(Redirect(uri).removingFromSession(ACCESS_URI)(request))
  }

  /**
    * A redirect target after a successful user logout.
    */
  override def logoutSucceeded(request: RequestHeader): Future[Result] =
    immediate(Redirect(controllers.portal.routes.Portal.index())
      .flashing("success" -> "logout.confirmation"))


  override def verifiedOnlyError(request: RequestHeader): Future[Result] = {
    implicit val r: RequestHeader = request
    fetchProfile(request).map { implicit userOpt =>
      Unauthorized(renderError("errors.verifiedOnly", views.html.errors.verifiedOnly()))
    }
  }

  override def staffOnlyError(request: RequestHeader): Future[Result] = {
    implicit val r: RequestHeader = request
    fetchProfile(request).map { implicit userOpt =>
      Unauthorized(renderError("errors.staffOnly", views.html.errors.staffOnly()))
    }
  }

  override def notFoundError(request: RequestHeader, msg: Option[String] = None): Future[Result] = {
    val doMoveCheck: Boolean = config.getOptional[Boolean]("ehri.handlePageMoved").getOrElse(false)
    implicit val r: RequestHeader = request
    fetchProfile(request).flatMap { implicit userOpt =>
      val notFoundResponse = NotFound(renderError("errors.itemNotFound", itemNotFound(msg)))
      if (!doMoveCheck) immediate(notFoundResponse)
      else for {
        maybeMoved <- appComponents.pageRelocator.hasMovedTo(request.path)
      } yield maybeMoved match {
        case Some(path) => MovedPermanently(utils.http.iriToUri(path))
        case None =>
          logger.warn(s"404 at ${request.path}. " +
            s"Referer: ${request.headers.get(HeaderNames.REFERER)}, " +
            s"UserAgent: ${request.headers.get(HeaderNames.USER_AGENT)}")
          notFoundResponse
      }
    }
  }

  override def downForMaintenance(request: RequestHeader): Future[Result] = {
    implicit val r: RequestHeader = request
    fetchProfile(request).map { userOpt =>
      ServiceUnavailable(renderError("errors.maintenance", maintenance()))
    }
  }

  /**
   * A redirect target after a failed authentication.
   */
  override def authenticationFailed(request: RequestHeader): Future[Result] = {
    implicit val r: RequestHeader = request
    logger.warn(s"Auth failed for: ${request.uri}")
    if (isAjax(request)) {
      immediate(Unauthorized("authentication failed"))
    } else {
      immediate(Redirect(controllers.portal.account.routes.Accounts.login())
        .addingToSession(ACCESS_URI -> request.uri))
    }
  }

  override def authorizationFailed(request: RequestHeader, user: UserProfile): Future[Result] = {
    implicit val r: RequestHeader = request
    fetchProfile(request).map { implicit userOpt =>
      Forbidden(renderError("errors.permissionDenied", views.html.errors.permissionDenied()))
    }
  }

  /**
   * Wrap some code generating an optional result, falling back to a 404.
   */
  def itemOr404(f: => Option[Result])(implicit request: RequestHeader): Result = {
    f.getOrElse(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }

  /**
    * Wrap some code generating an optional result, falling back to a 404.
    */
  def futurePageOr404(f: => Option[Future[Result]])(implicit request: RequestHeader): Future[Result] = {
    f.getOrElse(immediate(NotFound(renderError("errors.pageNotFound", pageNotFound()))))
  }

  /**
   * Wrap some code generating an optional result, falling back to a 404.
   */
  def futureItemOr404(f: => Option[Future[Result]])(implicit request: RequestHeader): Future[Result] = {
    f.getOrElse(immediate(NotFound(renderError("errors.itemNotFound", itemNotFound()))))
  }

  /**
   * Given an optional item and a function to produce a
   * result from it, run the function or fall back on a 404.
   */
  def itemOr404[T](item: Option[T])(f: => T => Result)(implicit request: RequestHeader): Result = {
    item.map(f).getOrElse(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }

  /**
   * Fetched watched items for an optional user.
   */
  protected def watchedItemIds(implicit userIdOpt: Option[String]): Future[Seq[String]] = userIdOpt.map { userId =>
    appComponents.cacheApi.get[Seq[String]](userWatchCacheKey(userId)) match {
      case Some(seq) => immediate(seq)
      case None =>
        import scala.concurrent.duration._
        implicit val apiUser: ApiUser = ApiUser(Some(userId))
        userDataApi.watching[Model](userId, PageParams.empty.withoutLimit).map { page =>
          val seq = page.items.map(_.id)
          appComponents.cacheApi.set(userWatchCacheKey(userId), seq, 20.minutes)
          seq
        }
    }
  }.getOrElse(Future.successful(Seq.empty))

  protected def exportXml(entityType: EntityType.Value, id: String, formats: Seq[String], asFile: Boolean = false)(
      implicit apiUser: ApiUser, request: RequestHeader): Future[Result] = {
    val format: String = request.getQueryString("format")
      .filter(formats.contains).getOrElse(formats.head)
    val params = request.queryString.filter(_._1 == "lang")
    userDataApi.stream(s"classes/$entityType/$id/$format", params = params,
      headers = Headers(HeaderNames.ACCEPT -> "text/xml,application/zip")).map { sr =>
      val ct = sr.headers.get(HeaderNames.CONTENT_TYPE)
        .flatMap(_.headOption).getOrElse(ContentTypes.XML)

      val encodedId = java.net.URLEncoder.encode(id, StandardCharsets.UTF_8.name())
      val disp = if (ct.contains("zip"))
        Seq("Content-Disposition" -> ("attachment; filename=\"" + s"$encodedId-$format.zip" + "\""))
      else if (asFile)
        Seq("Content-Disposition" -> ("attachment; filename=\"" + s"$encodedId-$format.xml" + "\""))
      else Seq.empty

      // If we're streaming a zip file, send it as an attachment
      // with a more useful filename...
      Status(sr.status).chunked(sr.bodyAsSource).as(ct).withHeaders(disp: _*)
    }
  }

  case class UserDetailsRequest[A](
    watched: Seq[String],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  /**
   * Action which fetches a user's profile and list of watched items.
   */
  protected def UserBrowseAction: ActionBuilder[UserDetailsRequest, AnyContent] = OptionalAccountAction andThen new CoreActionTransformer[OptionalAccountRequest,
    UserDetailsRequest] {
    override protected def transform[A](request: OptionalAccountRequest[A]): Future[UserDetailsRequest[A]] = {
      request.accountOpt.map { account =>
        implicit val apiUser: ApiUser = ApiUser(Some(account.id))
        val userF: Future[UserProfile] = userDataApi.get[UserProfile](account.id)
        val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = Some(account.id))
        for {
          user <- userF
          userWithAccount = user.copy(account=Some(account))
          watched <- watchedF
        } yield UserDetailsRequest(watched, Some(userWithAccount), request)
      } getOrElse {
        immediate(UserDetailsRequest(Nil, None, request))
      }
    }
  }

  import play.api.data.Form
  import play.api.data.Forms._
  protected val messageForm: Form[(String, String, Boolean)] = Form(
    tuple(
      "subject" -> nonEmptyText,
      "message" -> nonEmptyText,
      "copySelf" -> default(boolean, false)
    )
  )

  /**
    * Ascertain if a user can receive messages from other users.
    */
  protected def getMessagingInfo(senderId: String, recipientId: String)(implicit apiUser: ApiUser): Future[MessagingInfo] = {
    // First, find their account. If we don't have
    // an account we don't have an email, so we can't
    // message them... Ignore accounts which have disabled
    // messaging.
    val info = MessagingInfo(recipientId)(appComponents.config)
    if (senderId == recipientId) immediate(info)
    else accounts.findById(recipientId).flatMap {
      case Some(account) if account.allowMessaging =>
        userDataApi.isBlocking(recipientId, senderId)
          .map(blocking => !blocking)
          .map(canMessage => info.copy(canMessage = canMessage))
      case _ => immediate(info)
    }
  }
}
