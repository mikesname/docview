package controllers.generic

import play.api.mvc._
import play.api.data.Form
import models.{ContentType, EventType, Model, ModelData, PermissionType, Persistable, UserProfile, UsersAndGroups, Writable}
import forms._
import services.data.{DataHelpers, ValidationError}

import scala.concurrent.Future

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Repository and
 * DocumentaryUnit itself.
 */
trait Creator[CMT <: Model{type T <: ModelData with Persistable}, MT <: Model] extends Read[MT] with Write {

  protected def dataHelpers: DataHelpers

  case class NewChildRequest[A](
    item: MT,
    usersAndGroups: UsersAndGroups,
    userOpt: Option[UserProfile],
    request: Request[A]
    ) extends WrappedRequest[A](request)
  with WithOptionalUser

  private[generic] def NewChildTransformer = new CoreActionTransformer[ItemPermissionRequest, NewChildRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[NewChildRequest[A]] = {
      dataHelpers.getUserAndGroupList.map { usersAndGroups =>
        NewChildRequest(request.item, usersAndGroups, request.userOpt, request)
      }
    }
  }

  protected def NewChildAction(itemId: String)(implicit ct: ContentType[MT], cct: ContentType[CMT]): ActionBuilder[NewChildRequest, AnyContent] =
    WithParentPermissionAction(itemId, PermissionType.Create, cct.contentType) andThen NewChildTransformer

  case class CreateChildRequest[A](
     item: MT,
     formOrItem: Either[(Form[CMT#T],Form[Seq[String]], UsersAndGroups),CMT],
     userOpt: Option[UserProfile],
     request: Request[A]
     ) extends WrappedRequest[A](request)
  with WithOptionalUser

  private[generic] def CreateChildTransformer(id: String, form: Form[CMT#T], extraParams: ExtraParams = defaultExtra)(implicit ct: ContentType[MT], fmt: Writable[CMT#T], cct: ContentType[CMT]) =
    new CoreActionTransformer[ItemPermissionRequest, CreateChildRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[CreateChildRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val extra = extraParams.apply(request.request)
        val visForm = visibilityForm.bindFromRequest()
        form.bindFromRequest().fold(
          errorForm => dataHelpers.getUserAndGroupList.map { usersAndGroups =>
            CreateChildRequest(request.item, Left((errorForm, visForm, usersAndGroups)), request.userOpt, request.request)
          },
          citem => {
            val accessors = visForm.value.getOrElse(Nil)
            (for {
              pre <- itemLifecycle.preSave(None, None, citem, EventType.creation)
              saved <- userDataApi.createInContext[MT, CMT#T, CMT](id, pre, accessors, params = extra, logMsg = getLogMessage)
              post <- itemLifecycle.postSave(saved.id, saved, EventType.creation)
            } yield CreateChildRequest(request.item, Right(post), request.userOpt, request)) recoverWith {
              case ValidationError(errorSet) =>
                val filledForm = citem.getFormErrors(errorSet, form.fill(citem))
                dataHelpers.getUserAndGroupList.map { usersAndGroups =>
                  CreateChildRequest(request.item, Left((filledForm, visForm, usersAndGroups)), request.userOpt, request)
                }
            }
          }
        )
      }
    }

  protected def CreateChildAction(id: String, form: Form[CMT#T], extraParams: ExtraParams = defaultExtra)(implicit ct: ContentType[MT], fmt: Writable[CMT#T], cct: ContentType[CMT]): ActionBuilder[CreateChildRequest, AnyContent] =
    WithParentPermissionAction(id, PermissionType.Create, cct.contentType) andThen CreateChildTransformer(id, form, extraParams)
  
  
  /**
   * Functor to extract arbitrary DAO params from a request...
   */
  type ExtraParams = Request[_] => Map[String,Seq[String]]

  protected def defaultExtra: ExtraParams = (_: Request[_]) => Map.empty
}
