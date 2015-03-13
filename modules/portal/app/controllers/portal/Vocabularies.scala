package controllers.portal

import auth.AccountManager
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{Repository, Vocabulary, Concept}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.RequestHeader
import utils.search._
import views.html.p

import scala.concurrent.Future.{successful => immediate}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Vocabularies @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
                                  accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends PortalController
  with Generic[Vocabulary]
  with Search
  with FacetConfig {

  private val portalVocabRoutes = controllers.portal.routes.Vocabularies

  private def filters(id: String)(implicit request: RequestHeader): Map[String,Any] =
    (if (!hasActiveQuery(request)) Map(SearchConstants.TOP_LEVEL -> true)
      else Map.empty[String,Any]) ++ Map(SearchConstants.HOLDER_ID -> id)

  def searchAll = UserBrowseAction.async { implicit request =>
    findType[Vocabulary](
      facetBuilder = repositorySearchFacets
    ).map { result =>
      Ok(p.vocabulary.list(result, portalVocabRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(p.vocabulary.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[Concept](
      filters = filters(request.item.id),
      facetBuilder = conceptFacets
    ).map { result =>
      Ok(p.vocabulary.show(request.item, result, request.annotations,
        request.links, portalVocabRoutes.search(id), request.watched))
    }
  }

  def search(id: String) = GetItemAction(id).async { implicit request =>
      findType[Concept](
        filters = filters(request.item.id),
        facetBuilder = conceptFacets
      ).map { result =>
        if (isAjax) Ok(p.vocabulary.childItemSearch(request.item, result,
          portalVocabRoutes.search(id), request.watched))
        else Ok(p.vocabulary.search(request.item, result,
          portalVocabRoutes.search(id), request.watched))
      }
  }

}
