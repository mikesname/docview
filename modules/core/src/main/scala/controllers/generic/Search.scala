package controllers.generic

import controllers.base.CoreActionBuilders
import models.{ContentType, EntityType, Readable, UserProfile, WithId}
import play.api.Logger
import play.api.mvc._
import services.search._
import utils.{Page, PageParams}

import scala.concurrent.Future


/**
  * Helpers for using the search engine from controllers.
  */
trait Search extends CoreActionBuilders {

  private def logger = Logger(getClass)

  protected def searchEngine: services.search.SearchEngine

  protected def searchResolver: services.search.SearchItemResolver

  /**
    * A function that generates a list of facet classes from an
    * incoming request header. The facet rendering can be changed
    * based on request variables such as the user's current language.
    */

  /**
    * A default facet class builder.
    */
  protected val emptyFacets: FacetBuilder = lang => List.empty[FacetClass[Facet]]

  /**
    * Ascertain if the user is making a textual query on this
    * request, as opposed to facet filtering a full list.
    */
  protected def hasActiveQuery(request: RequestHeader): Boolean =
    request.getQueryString(SearchParams.QUERY).exists(_.nonEmpty)

  private def bindFacetsFromRequest(facetClasses: Seq[FacetClass[Facet]])(implicit request: RequestHeader): Seq[AppliedFacet] = {
    // Facets provided with names as query parameters
    val specific: Seq[AppliedFacet] = facetClasses.flatMap { fc =>
      request.queryString
        .get(fc.param)
        .map(_.filterNot(_.trim.isEmpty))
        .map(values => AppliedFacet(fc.key, values.toList))
    }

    // Facets provided using ?facet=name:value format
    val fs: Seq[String] = request.queryString.getOrElse(SearchParams.FACET, Seq.empty[String])
    val keys: Map[String, String] = facetClasses.map(fc => fc.param -> fc.key).toMap
    val generic: Seq[AppliedFacet] = fs.map(_.split(":").toList).collect {
      case key :: value :: Nil if keys.contains(key) => keys.getOrElse(key, key) -> value
    }.foldLeft(Map.empty[String, Seq[String]]) { case (m, (k, v)) =>
      m.updated(k, v +: m.getOrElse(k, Seq.empty[String]))
    }.map(s => AppliedFacet(s._1, s._2)).toSeq

    specific ++ generic
  }

  // Search sort logic. By default, if there's a query, items come out
  //sorted by their score. Otherwise, they are sorted by name.
  private def defaultSortFunction(sp: SearchParams, fallback: SearchSort.Value = SearchSort.DateNewest): SearchSort.Value =
    sp.sort.getOrElse(if (sp.query.exists(_.trim.nonEmpty)) SearchSort.Score else fallback)

  private def queryFromRequest(
    params: SearchParams,
    paging: PageParams,
    sort: SearchSort.Value = SearchSort.DateNewest,
    facetBuilder: FacetBuilder = emptyFacets)(implicit userOpt: Option[UserProfile], request: RequestHeader): SearchQuery = {

    val facetClasses = facetBuilder(request)
    val appliedFacets: Seq[AppliedFacet] = bindFacetsFromRequest(facetClasses)

    SearchQuery(
      params.copy(sort = Some(defaultSortFunction(params, fallback = sort))),
      paging,
      appliedFacets = appliedFacets,
      facetClasses = facetClasses,
      user = userOpt,
      lang = request.lang
    )
  }

