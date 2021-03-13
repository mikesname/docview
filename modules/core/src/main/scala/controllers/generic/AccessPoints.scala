package controllers.generic

import models.{AccessPointF, DescribedModel, EntityType, Link, LinkF, Resource}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}


trait AccessPoints[MT <: DescribedModel] extends Read[MT] {

  // NB: This doesn't work when placed within the function scope
  // should probably check if a bug has been reported.
  case class Target(id: String, `type`: EntityType.Value)

  case class LinkItem(accessPoint: AccessPointF, link: Option[LinkF], target: Option[Target])

  /**
    * FIXME: Address this festering sore!
    *
    * Translate an item's access points and accompanying links into a more
    * easily consumable format. We start out with a list of links belonging
    * to the item. Then, for each description we check if that link's body is
    * an access point (with, say, descriptive text.) We then sort the access
    * points for each description according to their type and return them in
    * a map along with the accompanying link data (if any.) The result looks
    * something like:
    *
    * [ {
    * "id" : "e6410af3-c45e-4649-9b5e-6753b2aa1156",
    * "data" : [ {
    *   "type" : "creator",
    *   "data" : [ ]
    * }, {
    *   "type" : "person",
    *   "data" : [ ]
    * }, {
    *   "type" : "family",
    *   "data" : [ ]
    * }, {
    *   "type" : "corporateBody",
    *   "data" : [ ]
    * }, {
    *   "type" : "subject",
    *   "data" : [ ]
    * }, {
    *   "type" : "place",
    *   "data" : [ {
    *     "accessPoint" : {
    *       "isA" : "AccessPoint",
    *       "id" : "07238843-5b75-4af0-80b6-67c17c285686",
    *       "accessPointType" : "placeAccess",
    *       "name" : "Wiener Library Archives",
    *       "description" : ""
    *     },
    *     "link" : {
    *       "isA" : "Link",
    *       "id" : "1fdb1ece-8202-496d-a01d-33cdddede00f",
    *       "linkType" : "associative",
    *       "description" : ""
    *     },
    *     "target" : {
    *       "id" : "il-002821",
    *       "type" : "Repository"
    *     }
    *   } ]
    * }, {
    *   "type" : "genre",
    *   "data" : [ ]
    * } ]
    * } ]
    *
    */
  def getAccessPointsJson(id: String)(implicit rs: Resource[MT]): Action[AnyContent] =
    OptionalUserAction.async { implicit request =>
      for (item <- userDataApi.get(id); links <- userDataApi.links[Link](id)) yield {
        implicit val accessPointFormat = Json.format[AccessPointF]
        implicit val linkFormat = Json.format[LinkF]
        implicit val targetWrites = Json.format[Target]
        implicit val itemWrites = Json.format[LinkItem]

        val list = item.data.descriptions.map { desc =>
          val accessPointTypes = AccessPointF.AccessPointType.values.toList.map { apt =>
            val apTypes = desc.accessPoints.filter(_.accessPointType == apt).map { ap =>
              val linkOpt = links.find(_.bodies.exists(b => b.data.id == ap.id))
              LinkItem(
                ap,
                linkOpt.map(_.data),
                linkOpt.flatMap(l => l.opposingTarget(item).map(t => Target(t.id, t.isA)))
              )
            }
            Map("type" -> Json.toJson(apt.toString), "data" -> Json.toJson(apTypes))
          }
          Map("id" -> Json.toJson(desc.id), "data" -> Json.toJson(accessPointTypes))
        }
        Ok(Json.toJson(list))
      }
    }
}
