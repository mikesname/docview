package controllers.generic

import models.{ContentType, EventType, Model, PermissionType}
import play.api.mvc._

import scala.concurrent.Future

/**
  * Controller trait for deleting entities.
  */
trait Delete[MT <: Model] extends Read[MT] with Write {

  protected def CheckDeleteAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Delete)

  private[generic] def DeleteTransformer(id: String)(implicit ct: ContentType[MT]) =
    new CoreActionTransformer[ItemPermissionRequest, OptionalUserRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[OptionalUserRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        for {
          _ <- itemLifecycle.preSave(Some(id), Some(req.item), req.item.data, EventType.deletion)
          _ <- userDataApi.delete(id, logMsg = getLogMessage)
          _ <- itemLifecycle.postSave(id, req.item, EventType.deletion)
        } yield OptionalUserRequest(request.userOpt, request)
      }
    }

  protected def DeleteAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[OptionalUserRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Delete) andThen DeleteTransformer(id)
}
