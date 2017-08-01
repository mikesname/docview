import javax.inject.{Inject, Provider}

import auth.handler.AuthIdContainer
import auth.handler.cookie.CookieIdContainer
import auth.oauth2.{OAuth2Flow, WebOAuth2Flow}
import services.cypher.{Cypher, CypherQueryService, CypherService, SqlCypherQueryService}
import com.google.inject.AbstractModule
import eu.ehri.project.indexing.index.Index
import eu.ehri.project.indexing.index.impl.SolrIndex
import eu.ehri.project.search.solr._
import global.{AppGlobalConfig, GlobalConfig, GlobalEventHandler}
import models.{GuideService, SqlGuideService}
import services.accounts.{AccountManager, SqlAccountManager}
import services.data.{GidSearchResolver, _}
import services.feedback.{FeedbackService, SqlFeedbackService}
import services.htmlpages.{GoogleDocsHtmlPages, HtmlPages}
import services.redirects.{MovedPageLookup, SqlMovedPageLookup}
import services.search.{SearchEngine, SearchIndexMediator, SearchItemResolver, SearchToolsIndexMediator}
import services.storage.{FileStorage, S3FileStorage}
import views.{FlexmarkMarkdownRendererProvider, MarkdownRenderer}

private class SolrIndexProvider @Inject()(config: play.api.Configuration) extends Provider[Index] {
  override def get(): Index = new SolrIndex(utils.serviceBaseUrl("solr", config))
}

class AppModule extends AbstractModule {
  protected def configure(): Unit = {
    bind(classOf[AuthIdContainer]).to(classOf[CookieIdContainer])
    bind(classOf[AccountManager]).to(classOf[SqlAccountManager])
    bind(classOf[GlobalConfig]).to(classOf[AppGlobalConfig])
    bind(classOf[Index]).toProvider(classOf[SolrIndexProvider])
    bind(classOf[ResponseParser]).to(classOf[SolrJsonResponseParser])
    bind(classOf[QueryBuilder]).to(classOf[SolrQueryBuilder])
    bind(classOf[SearchIndexMediator]).to(classOf[SearchToolsIndexMediator])
    bind(classOf[SearchEngine]).to(classOf[SolrSearchEngine])
    bind(classOf[SearchItemResolver]).to(classOf[GidSearchResolver])
    bind(classOf[EventHandler]).to(classOf[GlobalEventHandler])
    bind(classOf[DataApi]).to(classOf[DataApiService])
    bind(classOf[FeedbackService]).to(classOf[SqlFeedbackService])
    bind(classOf[CypherQueryService]).to(classOf[SqlCypherQueryService])
    bind(classOf[IdGenerator]).to(classOf[CypherIdGenerator])
    bind(classOf[OAuth2Flow]).to(classOf[WebOAuth2Flow])
    bind(classOf[MovedPageLookup]).to(classOf[SqlMovedPageLookup])
    bind(classOf[FileStorage]).to(classOf[S3FileStorage])
    bind(classOf[HtmlPages]).to(classOf[GoogleDocsHtmlPages])
    bind(classOf[GuideService]).to(classOf[SqlGuideService])
    bind(classOf[MarkdownRenderer]).toProvider(classOf[FlexmarkMarkdownRendererProvider])
    bind(classOf[Cypher]).to(classOf[CypherService])
  }
}