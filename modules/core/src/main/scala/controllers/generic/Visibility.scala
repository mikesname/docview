package controllers.generic

import models.{ContentType, PermissionType, UserProfile, UsersAndGroups}
import play.api.mvc._
import services.data.DataHelpers

import scala.concurrent.Future

/**
  * Trait for setting visibility on any item.
  */
trait Visibility[MT] extends Read[MT] {

  protected def dataHelpers: DataHelpers

  case class VisibilityRequest[A](
    item: MT,
    usersAndGroups: UsersAndGroups,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def EditVisibilityAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[VisibilityRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update) andThen new CoreActionTransformer[ItemPermissionRequest, VisibilityRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[VisibilityRequest[A]] = {
        dataHelpers.getUserAndGroupList.map { usersAndGroups =>
          VisibilityRequest(request.item, usersAndGroups, request.userOpt, request)
        }
      }
    }

  protected def UpdateVisibilityAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update) andThen new CoreActionTransformer[ItemPermissionRequest, ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val data = forms.visibilityForm.bindFromRequest().value.getOrElse(Nil)
        userDataApi.setVisibility(id, data).map { newItem =>
          ItemPermissionRequest(newItem, request.userOpt, request)
        }
      }
    }
}

