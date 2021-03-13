package controllers.generic

import models.{ContentType, DeleteChildrenOptions, Holder, Model, PermissionType, Readable, UserProfile}
import play.api.data.Form
import play.api.mvc._
import services.data.HierarchyError
import utils.{Page, PageParams}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


/**
  * Controller trait which handles logic for deleting an item's sub-items
  * and confirming the user's actions.
  */
trait DeleteChildren[CMT <: Model, MT <: Model with Holder[CMT]] extends Read[MT] with Write {

  case class CheckDeleteChildrenRequest[A](
    item: MT,
    children: Page[CMT],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class DeleteChildrenRequest[A](
    item: MT,
    formOrIds: Either[(Form[DeleteChildrenOptions], Page[CMT]), Seq[String]],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def CheckDeleteChildrenAction(itemId: String, paging: PageParams)(implicit ct: ContentType[MT], rs: Readable[CMT]): ActionBuilder[CheckDeleteChildrenRequest, AnyContent] =
    WithItemPermissionAction(itemId, PermissionType.Delete) andThen new CoreActionTransformer[ItemPermissionRequest, CheckDeleteChildrenRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[CheckDeleteChildrenRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        userDataApi.children[MT, CMT](itemId, paging).map { page =>
          CheckDeleteChildrenRequest(req.item, page, req.userOpt, req)
        }
      }
    }

  protected def DeleteChildrenAction(itemId: String, paging: PageParams)(implicit ct: ContentType[MT], rs: Readable[CMT]): ActionBuilder[DeleteChildrenRequest, AnyContent] =
    CheckDeleteChildrenAction(itemId, paging) andThen new CoreActionTransformer[CheckDeleteChildrenRequest, DeleteChildrenRequest] {
      def transform[A](request: CheckDeleteChildrenRequest[A]): Future[DeleteChildrenRequest[A]] = {
        implicit val req: CheckDeleteChildrenRequest[A] = request
        val f = DeleteChildrenOptions.form.bindFromRequest()
        f.fold(
          errorForm => immediate(
            DeleteChildrenRequest(request.item, Left(errorForm, req.children), request.userOpt, request.request)),
          data => {
            (for {
              ids <- userDataApi.deleteChildren[MT](itemId, data.all, logMsg = getLogMessage)
            } yield DeleteChildrenRequest(request.item, Right(ids), request.userOpt, request)) recover {
              case e: HierarchyError =>
                val filledForm = f.withGlobalError(e.error)
                DeleteChildrenRequest(request.item, Left(filledForm, req.children), request.userOpt, request)
            }
          }
        )
      }
    }
}
