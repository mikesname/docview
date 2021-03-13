package controllers.base

import akka.stream.scaladsl.Flow
import auth.handler.AuthHandler
import config.AppConfig
import lifecycle.ItemLifecycle
import models.{Account, ContentTypes, PermissionType, UserProfile}
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.{Result, _}
import services.accounts.AccountManager
import services.data.{ApiUser, AuthenticatedUser, DataApi, DataApiHandle}

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}


/**
  * Trait containing Action wrappers to handle different
  * types of site management and request authentication concerns
  */
trait CoreActionBuilders extends BaseController with ControllerHelpers {

  /**
    * Inheriting controllers need to be provided/injected with
    * a dataApi implementation.
    */
  protected def dataApi: DataApi

  protected def authHandler: AuthHandler

  protected def accounts: AccountManager

  protected def itemLifecycle: ItemLifecycle

  protected def conf: AppConfig

  protected implicit val implicitEc: ExecutionContext = controllerComponents.executionContext
  protected val parsers: PlayBodyParsers = controllerComponents.parsers

  protected trait CoreActionBuilder[+R[_], B] extends ActionBuilder[R, AnyContent] {
    override protected def executionContext: ExecutionContext = controllerComponents.executionContext

    override def parser: BodyParser[AnyContent] = controllerComponents.parsers.defaultBodyParser
  }

