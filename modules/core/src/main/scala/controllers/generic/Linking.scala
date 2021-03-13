package controllers.generic

import models.{ContentType, _}
import play.api.data.Form
import play.api.libs.json._
import play.api.mvc._
import utils.{EnumUtils, PageParams}
import services.search.{SearchHit, _}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
  * Class representing an access point link.
  *
  * @param target      id of the destination item
  * @param `type`      type field, i.e. associative
  * @param description descrioption of link
  */
case class AccessPointLink(
  target: String,
  `type`: Option[LinkF.LinkType.Value] = None,
  description: Option[String] = None
)

object AccessPointLink {
  // handlers for creating/listing/deleting links via JSON
  implicit val linkTypeFormat: Format[LinkF.LinkType.Value] = EnumUtils.enumFormat(LinkF.LinkType)
  implicit val accessPointTypeFormat: Format[AccessPointF.AccessPointType.Value] = utils.EnumUtils.enumFormat(AccessPointF.AccessPointType)
  implicit val accessPointFormat: Format[AccessPointF] = Json.format[AccessPointF]
  implicit val accessPointLinkReads: Format[AccessPointLink] = Json.format[AccessPointLink]
}


/**
  * Trait for setting visibility on any AccessibleEntity.
  *
  * @tparam MT the entity's build class
  */
trait Linking[MT <: Model] extends Read[MT] with Search {

  // This is used to send the link data back to JSON endpoints...
  private implicit val linkFormatForClient: Format[LinkF] = Json.format[LinkF]

  case class LinkSelectRequest[A](
    item: MT,
    searchResult: SearchResult[(Model, SearchHit)],
    entityType: EntityType.Value,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def LinkSelectAction(id: String, toType: EntityType.Value, params: SearchParams, paging: PageParams, facets: FacetBuilder = emptyFacets)(implicit ct: ContentType[MT]): ActionBuilder[LinkSelectRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Annotate) andThen new CoreActionTransformer[ItemPermissionRequest, LinkSelectRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[LinkSelectRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        find[Model](
          paging = paging,
          facetBuilder = facets,
          params = params.copy(excludes = params.excludes :+ id),
          entities = Seq(toType)
        ).map { r =>
          LinkSelectRequest(request.item, r, toType, request.userOpt, request)
        }
      }
    }

  case class LinkItemsRequest[A](
    from: MT,
    to: Model,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def LinkAction(id: String, toType: EntityType.Value, to: String)(
    implicit ct: ContentType[MT]): ActionBuilder[LinkItemsRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Annotate) andThen new CoreActionTransformer[ItemPermissionRequest, LinkItemsRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[LinkItemsRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        userDataApi.get[Model](Model.resourceFor(toType), to).map { toItem =>
          LinkItemsRequest(request.item, toItem, request.userOpt, request)
        }
      }
    }

  case class CreateLinkRequest[A](
    from: MT,
    formOrLink: Either[(Model, Form[LinkF]), Link],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def CreateLinkAction(id: String, toType: EntityType.Value, to: String, directional: Boolean = false)(
    implicit ct: ContentType[MT]): ActionBuilder[CreateLinkRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Annotate) andThen new CoreActionTransformer[ItemPermissionRequest, CreateLinkRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[CreateLinkRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        Link.form.bindFromRequest().fold(
          errorForm => {
            // oh dear, we have an error...
            userDataApi.get[Model](Model.resourceFor(toType), to).map { toItem =>
              CreateLinkRequest(request.item, Left((toItem, errorForm)), request.userOpt, request)
            }
          },
          link => userDataApi.linkItems[MT, Link, LinkF](id, to, link, directional = directional).map { link =>
            CreateLinkRequest(request.item, Right(link), request.userOpt, request)
          }
        )
      }
    }

  /**
    * Create a link, via Json, for any arbitrary two objects, via an access point.
    */
  def createLink(id: String, apid: String)(implicit ct: ContentType[MT]): Action[JsValue] =
    WithItemPermissionAction(id, PermissionType.Annotate).async(parsers.json) { implicit request =>
      request.body.validate[AccessPointLink].fold(
        errors => immediate(BadRequest(JsError.toJson(errors))),
        ann => {
          val link = new LinkF(id = None, linkType = LinkF.LinkType.Associative, description = ann.description)
          userDataApi.linkItems[MT, Link, LinkF](id, ann.target, link, Some(apid)).map { ann =>
            Created(Json.toJson(ann.data))
          }
        }
      )
    }

  /**
    * Create a link, via Json, for any arbitrary two objects, via an access point.
    */
  def createAccessPoint(id: String, did: String)(implicit ct: ContentType[MT]): Action[JsValue] =
    WithItemPermissionAction(id, PermissionType.Update).async(parsers.json) { implicit request =>
      request.body.validate[AccessPointF](AccessPointLink.accessPointFormat).fold(
        errors => immediate(BadRequest(JsError.toJson(errors))),
        ap => userDataApi.createAccessPoint(id, did, ap).map { ann =>
          Created(Json.toJson(ann)(Json.format[AccessPointF]))
        }
      )
    }

  /**
    * Get the link, if any, for a document and an access point.
    */
  def getLink(id: String, apid: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    userDataApi.links[Link](id).map { links =>
      val linkOpt = links.find(link => link.bodies.exists(b => b.data.id.contains(apid)))
      val res = for {
        link <- linkOpt
        target <- link.opposingTarget(id)
      } yield new AccessPointLink(
        target = target.id,
        `type` = Some(link.data.linkType),
        description = link.data.description
      )
      Ok(Json.toJson(res))
    }
  }

  /**
    * Delete an access point by ID.
    */
  def deleteAccessPoint(id: String, did: String, accessPointId: String)(implicit ct: ContentType[MT]): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
      userDataApi.deleteAccessPoint(id, did, accessPointId).map { ok =>
        Ok(Json.toJson(true))
      }
    }

  /**
    * Delete a link.
    */
  def deleteLink(id: String, linkId: String)(implicit ct: ContentType[MT]): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Annotate).async { implicit request =>
      userDataApi.delete[Link](linkId).map { ok =>
        Ok(Json.toJson(true))
      }
    }

  /**
    * Delete a link and an access point in one go.
    */
  def deleteLinkAndAccessPoint(id: String, did: String, accessPointId: String, linkId: String)(
    implicit ct: ContentType[MT]): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Annotate).async { implicit request =>
      for {
        _ <- userDataApi.delete[Link](linkId)
        _ <- userDataApi.deleteAccessPoint(id, did, accessPointId)
      } yield Ok(Json.toJson(true))
    }
}

