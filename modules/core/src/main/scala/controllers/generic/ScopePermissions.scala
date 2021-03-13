package controllers.generic

import models.{Accessor, ContentType, ContentTypes, EntityType, GlobalPermissionSet, PermissionGrant, UserProfile}
import play.api.mvc._
import utils.{Page, PageParams}

import scala.concurrent.Future

/**
  * Trait for setting visibility on any AccessibleEntity.
  */
trait ScopePermissions[MT] extends ItemPermissions[MT] {

  protected val targetContentTypes: Seq[ContentTypes.Value]

  case class ScopePermissionGrantRequest[A](
    item: MT,
    permissionGrants: Page[PermissionGrant],
    scopePermissionGrants: Page[PermissionGrant],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class SetScopePermissionRequest[A](
    item: MT,
    accessor: Accessor,
    scopePermissions: GlobalPermissionSet,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def ScopePermissionGrantAction(id: String, itemPaging: PageParams, scopePaging: PageParams)(implicit ct: ContentType[MT]): ActionBuilder[ScopePermissionGrantRequest, AnyContent] =
    WithGrantPermission(id) andThen new CoreActionTransformer[ItemPermissionRequest, ScopePermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ScopePermissionGrantRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        for {
          permGrants <- userDataApi.itemPermissionGrants[PermissionGrant](id, itemPaging)
          scopeGrants <- userDataApi.scopePermissionGrants[PermissionGrant](id, scopePaging)
        } yield ScopePermissionGrantRequest(request.item, permGrants, scopeGrants, request.userOpt, request)
      }
    }

  protected def CheckUpdateScopePermissionsAction(id: String, userType: EntityType.Value, userId: String)(
    implicit ct: ContentType[MT]): ActionBuilder[SetScopePermissionRequest, AnyContent] =
    WithGrantPermission(id) andThen new CoreActionTransformer[ItemPermissionRequest, SetScopePermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetScopePermissionRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val accessorF = userDataApi.get[Accessor](Accessor.resourceFor(userType), userId)
        val permsF = userDataApi.scopePermissions(userId, id)
        for {
          accessor <- accessorF
          perms <- permsF
        } yield SetScopePermissionRequest(request.item, accessor, perms, request.userOpt, request)
      }
    }


  protected def UpdateScopePermissionsAction(id: String, userType: EntityType.Value, userId: String)(
    implicit ct: ContentType[MT]): ActionBuilder[SetScopePermissionRequest, AnyContent] =
    WithGrantPermission(id) andThen new CoreActionTransformer[ItemPermissionRequest, SetScopePermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetScopePermissionRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val data = getData(request).getOrElse(Map.empty)
        val perms: Map[String, Seq[String]] = targetContentTypes.map { ct =>
          ct.toString -> data.getOrElse(ct.toString, Seq.empty)
        }.toMap
        for {
          accessor <- userDataApi.get[Accessor](Accessor.resourceFor(userType), userId)
          perms <- userDataApi.setScopePermissions(userId, id, perms)
        } yield SetScopePermissionRequest(request.item, accessor, perms, request.userOpt, request)
      }
    }
}

