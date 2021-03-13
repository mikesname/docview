package controllers.generic

import models.{ContentType, Described, DescribedModel, Description, ModelData, PermissionType, Persistable, UserProfile}
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
  * Provides helpers for managing items that can have multiple descriptions.
  */
trait Descriptions[MT <: DescribedModel{type T <: ModelData with Described{type D <: Description with Persistable}}] extends Write {

  this: Read[MT] =>

  case class DescriptionRequest[A](
    item: MT,
    description: MT#T#D,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser


  protected def WithDescriptionAction(id: String, did: String)(implicit ct: ContentType[MT]): ActionBuilder[DescriptionRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update) andThen new CoreActionRefiner[ItemPermissionRequest, DescriptionRequest] {
      override protected def refine[A](request: ItemPermissionRequest[A]): Future[Either[Result, DescriptionRequest[A]]] = {
        request.item.data.description(did) match {
          case Some(d) => immediate(Right(DescriptionRequest(request.item, d, request.userOpt, request)))
          case None => notFoundError(request).map(r => Left(r))
        }
      }
    }
}

