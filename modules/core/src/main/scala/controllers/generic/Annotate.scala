package controllers.generic

import models._
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._

import scala.concurrent.Future.{successful => immediate}

/**
  * Helper actions for controllers that can annotate item types.
  *
  * @tparam MT the entity's generic type
  */
trait Annotate[MT] extends Read[MT] {

  /**
    * Create an annotation via Ajax... note that this is an action
    * in itself and not a builder.
    *
    * @param id The item's id
    * @return
    */
  def createAnnotationJsonPost(id: String): Action[JsValue] = Action.async(parsers.json) { request =>
    request.body.validate[AnnotationF](AnnotationF.Converter.clientFormat).fold(
      errors => immediate(BadRequest(JsError.toJson(errors))),
      ap => {
        // NB: No checking of permissions here - we're going to depend
        // on the server for that
        OptionalUserAction.async { implicit request =>
          userDataApi.createAnnotation[Annotation, AnnotationF](id, ap).map { ann =>
            Created(Json.toJson(ann.data)(AnnotationF.Converter.clientFormat))
          }
        }(request.map(js => AnyContentAsEmpty))
      }
    )
  }
}