  /**
    * Dispatch a search to the search engine.
    *
    * @param params       The search parameters
    * @param paging       The pagination paramenters
    * @param filters      A map of key/value filter pairs
    * @param extra        An arbitrary set of key/value parameters
    * @param sort         The default ordering
    * @param idFilters    Additional ID filters
    * @param entities     A list of entities to limit the search to
    * @param facetBuilder A function to create the set of facets
    *                     from the incoming request
    * @param mode         The search mode, default all or default to none
    * @return A query result containing the page of search data,
    *         plus the resolved parameters and facets.
    */
  protected def find[MT](
    params: SearchParams,
    paging: PageParams,
    filters: Map[String, Any] = Map.empty,
    extra: Map[String, Any] = Map.empty,
    sort: SearchSort.Value = SearchSort.DateNewest,
    idFilters: Option[Seq[String]] = None,
    entities: Seq[EntityType.Value] = Nil,
    facetBuilder: FacetBuilder = emptyFacets,
    mode: SearchMode.Value = SearchMode.DefaultAll,
    resolverOpt: Option[SearchItemResolver] = None)(
    implicit request: RequestHeader, userOpt: Option[UserProfile], rd: Readable[MT]): Future[SearchResult[(MT, SearchHit)]] = {

    val query = queryFromRequest(params.copy(entities = entities), paging, sort, facetBuilder).copy(
      filters = filters,
      withinIds = idFilters,
      extraParams = extra,
      mode = mode
    )

    for {
      res <- searchEngine.search(query)
      list <- resolverOpt.getOrElse(searchResolver).resolve(res.page.items)
    } yield {
      if (list.size != res.page.size) {
        logger.warn(s"Items returned by search were not found in database: ${res.page.items.map(_.id)} -> $list")
      }
      res.copy(page = res.page.copy(items = list.zip(res.page.items).collect {
        case (Some(a), h) => (a, h)
      }))
    }
  }

  /**
    * Helper for searching a set of pre-fetched items and then
    * combining the resulting search hits with those items.
    *
    * This applies an ID filter to the search query from the
    * IDs of the given items.
    */
  protected def findIn[MT <: WithId](
    items: Seq[MT],
    params: SearchParams,
    paging: PageParams,
    filters: Map[String, Any] = Map.empty,
    extra: Map[String, Any] = Map.empty,
    sort: SearchSort.Value = SearchSort.DateNewest,
    entities: Seq[EntityType.Value] = Nil,
    facetBuilder: FacetBuilder = emptyFacets,
    mode: SearchMode.Value = SearchMode.DefaultAll)(
    implicit request: RequestHeader, userOpt: Option[UserProfile]): Future[SearchResult[(MT, SearchHit)]] = {

    val query = queryFromRequest(params.copy(entities = entities), paging, sort, facetBuilder).copy(
      filters = filters,
      withinIds = Some(items.map(_.id)),
      extraParams =  extra,
      mode = mode
    )

    searchEngine.search(query).map { result =>
      val resolved = result.page.items.flatMap(hit => items.find(_.id == hit.itemId).map(m => m -> hit))
      result.withItems(resolved)
    }
  }

  /**
    * Dispatch a search for items of a single content type to the search engine.
    */
  protected def findType[MT](
    params: SearchParams,
    paging: PageParams,
    filters: Map[String, Any] = Map.empty,
    extra: Map[String, Any] = Map.empty,
    sort: SearchSort.Value = SearchSort.DateNewest,
    idFilters: Option[Seq[String]] = None,
    facetBuilder: FacetBuilder = emptyFacets,
    mode: SearchMode.Value = SearchMode.DefaultAll,
    resolverOpt: Option[SearchItemResolver] = None)(
    implicit request: RequestHeader, userOpt: Option[UserProfile], rd: ContentType[MT]): Future[SearchResult[(MT, SearchHit)]] = {

    find[MT](params, paging, filters, extra, sort, idFilters, Seq(rd.entityType), facetBuilder, mode, resolverOpt)
  }

  protected def filter[A](params: SearchParams = SearchParams.empty, paging: PageParams = PageParams.empty, filters: Map[String, Any] = Map.empty)(implicit userOpt: Option[UserProfile], request: Request[A]): Future[Page[FilterHit]] =
    searchEngine.filter(queryFromRequest(params, paging).copy(filters = filters)).map(_.page)
}
