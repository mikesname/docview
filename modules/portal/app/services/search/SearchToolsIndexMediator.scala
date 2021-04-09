package services.search

import akka.actor.{ActorRef, ActorSystem}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper, ObjectWriter}
import config.serviceBaseUrl
import eu.ehri.project.indexing.Pipeline.Builder
import eu.ehri.project.indexing.converter.impl.JsonConverter
import eu.ehri.project.indexing.index.Index
import eu.ehri.project.indexing.sink.impl.{CallbackSink, IndexJsonSink}
import eu.ehri.project.indexing.source.impl.WebJsonSource
import eu.ehri.project.indexing.{IndexHelper, Pipeline}
import models.EntityType
import play.api.Logger
import services.data.Constants

import java.util.Properties
import javax.inject.Inject
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

case class SearchToolsIndexMediator @Inject()(
    index: Index,
    config: play.api.Configuration,
    actorSystem: ActorSystem)  extends SearchIndexMediator {

  override def handle: SearchIndexMediatorHandle = SearchToolsIndexMediatorHandle()(index, config)(
    actorSystem.dispatchers.lookup("contexts.blocking-io")
  )

  override def toString = "SearchTools"
}

/**
 * Wrapper for the EHRI search tools index helper, which
 * provides a more convenient API for keeping the search
 * engine updated. This uses the library in-process, which
 * is more convenient for deployment.
 */
case class SearchToolsIndexMediatorHandle(
  chan: Option[ActorRef] = None,
  processFunc: String => String = identity[String],
  progressFilter: Int => Boolean = _ % 100 == 0
)(index: Index, config: play.api.Configuration)(implicit executionContext: ExecutionContext) extends SearchIndexMediatorHandle {

  private val logger  = Logger(this.getClass)

  val dataBaseUrl: String = serviceBaseUrl("ehridata", config)

  override def withChannel(actorRef: ActorRef, formatter: String => String, filter: Int => Boolean): SearchToolsIndexMediatorHandle =
    copy(chan = Some(actorRef), processFunc = formatter, progressFilter = filter)(index, config)

  private def indexProperties(extra: Map[String,Any] = Map.empty): Properties = {
    val props = new Properties()
    props.put(Constants.STREAM_HEADER_NAME, true.toString)
    props.put(Constants.AUTH_HEADER_NAME, "admin")
    extra.foreach { case (k, v) => props.put(k, v.toString)}
    props
  }

  private def indexHelper(specs: String*): Pipeline[JsonNode, JsonNode] = {
    val builder = new Builder[JsonNode, JsonNode]
      .addSink(new IndexJsonSink(index, new IndexJsonSink.EventHandler {
        override def handleEvent(event: Any): Unit = {
          chan.foreach(_ ! processFunc(event.toString))
        }
      }))
      .addConverter(new JsonConverter)
      .addSink(new CallbackSink[JsonNode](new CallbackSink.Callback[JsonNode]() {
         val writer: ObjectWriter = new ObjectMapper().writerWithDefaultPrettyPrinter()
         var count = 0
         override def call(node: JsonNode): Unit = {
           logger.trace(writer.writeValueAsString(node))
           count += 1
           if (progressFilter(count)) {
             chan.foreach(_ ! processFunc(s"Items processed: $count"))
           }
         }
         override def finish(): Unit = {
           val msg: String = s"Total items processed: $count"
           logger.debug(msg)
           chan.foreach(_ ! processFunc(msg))
         }
       }))

    IndexHelper.urlsFromSpecs(dataBaseUrl, specs: _*).asScala.foreach { url =>
      builder.addSource(new WebJsonSource(url, indexProperties()))
    }
    builder.build()
  }

  override def indexIds(ids: String*): Future[Unit] = Future {
    logger.debug(s"Indexing: $ids")
    // NB: Search tools breaks if using too many IDs since the
    // URL for fetching data is too long, so this is a hacky
    // workaround...
    ids.grouped(100).foreach(batch => indexHelper(batch.map(id => s"@$id"): _*).run())

  }

  override def indexChildren(entityType: EntityType.Value, id: String): Future[Unit] = Future {
    logger.debug(s"Index children: $entityType|$id")
    indexHelper(entityType.toString + "|" + id).run()
  }

  override def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = Future {
    logger.debug(s"Index types: ${entityTypes.mkString(", ")}")
    indexHelper(entityTypes.map(_.toString): _*).run()
  }

  override def clearKeyValue(key: String, value: String): Future[Unit] = Future {
    logger.debug(s"Clear key value: $key -> $value")
    index.deleteByFieldValue(key, value, true)
  }

  override def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = Future {
    logger.debug(s"Clear types: ${entityTypes.mkString(", ")}")
    index.deleteTypes(entityTypes.map(_.toString).asJava, true)
  }

  override def clearAll(): Future[Unit] = Future {
    logger.debug(s"Delete all")
    index.deleteAll(true)
  }

  override def clearIds(ids: String*): Future[Unit] = Future {
    logger.debug(s"Clear ids: $ids")
    index.deleteItems(ids.asJava, true)
  }
}
