package controllers.generic

import models.{Accessor, ContentType, ContentTypes, GlobalPermissionSet, _}
import play.api.mvc._
import utils.{Page, PageParams}

import scala.concurrent.Future

/**
  * Trait for managing permissions on Accessor models that can have permissions assigned to them.
  */
trait PermissionHolder[MT <: Accessor] extends Read[MT] {

  case class HolderPermissionGrantRequest[A](
    item: MT,
    permissionGrants: Page[PermissionGrant],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class GlobalPermissionSetRequest[A](
    item: MT,
    permissions: GlobalPermissionSet,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class PermissionGrantRequest[A](
    item: MT,
    permissionGrant: PermissionGrant,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser


  protected def GrantListAction(id: String, paging: PageParams)(implicit ct: ContentType[MT]): ActionBuilder[HolderPermissionGrantRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new CoreActionTransformer[ItemPermissionRequest, HolderPermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[HolderPermissionGrantRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        userDataApi.permissionGrants[PermissionGrant](id, paging).map { perms =>
          HolderPermissionGrantRequest(request.item, perms, request.userOpt, request)
        }
      }
    }

  private def getData[A](request: Request[A]): Option[Map[String, Seq[String]]] = request.body match {
    case any: AnyContentAsFormUrlEncoded => Some(any.asFormUrlEncoded.getOrElse(Map.empty))
    case json: AnyContentAsJson => Some(json.asJson.flatMap(_.asOpt[Map[String, Seq[String]]]).getOrElse(Map.empty))
    case _ => None
  }

  protected def CheckGlobalPermissionsAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[GlobalPermissionSetRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new CoreActionTransformer[ItemPermissionRequest, GlobalPermissionSetRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[GlobalPermissionSetRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        userDataApi.globalPermissions(id).map { perms =>
          GlobalPermissionSetRequest(request.item, perms, request.userOpt, request)
        }
      }
    }

  protected def SetGlobalPermissionsAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[GlobalPermissionSetRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new CoreActionTransformer[ItemPermissionRequest, GlobalPermissionSetRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[GlobalPermissionSetRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val data = getData(request).getOrElse(Map.empty)
        val perms: Map[String, Seq[String]] = ContentTypes.values.toSeq.map { ct =>
          ct.toString -> data.getOrElse(ct.toString, Seq.empty)
        }.toMap
        userDataApi.setGlobalPermissions(id, perms).map { perms =>
          GlobalPermissionSetRequest(request.item, perms, request.userOpt, request)
        }
      }
    }

  protected def CheckRevokePermissionAction(id: String, permId: String)(implicit ct: ContentType[MT]): ActionBuilder[PermissionGrantRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new CoreActionTransformer[ItemPermissionRequest, PermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[PermissionGrantRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        userDataApi.get[PermissionGrant](permId).map { perm =>
          PermissionGrantRequest(request.item, perm, request.userOpt, request)
        }
      }
    }

  protected def RevokePermissionAction(id: String, permId: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new CoreActionTransformer[ItemPermissionRequest, ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        userDataApi.delete[PermissionGrant](permId).map(_ => request)
      }
    }
}