  protected trait CoreActionTransformer[-R[_], +P[_]] extends ActionTransformer[R, P] {
    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  protected trait CoreActionRefiner[-R[_], +P[_]] extends ActionRefiner[R, P] {
    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  protected trait CoreActionFilter[R[_]] extends ActionFilter[R] {
    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  /**
    * Obtain a handle to the dataApi database in the context of
    * a particular user.
    *
    * @param apiUser the current user
    * @return a data api handle
    */
  protected def userDataApi(implicit apiUser: ApiUser): DataApiHandle =
    dataApi.withContext(apiUser)

  /**
    * Indicates that the current controller is only accessible to
    * staff accounts.
    */
  protected def staffOnly = true

  /**
    * Indicates that the current controller is only accessible
    * to verified accounts.
    */
  protected def verifiedOnly = true

  /**
    * Indicates that the current controller is secured, which,
    * if set to false, overrides staffOnly and verifiedOnly.
    */
  protected lazy val secured: Boolean = config.getOptional[Boolean]("ehri.secured").getOrElse(true)

  /**
    * Abstract response methods that should be implemented by inheritors.
    */
  protected def verifiedOnlyError(request: RequestHeader): Future[Result]

  /**
    * This controller can only be accessed by staff
    */
  protected def staffOnlyError(request: RequestHeader): Future[Result]

  /**
    * A dataApi resource was not found
    */
  protected def notFoundError(request: RequestHeader, msg: Option[String] = None): Future[Result]

  /**
    * The user didn't have the required permissions
    */
  protected def authorizationFailed(request: RequestHeader, user: UserProfile): Future[Result]

  /**
    * The user was not authenticated
    */
  protected def authenticationFailed(request: RequestHeader): Future[Result]

  /**
    * The site is down currently for maintenance
    */
  protected def downForMaintenance(request: RequestHeader): Future[Result]

  /**
    * Handle a successful login
    */
  protected def loginSucceeded(request: RequestHeader): Future[Result]

  protected def logoutSucceeded(request: RequestHeader): Future[Result]

  /**
    * Handle a successful login with a custom action
    */
  protected def gotoLoginSucceeded(userId: String, result: => Future[Result])(implicit request: RequestHeader): Future[Result] =
    authHandler.login(userId, result)

  /**
    * Handle a successful login
    */
  protected def gotoLoginSucceeded(userId: String)(implicit request: RequestHeader): Future[Result] =
    authHandler.login(userId, loginSucceeded(request))

  /**
    * Handle a successful logout with a custom action
    */
  protected def gotoLogoutSucceeded(result: => Future[Result])(implicit request: RequestHeader): Future[Result] =
    authHandler.logout(result)

  /**
    * Handle a successful logout
    */
  protected def gotoLogoutSucceeded(implicit request: RequestHeader): Future[Result] =
    authHandler.logout(logoutSucceeded(request))

  /**
    * Base trait for any type of request that contains
    * an optional user profile (which is most of them.)
    */
  protected trait WithOptionalUser {
    self: WrappedRequest[_] =>
    def userOpt: Option[UserProfile]
  }

  /**
    * Base trait for any type of request that contains
    * an authenticated user profile.
    */
  protected trait WithUser {
    self: WrappedRequest[_] =>
    def user: UserProfile
  }

  /**
    * A wrapped request that optionally contains a user's account.
    *
    * @param accountOpt the optional account
    * @param request    the underlying request
    * @tparam A the type of underlying request
    */
  protected case class OptionalAccountRequest[A](accountOpt: Option[Account], request: Request[A])
    extends WrappedRequest[A](request)

  /**
    * A wrapped request that optionally contains a user's profile.
    *
    * @param userOpt the optional profile
    * @param request the underlying request
    * @tparam A the type of underlying request
    */
  protected case class OptionalUserRequest[A](userOpt: Option[UserProfile], request: Request[A])
    extends WrappedRequest[A](request)
      with WithOptionalUser

  /**
    * A wrapped request that contains a user's profile.
    *
    * @param user    the profile
    * @param request the underlying request
    * @tparam A the type of underlying request
    */
  protected case class WithUserRequest[A](user: UserProfile, request: Request[A])
    extends WrappedRequest[A](request)
      with WithUser

  /**
    * Implicit helper to convert an in-scope optional profile to an `ApiUser` instance.
    *
    * @param userOpt an optional profile
    * @return an API user, which may be anonymous
    */
  protected implicit def userOpt2apiUser(implicit userOpt: Option[UserProfile]): ApiUser =
    ApiUser(userOpt.map(_.id))

  /**
    * Implicit helper to convert an in-scope profile to an `AuthenticatedUser` instance.
    *
    * @param user a user profile
    * @return an authenticated API user
    */
  protected implicit def user2apiUser(implicit user: UserProfile): AuthenticatedUser =
    AuthenticatedUser(user.id)

  /**
    * Implicit helper to transform an in-scope `OptionalProfileRequest` (of any type)
    * into an optional user profile. This is used by views that need an implicit user profile
    * but are only given an `OptionalProfileRequest`.
    *
    * @param r an optional user request
    * @return an optional user profile
    */
  protected implicit def optionalUserRequest2UserOpt(implicit r: WithOptionalUser): Option[UserProfile] =
    r.userOpt

  /**
    * Implicit helper to transform an in-scope `OptionalAuthRequest` into an ApiUser.
    *
    * @param oar an optional auth request
    * @return an ApiUser, possibly anonymous
    */
  protected implicit def optionalAuthRequest2apiUser(implicit oar: OptionalAccountRequest[_]): ApiUser =
    ApiUser(oar.accountOpt.map(_.id))

  /**
    * Implicit helper to transform an in-scope `ProfileRequest` (of any type)
    * into an optional (but full) user profile. Used by views that need a user profile but are only given
    * a `ProfileRequest`
    *
    * @param r the request with an authenticated user
    * @return an optional user profile
    */
  protected implicit def userRequest2userOpt(implicit r: WithUser): Option[UserProfile] =
    Some(r.user)

  /**
    * A request, fetch a user's profile with global permissions.
    *
    * @param request a request header
    * @return an optional profile
    */
  protected def fetchProfile(request: RequestHeader): Future[Option[UserProfile]] = {
    authHandler.restoreAccount(request).flatMap {
      case (Some(account), _) => fetchProfile(account)
      case _ => immediate(Option.empty)
    }
  }

  /**
    * Given the user's account, fetch their profile with global permissions.
    *
    * @param account the account object
    * @return an optional profile
    */
  protected def fetchProfile(account: Account): Future[Option[UserProfile]] = {
    implicit val apiUser: ApiUser = AuthenticatedUser(account.id)
    val userF = userDataApi.get[UserProfile](UserProfile.UserProfileResource, account.id)
    val globalPermsF = userDataApi.globalPermissions(account.id)
    (for {
      user <- userF
      globalPerms <- globalPermsF
      profile = user.copy(account = Some(account), globalPermissions = Some(globalPerms))
    } yield Some(profile)).recover {
      case _:Exception => Option.empty
    }
  }

  /**
    * Transform an `OptionalAuthRequest` into an `OptionalProfileRequest` by
    * fetching the profile associated with the account and the user's global
    * permissions.
    */
  protected object FetchProfile extends ActionTransformer[OptionalAccountRequest, OptionalUserRequest] {
    def transform[A](request: OptionalAccountRequest[A]): Future[OptionalUserRequest[A]] = request.accountOpt.fold(
      ifEmpty = immediate(OptionalUserRequest[A](None, request))
    ) { account =>
      fetchProfile(account).map(profileOpt => OptionalUserRequest[A](profileOpt, request))
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  /**
    * If the global read-only flag is enabled, remove the account from
    * the request, globally denying all secured actions.
    */
  protected object ReadOnlyTransformer extends ActionTransformer[OptionalAccountRequest, OptionalAccountRequest] {
    protected def transform[A](request: OptionalAccountRequest[A]): Future[OptionalAccountRequest[A]] = immediate {
      if (conf.readOnly) OptionalAccountRequest(None, request) else request
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  protected object EmbedTransformer extends ActionTransformer[OptionalAccountRequest, OptionalAccountRequest] {
    protected def transform[A](request: OptionalAccountRequest[A]): Future[OptionalAccountRequest[A]] = immediate {
      if (conf.isEmbedMode(request)) OptionalAccountRequest(None, request) else request
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  /**
    * If the global read-only flag is enabled, remove the account from
    * the request, globally denying all secured actions.
    */
  protected object MaintenanceFilter extends ActionFilter[OptionalAccountRequest] {
    override protected def filter[A](request: OptionalAccountRequest[A]): Future[Option[Result]] = {
      if (conf.maintenance) downForMaintenance(request).map(r => Some(r))
      else immediate(None)
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  /**
    * If the IP WHITELIST file is present, check the incoming IP and show a 503 to
    * everyone else.
    */
  protected object IpFilter extends ActionFilter[OptionalAccountRequest] {
    override protected def filter[A](request: OptionalAccountRequest[A]): Future[Option[Result]] = {
      conf.ipFilter.map { whitelist =>
        // Extract the client from the forwarded header, falling back
        // on the remote address. This is dependent on the proxy situation.
        if (whitelist.contains(request.remoteAddress)) immediate(None)
        else downForMaintenance(request).map(r => Some(r))
      }.getOrElse(immediate(None))
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  /**
    * Check the user is allowed in this controller based on their account's
    * `staff` and `verified` flags.
    */
  protected object AllowedFilter extends ActionFilter[OptionalAccountRequest] {
    protected def filter[A](request: OptionalAccountRequest[A]): Future[Option[Result]] = {
      request.accountOpt.fold(
        if ((staffOnly || verifiedOnly) && secured) authenticationFailed(request).map(r => Some(r))
        else immediate(None)
      ) { account =>
        if (staffOnly && secured && !account.staff) staffOnlyError(request).map(r => Some(r))
        else if (verifiedOnly && secured && !account.verified) verifiedOnlyError(request).map(r => Some(r))
        else immediate(None)
      }
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  /**
    * Fetch, if available, the user's profile, ensuring that:
    *  - the site is not read-only
    *  - the site is not in maintenance mode
    *  - they are allowed in this controller
    */
  protected def OptionalAccountAction: ActionBuilder[OptionalAccountRequest, AnyContent] =
    GenericOptionalAccountFunction andThen
      MaintenanceFilter andThen
      IpFilter andThen
      ReadOnlyTransformer andThen
      EmbedTransformer andThen
      AllowedFilter


  protected def GenericOptionalAccountFunction: CoreActionBuilder[OptionalAccountRequest, AnyContent] =
    new CoreActionBuilder[OptionalAccountRequest, AnyContent] {
      def invokeBlock[A](request: Request[A], block: OptionalAccountRequest[A] => Future[Result]): Future[Result] = {
        authHandler.restoreAccount(request).recover({
          case _ => None -> identity[Result] _
        })(controllerComponents.executionContext).flatMap({
          case (user, cookieUpdater) => block(OptionalAccountRequest[A](user, request))
            .map(cookieUpdater)(controllerComponents.executionContext)
        })(controllerComponents.executionContext)
      }
    }


  /**
    * Fetch the profile in addition to the account
    */
  protected def OptionalUserAction: ActionBuilder[OptionalUserRequest, AnyContent] = OptionalAccountAction andThen FetchProfile

  /**
    * Ensure that a user is present
    */
  protected def WithUserAction: ActionBuilder[WithUserRequest, AnyContent] =
    OptionalUserAction andThen new ActionRefiner[OptionalUserRequest, WithUserRequest] {
      override protected def refine[A](request: OptionalUserRequest[A]): Future[Either[Result, WithUserRequest[A]]] = {
        request.userOpt match {
          case None => authenticationFailed(request).map(r => Left(r))
          case Some(profile) => immediate(Right(WithUserRequest(profile, request)))
        }
      }

      override protected def executionContext: ExecutionContext = controllerComponents.executionContext
    }

  /**
    * Ensure that a user a given permission on a given content type
    *
    * @param permissionType the permission type
    * @param contentType    the content type
    */
  protected def WithContentPermissionAction(permissionType: PermissionType.Value, contentType: ContentTypes.Value): ActionBuilder[WithUserRequest, AnyContent] =
    WithUserAction andThen new ActionFilter[WithUserRequest] {
      override protected def filter[A](request: WithUserRequest[A]): Future[Option[Result]] = {
        if (request.user.hasPermission(contentType, permissionType)) immediate(None)
        else authorizationFailed(request, request.user).map(r => Some(r))
      }

      override protected def executionContext: ExecutionContext = controllerComponents.executionContext
    }

  /**
    * Check the user belongs to a given group.
    */
  protected def MustBelongTo(groupId: String): ActionBuilder[WithUserRequest, AnyContent] = WithUserAction andThen new ActionFilter[WithUserRequest] {
    protected def filter[A](request: WithUserRequest[A]): Future[Option[Result]] = {
      if (request.user.isAdmin || request.user.allGroups.exists(_.id == groupId)) immediate(None)
      else authorizationFailed(request, request.user).map(r => Some(r))
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  /**
    * Check the user is an administrator to access this request
    */
  protected def AdminAction: ActionBuilder[WithUserRequest, AnyContent] = WithUserAction andThen new ActionFilter[WithUserRequest] {
    protected def filter[A](request: WithUserRequest[A]): Future[Option[Result]] = {
      if (request.user.isAdmin) immediate(None)
      else authorizationFailed(request, request.user).map(r => Some(r))
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  /**
    * Validate an incoming websocket request.
    */
  def AuthenticatedWebsocket[I, O](f: UserProfile => Boolean)(a: RequestHeader => Flow[I, O, _])
                                  (implicit transformer: MessageFlowTransformer[I, O]): WebSocket =
    WebSocket.acceptOrResult[I, O] { implicit request =>
      authHandler.restoreAccount(request).flatMap {
        case (Some(account), _) => fetchProfile(account).flatMap {
          case Some(prof) if f(prof) => immediate(Right(a(request)))
          // user doesn't have a profile, or fails the test
          case _ => authenticationFailed(request).map(r => Left(r))
        }
        case _ => authenticationFailed(request).map(r => Left(r))
      }
    }

  /**
    * A websocket that requires administrator privileges.
    */
  def AdminWebsocket[I, O](a: RequestHeader => Flow[I, O, _])(implicit transformer: MessageFlowTransformer[I, O]): WebSocket =
    AuthenticatedWebsocket(_.isAdmin)(a)
}
