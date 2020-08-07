package controllers.base

import services.cypher.{CypherResult, CypherService}
import controllers.generic.Search
import models.{Model, VirtualUnit}
import play.api.Logger
import play.api.mvc.RequestHeader
import services.search.SearchConstants._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


/**
 * Helpers for searching virtual collections
 */
trait SearchVC {
  this: Search =>

  protected def cypher: CypherService

  private def logger = Logger(getClass)

  /**
    * Filters for searching a virtual collection, depending on
    * whether or not there is a current query. If no query is
    * given the filters will fetch items at the child level only.
    * Otherwise they will return filters for items at all depths
    * in the virtual collection.
    */
  protected def vcSearchFilters(item: Model)(implicit request: RequestHeader): Future[Map[String,Any]] =
    if (!hasActiveQuery(request)) buildChildSearchFilter(item)
    else buildDescendentSearchFilter(item)

  /**
   * Fetch a list of descendant IDs for a given virtual collection
   * in order to constrain a search space. This is:
   *  - child virtual collections
   *  - top level documentary units
   *
   * @param id the parent VC id
   * @return a sequence of descendant IDs
   */
  protected def vcDescendantIds(id: String): Future[Seq[String]] = {

    def parseRows(res: CypherResult): Seq[String] = res.data.flatMap(_.flatMap(_.as[Seq[String]]))

    cypher.get(
      """
        |MATCH (vc:VirtualUnit {__id: {vcid}})
        |OPTIONAL MATCH (vc)<-[:isPartOf*]-(child)
        |OPTIONAL MATCH (ddoc)<-[:includesUnit]-(vc)
        |OPTIONAL MATCH (doc)<-[:includesUnit]-(child)
        |RETURN DISTINCT collect(DISTINCT child.__id) + collect(DISTINCT doc.__id) + collect(DISTINCT ddoc.__id)
      """.stripMargin, Map("vcid" -> play.api.libs.json.JsString(id))).map(parseRows).map { seq =>


      logger.debug(s"Elements: ${seq.length}, distinct: ${seq.distinct.length}")

      globalConfig.configuration.getOptional[Int]("search.vc.maxDescendants").map { vcLimit =>
        if (seq.length > vcLimit) {
          logger.error(s"Truncating clauses on child item search for $id: items ${seq.length}")
          seq.distinct.take(vcLimit)
        } else seq
      }.getOrElse(seq)
    }
  }

  /**
   * Fetch a list of descendant IDs for a given virtual collection
   * in order to constrain a search space. This is:
   *  - child virtual collections
   *  - top level documentary units
   *
   * @param item a documentary unit or virtual collection
   * @return a sequence of descendant IDs
   */
  protected def descendantIds(item: Model): Future[Seq[String]] = {
    item match {
      case v: VirtualUnit => vcDescendantIds(item.id)
      case d => Future.successful(Seq(item.id))
    }
  }

  protected def buildDescendentSearchFilter(item: Model): Future[Map[String, Any]] =
    item match {
      case v: VirtualUnit => vcDescendantIds(item.id).map { seq =>
        if (seq.isEmpty) Map(ITEM_ID -> "__NO_VALID_ID__")
        else Map((s"$ITEM_ID:(${seq.mkString(" ")}) OR $ANCESTOR_IDS:(${seq.mkString(" ")})", ()))
      }
      // otherwise, for documentary units, we can just query
      // all ancestors
      case d => immediate(Map((s"$ANCESTOR_IDS:${item.id}", ())))
    }

  protected def buildChildSearchFilter(item: Model): Future[Map[String,Any]] =
    immediate {
      // Nastiness. We want a Solr query that will allow searching
      // both the child virtual collections of a VU as well as the
      // physical documentary units it includes. Since there is no
      // connection from the DU to VUs it belongs to (and creating
      // one is not feasible) we need to do this badness:
      // - load the VU from the graph along with its included DUs
      // - query for anything that has the VUs parent ID *or* anything
      // with an itemId among its included DUs
      import services.search.SearchConstants._
      logger.debug(s"Building child search for: ${item.id}")
      item match {
        case v: VirtualUnit =>
          val pq = v.includedUnits.map(_.id)
          if (pq.isEmpty) Map((s"$PARENT_ID:${v.id}", ()))
          else Map((s"$PARENT_ID:${v.id} OR $ITEM_ID:(${pq.mkString(" ")})", ()))
        case d => Map((s"$PARENT_ID:${d.id}", ()))
      }
    }
}
