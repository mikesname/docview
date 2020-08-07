package controllers.portal.account

import java.net.ConnectException
import java.util.UUID
import auth.oauth2.providers.OAuth2Provider
import auth.oauth2.{OAuth2Config, UserData}
import auth.{AuthenticationError, HashedPassword}
import com.google.common.net.HttpHeaders
import controllers.AppComponents
import controllers.base.RecaptchaHelper
import controllers.portal.base.PortalController
import forms.{AccountForms, HoneyPotForm, TimeCheckForm}

import javax.inject.{Inject, Singleton}
import models._
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.data.Form
import play.api.http.{HeaderNames, HttpVerbs}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, JsString}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.libs.openid.OpenIdClient
import play.api.libs.ws.WSClient
import play.api.mvc.{Result, _}
import services.RateLimitChecker
import services.data.{AnonymousUser, AuthenticatedUser}
import services.oauth2.OAuth2Service

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
case class Accounts @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  mailer: MailerClient,
  oAuth2Flow: OAuth2Service,
  ws: WSClient,
  openId: OpenIdClient,
  oauth2Providers: OAuth2Config,
  accountForms: AccountForms,
  rateLimits: RateLimitChecker,
  cache: SyncCacheApi,
) extends PortalController with RecaptchaHelper {

  import accountForms._

  private val logger = Logger(getClass)

  private val portalRoutes = controllers.portal.routes.Portal
  private val accountRoutes = controllers.portal.account.routes.Accounts

  private val rateLimitHitsPerSec: Int = config.get[Int]("ehri.ratelimit.limit")
  private val rateLimitDuration: FiniteDuration = config.get[FiniteDuration]("ehri.ratelimit.timeout")

  private def rateLimitError(implicit r: RequestHeader) = Messages("error.rateLimit", rateLimitDuration.toMinutes)

  private val recaptchaKey = config.getOptional[String]("recaptcha.key.public").getOrElse("fakekey")

  // The max time an OAuth2 flow can be in progress before it times out. The user will
  // then have to begin the flow anew.
  private val oauth2SessionTimeout = config.get[Duration]("ehri.oauth2.sessionExpiration")


  private val openIDAttributes = Seq(
    "email" -> "http://schema.openid.net/contact/email",
    "axemail" -> "http://axschema.org/contact/email",
    "axname" -> "http://axschema.org/namePerson",
    "name" -> "http://openid.netdr/schema/media/spokenname",
    "firstname" -> "http://openid.net/schema/namePerson/first",
    "lastname" -> "http://openid.net/schema/namePerson/last",
    "friendly" -> "http://openid.net/schema/namePerson/friendly"
  )


  /**
    * Prevent people signin up, logging in etc when in read-only mode.
    */
  case class NotReadOnly[A](action: Action[A]) extends Action[A] {
    def apply(request: Request[A]): Future[Result] = {
      if (conf.readOnly) {
        Future.successful(Redirect(portalRoutes.index())
          .flashing("warning" -> "login.disabled"))
      } else action(request)
    }

    lazy val parser: BodyParser[A] = action.parser
    lazy val executionContext: ExecutionContext = action.executionContext
  }

  object NotReadOnlyAction extends CoreActionBuilder[Request, AnyContent] {
    def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
      block(request)
    }

    override def composeAction[A](action: Action[A]): Action[A] = NotReadOnly(action)
  }

  object RateLimit extends CoreActionBuilder[Request, AnyContent] {
    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
      if (request.method != HttpVerbs.POST) block(request)
      else {
        if (rateLimits.checkHits(rateLimitHitsPerSec, rateLimitDuration)(request)) block(request)
        else immediate(TooManyRequests(rateLimitError(request)))
      }
    }
  }

  def signupPost: Action[AnyContent] = NotReadOnlyAction.async { implicit request =>
    implicit val userOpt: Option[UserProfile] = None

    def badForm(form: Form[SignupData], status: Status = BadRequest): Future[Result] = immediate {
      status(
        views.html.account.register(
          form,
          accountRoutes.signupPost(),
          recaptchaKey,
          openidForm,
          oauth2Providers
        )
      )
    }

    val boundForm: Form[SignupData] = signupForm.bindFromRequest()
    checkRecapture.flatMap {
      case false =>
        badForm(boundForm.withGlobalError("error.badRecaptcha"))
      case true if !rateLimits.checkHits(rateLimitHitsPerSec, rateLimitDuration)(request) =>
        badForm(boundForm.withGlobalError(rateLimitError), TooManyRequests)
      case true =>
        boundForm.fold(
          errForm => {
            if (errForm.error(HoneyPotForm.BLANK_CHECK).nonEmpty) {
              logger.warn(s"Honeypot miss on signup from IP ${request.remoteAddress}")
            }
            if (errForm.error(TimeCheckForm.TIMESTAMP).nonEmpty) {
              logger.warn(s"Time-to-submit violation on signup from IP ${request.remoteAddress}")
            }
            badForm(errForm)
          },
          data => {
            accounts.findByEmail(data.email.toLowerCase).flatMap {
              // that email already exists, problem!
              case Some(_) =>
                badForm(boundForm.withError(SignupData.EMAIL, "error.emailExists"))
              // we're okay to proceed...
              case None =>
                implicit val apiUser: AnonymousUser.type = AnonymousUser
                val uuid = UUID.randomUUID()
                val profileData = Map(UserProfileF.NAME -> data.name)
                for {
                  profile <- userDataApi.createNewUserProfile[UserProfile](
                    data = profileData, groups = conf.defaultPortalGroups)
                  account <- accounts.create(Account(
                    id = profile.id,
                    email = data.email.toLowerCase,
                    allowMessaging = data.allowMessaging,
                    password = Some(HashedPassword.fromPlain(data.password))
                  ))
                  _ <- accounts.createToken(account.id, uuid, isSignUp = true)
                  result <- doLogin(account)
                    .map(_.flashing("success" -> "signup.confirmation"))
                } yield {
                  sendValidationEmail(profile.data.name, data.email, uuid)
                  result
                }
            }
          }
        )
    }
  }

  def confirmEmail(token: String): Action[AnyContent] = NotReadOnlyAction.async { implicit request =>
    accounts.findByToken(token, isSignUp = true).flatMap {
      case Some(account) => for {
        _ <- accounts.verify(account, token)
        result <- doLogin(account)
          .map(_.flashing("success" -> "signup.validation.confirmation"))
      } yield result
      case None =>
        immediate(BadRequest(
          controllers.renderError("errors.clientError",
            views.html.errors.genericError(Messages("signup.validation.badToken")))))
    }
  }

  def openIDCallback: Action[AnyContent] = NotReadOnlyAction.async { implicit request =>
    implicit val userOpt: Option[UserProfile] = None

    openId.verifiedId(request).flatMap { info =>
      // check if there's a user with the right id
      accounts.openId.findByUrl(info.id).flatMap {
        case Some(assoc) =>
          logger.info(s"User '${assoc.user.get.id}' logged in via OpenId")
          doLogin(assoc.user.get)
        case None =>
          val email = extractOpenIDEmail(info.attributes)
            .getOrElse(sys.error("Unable to retrieve email info via OpenID"))

          val data = Map("name" -> extractOpenIDName(info.attributes)
            .getOrElse(sys.error("Unable to retrieve name info via OpenID")))

          accounts.findByEmail(email).flatMap {
            case Some(account) =>
              logger.info(s"User '${account.id}' created OpenID association")
              accounts.openId.addAssociation(account.id, info.id)
              doLogin(account)
            case None =>
              implicit val apiUser: AnonymousUser.type = AnonymousUser
              for {
                up <- userDataApi.createNewUserProfile[UserProfile](
                  data, groups = conf.defaultPortalGroups)
                account <- accounts.create(Account(
                  id = up.id,
                  email = email.toLowerCase,
                  verified = true,
                  allowMessaging = conf.canMessage
                ))
                _ <- accounts.openId.addAssociation(account.id, info.id)
                r <- doLogin(account)
              } yield r
          }
      }
    } recover {
      case t: Throwable => BadRequest(
        views.html.account.login(
          passwordLoginForm,
          recaptchaKey,
          openidForm.withGlobalError("error.openId", t.getMessage),
          oauth2Providers
        )
      )
    }
  }

  def signup: Action[AnyContent] = (NotReadOnlyAction andThen OptionalAccountAction).apply { implicit request =>
    logger.debug(s"Signup, session is ${request.session.data}")
    implicit val userOpt: Option[UserProfile] = None
    request.accountOpt match {
      case Some(user) => Redirect(portalRoutes.index())
        .removingFromSession(ACCESS_URI)
        .flashing("warning" -> Messages("login.alreadyLoggedIn", user.email))
      case None =>
        Ok(
          views.html.account.register(
            signupForm,
            accountRoutes.signupPost(),
            recaptchaKey,
            openidForm,
            oauth2Providers
          )
        )
    }
  }


  def login: Action[AnyContent] = (NotReadOnlyAction andThen OptionalAccountAction).apply { implicit request =>
    logger.debug(s"Login, session is ${request.session.data}")
    implicit val userOpt: Option[UserProfile] = None
    request.accountOpt match {
      case Some(user) => Redirect(portalRoutes.index())
        .removingFromSession(ACCESS_URI)
        .flashing("warning" -> Messages("login.alreadyLoggedIn", user.email))
      case None =>
        Ok(views.html.account.login(passwordLoginForm, recaptchaKey, openidForm, oauth2Providers))
    }
  }

  def openIDLoginPost(isLogin: Boolean = true): Action[AnyContent] = NotReadOnlyAction.async { implicit request =>
    def badForm(f: Form[String], status: Status = BadRequest): Result = {
      implicit val userOpt: Option[UserProfile] = None
      status(
        if (isLogin) views.html.account.login(
          passwordLoginForm,
          recaptchaKey,
          f,
          oauth2Providers
        ) else views.html.account.register(
          signupForm,
          accountRoutes.signupPost(),
          recaptchaKey,
          f,
          oauth2Providers
        )
      )
    }

    try {
      val boundForm: Form[String] = openidForm.bindFromRequest()
      boundForm.fold(
        error => immediate(badForm(error)),
        openidUrl => openId
          .redirectURL(openidUrl, accountRoutes.openIDCallback().absoluteURL(conf.https),
            openIDAttributes)
          .map(url => Redirect(url))
          .recover {
            case t: ConnectException =>
              logger.warn(s"OpenID Login connect exception: $t")
              badForm(boundForm.withGlobalError(Messages("error.openId.url", openidUrl)))
            case t =>
              logger.warn(s"OpenID Login argument exception: $t")
              badForm(boundForm.withGlobalError(Messages("error.openId.url", openidUrl)))
          }
      )
    } catch {
      case _: Throwable => immediate(badForm(openidForm.withGlobalError(Messages("error.openId"))))
    }
  }

  def passwordLoginPost: Action[AnyContent] = (NotReadOnlyAction andThen OptionalUserAction).async { implicit request =>
    def badForm(f: Form[(String, String)], status: Status = BadRequest): Future[Result] = immediate {
      status(
        views.html.account.login(
          f,
          recaptchaKey,
          openidForm,
          oauth2Providers
        )
      )
    }

    val boundForm = passwordLoginForm.bindFromRequest()
    if (!rateLimits.checkHits(rateLimitHitsPerSec, rateLimitDuration)(request)) {
      badForm(boundForm.withGlobalError(rateLimitError), TooManyRequests)
    } else boundForm.fold(
      errForm => badForm(errForm),
      data => {
        val (email, pw) = data
        accounts.authenticateByEmail(email, pw).flatMap {
          case Some(account) =>
            // Legacy accounts have an MD5 password encoded via BCrypt, so
            // we need to re-save this and untag them as legacy.
            if (account.isLegacy) {
              logger.info(s"Updating legacy account for user: ${account.id}")
              accounts.update(account = account.copy(
                password = Some(HashedPassword.fromPlain(pw)),
                isLegacy = false
              )).flatMap(doLogin)
            } else {
              logger.info(s"User logged in via password: ${account.id}")
              doLogin(account)
            }
          case None =>
            badForm(boundForm.withGlobalError("login.error.badUsernameOrPassword"))
        }
      }
    )
  }

  def logout: Action[AnyContent] = Action.async { implicit authRequest =>
    gotoLogoutSucceeded(authRequest)
  }

  def oauth2Login(providerId: String, code: Option[String], state: Option[String]): Action[AnyContent] =
    NotReadOnlyAction.async { implicit request =>
      oauth2(providerId, code, state, isLogin = true).apply(request)
    }

  def oauth2Register(providerId: String, code: Option[String], state: Option[String]): Action[AnyContent] =
    NotReadOnlyAction.async { implicit request =>
      oauth2(providerId, code, state, isLogin = false).apply(request)
    }

  def oauth2(providerId: String, code: Option[String], state: Option[String], isLogin: Boolean): Action[AnyContent] =
    NotReadOnlyAction.async { implicit request =>
      oauth2Providers.providers.find(_.name == providerId).map { provider =>

        // Create a random nonce to stamp this OAuth2 session
        val sessionKey = "sid"
        val sessionId = request.session.get(sessionKey).getOrElse(UUID.randomUUID().toString)
        val handlerUrl: String =
          if (isLogin) accountRoutes.oauth2Login(providerId).absoluteURL(conf.https)
          else accountRoutes.oauth2Register(providerId).absoluteURL(conf.https)

        code match {

          // First stage of request. User is redirected to an external URL, where they
          // authorize our app. The external provider then sends us back to this handler
          // with a code parameter, initiating the second phase.
          case None =>
            val state = UUID.randomUUID().toString
            cache.set(sessionId, state, oauth2SessionTimeout)
            val redirectUrl = provider.buildRedirectUrl(handlerUrl, state)
            logger.debug(s"OAuth2 redirect URL: $redirectUrl")
            immediate(Redirect(redirectUrl).addingToSession(sessionKey -> sessionId))

          // Second phase of request. Using our new code, and with the same random session
          // nonce, proceed to get an access token, the user data, and handle the account
          // creation or updating.
          case Some(c) => if (checkSessionNonce(sessionId, state)) {
            cache.remove(sessionId)
            (for {
              info <- oAuth2Flow.getAccessToken(provider, handlerUrl, c)
              userData <- oAuth2Flow.getUserData(provider, info)
              account <- getOrCreateAccount(provider, userData)
              result <- doLogin(account).map(_.removingFromSession(sessionKey))
            } yield result) recover {
              case AuthenticationError(msg) =>
                logger.error(msg)
                val url = if (isLogin) accountRoutes.login() else accountRoutes.signup()
                Redirect(url)
                  .removingFromSession(sessionKey)
                  .flashing("danger" -> Messages("login.error.oauth2.info", provider.name.capitalize))
            }
          } else authenticationFailed(request)
            .map(_.flashing("danger" -> Messages("login.error.oauth2.badSessionId", provider.name.capitalize)))
        }
      } getOrElse {
        notFoundError(request)
      }
    }

  def forgotPassword: Action[AnyContent] = OptionalUserAction { implicit request =>
    Ok(views.html.account.forgotPassword(forgotPasswordForm,
      recaptchaKey, accountRoutes.forgotPasswordPost()))
  }

  def forgotPasswordPost: Action[AnyContent] = (NotReadOnlyAction andThen OptionalUserAction).async { implicit request =>
    checkRecapture.flatMap {
      case false =>
        val errForm = forgotPasswordForm.withGlobalError("error.badRecaptcha")
        immediate(BadRequest(views.html.account.forgotPassword(errForm,
          recaptchaKey, accountRoutes.forgotPasswordPost())))
      case true =>
        forgotPasswordForm.bindFromRequest().fold({ errForm =>
          immediate(BadRequest(views.html.account.forgotPassword(errForm,
            recaptchaKey, accountRoutes.forgotPasswordPost())))
        }, { email =>
          val resp = Redirect(portalRoutes.index())
            .flashing("warning" -> "login.password.reset.sentLink")
          accounts.findByEmail(email).flatMap {
            case Some(account) =>
              val uuid = UUID.randomUUID()
              for {
                p <- userDataApi.get[UserProfile](account.id)
                _ <- accounts.createToken(account.id, uuid, isSignUp = false)
                _ = sendResetEmail(p.data.name, account.email, uuid)
              } yield resp
            // Note: to avoid leaking email data we send the same response whether or not the email
            // exists on the system.
            case None => immediate(resp)
          }
        })
    }
  }

  def passwordReminderSent: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.account.passwordReminderSent())
  }

  def changePassword: Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.account.changePassword(request.user.account.get, changePasswordForm,
      accountRoutes.changePasswordPost()))
  }

  /**
    * Store a changed password.
    */
  def changePasswordPost: Action[AnyContent] = (NotReadOnlyAction andThen WithUserAction).async { implicit request =>
    val account = request.user.account.get
    val boundForm = changePasswordForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.account.changePassword(
        account, errForm, accountRoutes.changePasswordPost()))),
      data => {
        val (current, newPw, _) = data
        accounts.authenticateById(request.user.id, current).flatMap {
          case Some(acc) => accounts.update(acc.copy(
            password = Some(HashedPassword.fromPlain(newPw)),
            isLegacy = false
          )).map { _ =>
            logger.info(s"Password change: ${acc.id}")
            Redirect(controllers.portal.users.routes.UserProfiles.updateProfile())
              .flashing("success" -> "login.password.change.confirmation")
          }
          case None =>
            immediate(BadRequest(views.html.account.changePassword(
              account, boundForm.withGlobalError("login.error.badUsernameOrPassword"),
              accountRoutes.changePasswordPost())))
        }
      }
    )
  }

  def changeEmail: Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.account.changeEmail(request.user.account.get, changeEmailForm,
      accountRoutes.changeEmailPost()))
  }

  /**
    * Store a changed password.
    */
  def changeEmailPost: Action[AnyContent] = (NotReadOnlyAction andThen WithUserAction).async { implicit request =>
    val account = request.user.account.get
    val boundForm = changeEmailForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.account.changeEmail(
        account, errForm, accountRoutes.changeEmailPost()))),
      data => {
        val (newEmail, pw) = data
        accounts.authenticateById(request.user.id, pw).flatMap {
          case Some(acc) =>
            accounts.findByEmail(newEmail).flatMap {
              case Some(otherAcc) if otherAcc.id != acc.id =>
                // Bad request... email is already taken.
                immediate(BadRequest(views.html.account.changeEmail(
                  account, boundForm.withGlobalError("login.error.badEmail"),
                  accountRoutes.changeEmailPost())))

              case _ =>
                accounts.update(acc.copy(email = newEmail)).map { _ =>
                  logger.info(s"Email change: ${acc.id}")
                  Redirect(controllers.portal.users.routes.UserProfiles.updateProfile())
                    .flashing("success" -> "login.email.change.confirmation")
            }
          }
          case None =>
            immediate(BadRequest(views.html.account.changeEmail(
              account, boundForm.withGlobalError("login.error.badUsernameOrPassword"),
              accountRoutes.changeEmailPost())))
        }
      }
    )
  }

  def resetPassword(token: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    accounts.findByToken(token).map {
      case Some(account) => Ok(views.html.account.resetPassword(resetPasswordForm,
        accountRoutes.resetPasswordPost(token)))
      case _ => Redirect(accountRoutes.forgotPassword())
        .flashing("danger" -> "login.error.badResetToken")
    }
  }

  def resendVerificationPost(): Action[AnyContent] = WithUserAction.async { implicit request =>
    request.user.account match {
      case Some(account) =>
        val uuid = UUID.randomUUID()
        accounts.createToken(account.id, uuid, isSignUp = true).map { _ =>
          sendValidationEmail(request.user.data.name, account.email, uuid)
          val redirect = request.headers.get(HttpHeaders.REFERER)
            .getOrElse(portalRoutes.index().url)
          Redirect(redirect).flashing("success" -> "mail.emailConfirmationResent")
        }
      case _ => authenticationFailed(request)
    }
  }

  def resetPasswordPost(token: String): Action[AnyContent] = (NotReadOnlyAction andThen OptionalUserAction).async { implicit request =>
    val boundForm: Form[(String, String)] = resetPasswordForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.account.resetPassword(errForm,
        accountRoutes.resetPasswordPost(token)))),
      { case (pw, _) =>
        accounts.findByToken(token).flatMap {
          case Some(account) =>
            for {
              _ <- accounts.expireTokens(account.id)
              _ <- accounts.update(account.copy(
                password = Some(HashedPassword.fromPlain(pw)),
                isLegacy = false
              ))
              request <- doLogin(account)
                .map(_.flashing("success" -> "login.password.reset.confirmation"))
            } yield request
          case None => immediate(BadRequest(views.html.account.resetPassword(
            boundForm.withGlobalError("login.error.badResetToken"),
            accountRoutes.resetPasswordPost(token))))
        }
      }
    )
  }

  //
  // Helpers
  //

  private def extractOpenIDEmail(attrs: Map[String, String]): Option[String] =
    attrs.get("email").orElse(attrs.get("axemail"))

  private def extractOpenIDName(attrs: Map[String, String]): Option[String] = {
    val fullName = for {
      fn <- attrs.get("firstname")
      ln <- attrs.get("lastname")
    } yield s"$fn $ln"
    attrs.get("name").orElse(attrs.get("fullname")).orElse(fullName)
  }

  private def doLogin(account: Account)(implicit request: RequestHeader): Future[Result] =
    accounts.setLoggedIn(account).flatMap(_ => gotoLoginSucceeded(account.id))

  private def updateUserInfo(account: Account, userData: UserData): Future[UserProfile] = {
    implicit val apiUser: AuthenticatedUser = AuthenticatedUser(account.id)
    userDataApi.get[UserProfile](account.id).flatMap { up =>
      val data: Seq[(String, JsString)] = Seq(
        // Only update the user image if it hasn't already been set
        UserProfileF.IMAGE_URL -> up.data.imageUrl.filter(_.trim.nonEmpty).orElse(userData.imageUrl)
      ).collect { case (k, Some(v)) => k -> JsString(v)}
      userDataApi.patch[UserProfile](account.id, JsObject(data))
    }
  }

  private def createNewProfile(userData: UserData): Future[Account] = {
    implicit val apiUser: AnonymousUser.type = AnonymousUser
    val profileData = Map(UserProfileF.NAME -> Some(userData.name),  UserProfileF.IMAGE_URL -> userData.imageUrl)
      .collect{ case (k, Some(v)) if v.trim.nonEmpty => k -> v }
    for {
      profile <- userDataApi.createNewUserProfile[UserProfile](
        profileData, groups = conf.defaultPortalGroups)
      account <- accounts.create(Account(
        id = profile.id,
        email = userData.email.toLowerCase,
        verified = true,
        allowMessaging = conf.canMessage
      ))
    } yield account
  }

  private def getOrCreateAccount(provider: OAuth2Provider, userData: UserData): Future[Account] = {
    accounts.oAuth2.findByProviderInfo(userData.providerId, provider.name).flatMap { assocOpt =>
      assocOpt.flatMap(_.user).map { account =>
        logger.info(s"Found existing association for ${userData.name} -> ${provider.name}")
        for {
          updated <- accounts.update(account.copy(verified = true))
          _ <- updateUserInfo(updated, userData)
        } yield updated
      } getOrElse {
        accounts.findByEmail(userData.email).flatMap { accountOpt =>
          accountOpt.map { account =>
            logger.info(s"Creating new association for ${userData.name} -> ${provider.name}")
            for {
              updated <- accounts.update(account.copy(verified = true))
              _ <- accounts.oAuth2.addAssociation(updated.id, userData.providerId, provider.name)
              _ <- updateUserInfo(updated, userData)
            } yield updated
          } getOrElse {
            logger.info(s"Creating new account for ${userData.name} -> ${provider.name}")
            for {
              newAccount <- createNewProfile(userData)
              _ <- accounts.oAuth2.addAssociation(newAccount.id, userData.providerId, provider.name)
            } yield newAccount
          }
        }
      }
    }
  }

  private def checkSessionNonce[A](sessionId: String, state: Option[String])(implicit request: RequestHeader): Boolean = {
    val origStateOpt: Option[String] = cache.get[String](sessionId)
    (for {
      // check if the state we sent is equal to the one we're receiving now before continuing the flow.
      originalState <- origStateOpt
      currentState <- state
    } yield {
      val check = originalState == currentState
      if (!check) logger.error(s"OAuth2 state mismatch: sessionId: $sessionId, " +
        s"original token: $origStateOpt, new token: $state")
      check
    }).getOrElse {
      logger.error(s"Missing OAuth2 state data: session key -> $sessionId at ${request.path} [${request.rawQueryString}]. " +
        s"Referer: ${request.headers.get(HeaderNames.REFERER)}, " +
        s"UserAgent: ${request.headers.get(HeaderNames.USER_AGENT)}")
      false
    }
  }

  private def sendResetEmail(name: String, emailAddress: String, uuid: UUID)(implicit request: RequestHeader): String = {
    val email = Email(
      subject = "EHRI Password Reset",
      to = Seq(emailAddress),
      from = s"EHRI Password Reset <${config.get[String]("ehri.portal.emails.help")}>",
      bodyText = Some(views.txt.account.mail.forgotPassword(uuid).body)
    )
    mailer.send(email)
  }

  private def sendValidationEmail(name: String, emailAddress: String, uuid: UUID)(implicit request: RequestHeader): String = {
    val email = Email(
      subject = "Please confirm your EHRI Account Email",
      from = s"EHRI Email Validation <${config.get[String]("ehri.portal.emails.help")}>",
      to = Seq(emailAddress),
      bodyText = Some(views.txt.account.mail.confirmEmail(uuid).body)
    )
    mailer.send(email)
  }
}
