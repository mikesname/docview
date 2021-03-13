package controllers.generic

import models.{Accessor, ContentType, EntityType, ItemPermissionSet, PermissionGrant, PermissionType, UserProfile}
import play.api.mvc._
import utils.{Page, PageParams}

import scala.concurrent.Future

/**
  * Trait for setting permissions on an individual item.
  */
trait ItemPermissions[MT] extends Visibility[MT] {

  case class ItemPermissionGrantRequest[A](
    item: MT,
    permissionGrants: Page[PermissionGrant],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class SetItemPermissionRequest[A](
    item: MT,
    accessor: Accessor,
    itemPermissions: ItemPermissionSet,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def WithGrantPermission(id: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Grant)

  protected def PermissionGrantAction(id: String, paging: PageParams)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionGrantRequest, AnyContent] =
    WithGrantPermission(id) andThen new CoreActionTransformer[ItemPermissionRequest, ItemPermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionGrantRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        userDataApi.itemPermissionGrants[PermissionGrant](id, paging).map { permGrants =>
          ItemPermissionGrantRequest(request.item, permGrants, request.userOpt, request)
        }
      }
    }

  protected def EditItemPermissionsAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[VisibilityRequest, AnyContent] =
    WithGrantPermission(id) andThen EditVisibilityAction(id)

  protected def CheckUpdateItemPermissionsAction(id: String, userType: EntityType.Value, userId: String)(
    implicit ct: ContentType[MT]): ActionBuilder[SetItemPermissionRequest, AnyContent] =
    WithGrantPermission(id) andThen new CoreActionTransformer[ItemPermissionRequest, SetItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetItemPermissionRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val accessorF = userDataApi.get[Accessor](Accessor.resourceFor(userType), userId)
        val permsF = userDataApi.itemPermissions(userId, ct.contentType, id)
        for {
          accessor <- accessorF
          perms <- permsF
        } yield SetItemPermissionRequest(request.item, accessor, perms, request.userOpt, request)
      }
    }

  protected def getData[A](request: Request[A]): Option[Map[String, Seq[String]]] = request.body match {
    case any: AnyContentAsFormUrlEncoded => Some(any.asFormUrlEncoded.getOrElse(Map.empty))
    case json: AnyContentAsJson => Some(json.asJson.flatMap(_.asOpt[Map[String, Seq[String]]]).getOrElse(Map.empty))
    case _ => None
  }

  protected def UpdateItemPermissionsAction(id: String, userType: EntityType.Value, userId: String)(
    implicit ct: ContentType[MT]): ActionBuilder[SetItemPermissionRequest, AnyContent] =
    WithGrantPermission(id) andThen new CoreActionTransformer[ItemPermissionRequest, SetItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetItemPermissionRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val data = getData(request).getOrElse(Map.empty)
        val perms: Seq[String] = data.getOrElse(ct.contentType.toString, Seq.empty)
        for {
          accessor <- userDataApi.get[Accessor](Accessor.resourceFor(userType), userId)
          perms <- userDataApi.setItemPermissions(userId, ct.contentType, id, perms)
        } yield SetItemPermissionRequest(request.item, accessor, perms, request.userOpt, request)
      }
    }
}

