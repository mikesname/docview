package controllers.portal

import auth.AccountManager
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.DocumentaryUnit
import play.api.mvc.RequestHeader
import utils.search._
import views.html.p

import scala.concurrent.Future.{successful => immediate}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class DocumentaryUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
                                  accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends PortalController
  with Generic[DocumentaryUnit]
  with Search
  with FacetConfig {

  private val portalDocRoutes = controllers.portal.routes.DocumentaryUnits

  private def filterKey(implicit request: RequestHeader): String =
    if (!hasActiveQuery(request)) SearchConstants.PARENT_ID else SearchConstants.ANCESTOR_IDS

  def searchAll = UserBrowseAction.async { implicit request =>
    val filters = if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[AnyModel](
      filters = filters,
      facetBuilder = docSearchFacets,
      entities = Seq(EntityType.DocumentaryUnit, EntityType.VirtualUnit)
    ).map { result =>
      Ok(p.documentaryUnit.list(result, portalDocRoutes.searchAll(),
        request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(p.documentaryUnit.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[DocumentaryUnit](
      filters = Map(filterKey -> request.item.id),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      Ok(p.documentaryUnit.show(request.item, result,  request.annotations,
        request.links, portalDocRoutes.search(id), request.watched))
    }
  }

  def search(id: String) = GetItemAction(id).async { implicit request =>
    findType[DocumentaryUnit](
      filters = Map(filterKey -> request.item.id),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      if (isAjax) Ok(p.documentaryUnit.childItemSearch(request.item, result,
        portalDocRoutes.search(id), request.watched))
      else Ok(p.documentaryUnit.search(request.item, result,
        portalDocRoutes.search(id), request.watched))
    }
  }
}
