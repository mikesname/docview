package controllers.generic

import controllers.base.CoreActionBuilders
import models.{ContentTypes, _}
import play.api.Logger
import play.api.mvc.{Result, _}
import services.data.ItemNotFound
import utils.{Page, PageParams, RangePage, RangeParams}

import scala.concurrent.Future

/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the [[Accessible]] trait.
 *
 * @tparam MT Meta-model
 */
trait Read[MT] extends CoreActionBuilders {

  private def logger = Logger(getClass)

  case class ItemPermissionRequest[A](
    item: MT,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  case class ItemMetaRequest[A](
    item: MT,
    annotations: Page[Annotation],
    links: Page[Link],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  case class ItemPageRequest[A](
    page: Page[MT],
    params: PageParams,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  case class ItemHistoryRequest[A](
    item: MT,
    page: RangePage[Seq[SystemEvent]],
    params: RangeParams,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  case class ItemVersionsRequest[A](
    item: MT,
    page: Page[Version],
    params: PageParams,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  private def WithPermissionFilter(perm: PermissionType.Value, contentType: ContentTypes.Value) = new CoreActionFilter[ItemPermissionRequest] {
    override protected def filter[A](request: ItemPermissionRequest[A]): Future[Option[Result]] = {
      request.userOpt.map { user =>
        if (user.hasPermission(contentType, perm)) Future.successful(None)
        else authorizationFailed(request, user).map(r => Some(r))
      }.getOrElse(authenticationFailed(request).map(r => Some(r)))
    }
  }

  private def WithItemPermissionFilter(perm: PermissionType.Value)(implicit ct: ContentType[MT]) =
    WithPermissionFilter(perm, ct.contentType)

  protected def ItemPermissionAction(itemId: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    OptionalUserAction andThen new CoreActionRefiner[OptionalUserRequest, ItemPermissionRequest] {
      private def transform[A](input: OptionalUserRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val user: Option[UserProfile] = input.userOpt
        input.userOpt.map { profile =>
          val itemF = userDataApi.get[MT](itemId)
          val scopedPermsF = userDataApi.scopePermissions(profile.id, itemId)
          val permsF = userDataApi.itemPermissions(profile.id, ct.contentType, itemId)
          for {
            item <- itemF
            scopedPerms <- scopedPermsF
            perms <- permsF
            newProfile = profile.copy(itemPermissions = Some(perms), globalPermissions = Some(scopedPerms))
          } yield ItemPermissionRequest[A](item, Some(newProfile), input)
        }.getOrElse {
          for {
            item <- userDataApi.get[MT](itemId)
          } yield ItemPermissionRequest[A](item, None, input)
        }
      }

      override protected def refine[A](request: OptionalUserRequest[A]): Future[Either[Result, ItemPermissionRequest[A]]] = {
        transform(request).map(r => Right(r)).recoverWith {
          case _: ItemNotFound => notFoundError(request, msg = Some(itemId)).map(r => Left(r))
        }
      }
    }

  protected def WithItemPermissionAction(itemId: String, perm: PermissionType.Value)(
    implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    ItemPermissionAction(itemId) andThen WithItemPermissionFilter(perm)

  protected def WithParentPermissionAction(itemId: String, perm: PermissionType.Value, contentType: ContentTypes.Value)(
    implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    ItemPermissionAction(itemId) andThen WithPermissionFilter(perm, contentType)

  protected def ItemMetaAction(itemId: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemMetaRequest, AnyContent] =
    ItemPermissionAction(itemId) andThen new CoreActionTransformer[ItemPermissionRequest, ItemMetaRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[ItemMetaRequest[A]] = {
        implicit val user: Option[UserProfile] = request.userOpt
        val annotationsF = userDataApi.annotations[Annotation](itemId)
        val linksF = userDataApi.links[Link](itemId)
        for {
          annotations <- annotationsF
          links <- linksF
        } yield ItemMetaRequest[A](request.item, annotations, links, request.userOpt, request)
      }
    }

  protected def ItemPageAction(paging: PageParams)(implicit rs: Resource[MT]): ActionBuilder[ItemPageRequest, AnyContent] =
    OptionalUserAction andThen new CoreActionTransformer[OptionalUserRequest, ItemPageRequest] {
      def transform[A](input: OptionalUserRequest[A]): Future[ItemPageRequest[A]] = {
        implicit val user: Option[UserProfile] = input.userOpt
        for {
          page <- userDataApi.list[MT](paging)
        } yield ItemPageRequest[A](page, paging, input.userOpt, input)
      }
    }

  protected def ItemHistoryAction(itemId: String, range: RangeParams)(implicit ct: ContentType[MT]): ActionBuilder[ItemHistoryRequest, AnyContent] =
    ItemPermissionAction(itemId) andThen new CoreActionTransformer[ItemPermissionRequest,ItemHistoryRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemHistoryRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val historyF: Future[RangePage[Seq[SystemEvent]]] = userDataApi.history[SystemEvent](itemId, range)
        for {
          events <- historyF
        } yield ItemHistoryRequest(request.item, events, range, request.userOpt, request)
      }
    }

  protected def ItemVersionsAction(itemId: String, paging: PageParams)(implicit ct: ContentType[MT]): ActionBuilder[ItemVersionsRequest, AnyContent] =
    ItemPermissionAction(itemId) andThen new CoreActionTransformer[ItemPermissionRequest, ItemVersionsRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemVersionsRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val versionsF: Future[Page[Version]] = userDataApi.versions[Version](itemId, paging)
        for {
          versions <- versionsF
        } yield ItemVersionsRequest(request.item, versions, paging, request.userOpt, request)
      }
    }
}
