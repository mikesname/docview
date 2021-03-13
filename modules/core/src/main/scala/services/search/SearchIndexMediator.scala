package services.search

import akka.actor.ActorRef
import models.EntityType

import scala.concurrent.Future

case class IndexingError(msg: String) extends Exception(msg)

trait SearchIndexMediator {
  def handle: SearchIndexMediatorHandle
}

/**
  * Interface to an indexer service.
  */
trait SearchIndexMediatorHandle {

  /**
    * Provide a concurrent channel through which to
    * pass update events.
    *
    * @param actorRef  an actor to which to post messages
    * @param formatter a formatter for event strings
    * @return
    */
  def withChannel(actorRef: ActorRef, formatter: String => String = identity[String], filter: Int => Boolean = _ % 100 == 0): SearchIndexMediatorHandle = this

  /**
    * Index single items by id.
    *
    * @param ids the ids of the items
    */
  def indexIds(ids: String*): Future[Unit]

  /**
    * Index all items of a given set of types.
    *
    * @param entityTypes types to index
    */
  def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Unit]

  /**
    * Index all children of a given item.
    *
    * @param entityType the types to clear
    * @param id         the id of the parent
    */
  def indexChildren(entityType: EntityType.Value, id: String): Future[Unit]

  /**
    * Clear the index of all items.
    */
  def clearAll(): Future[Unit]

  /**
    * Clear the index of all items of a given type.
    *
    * @param entityTypes the types to clear
    */
  def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Unit]

  /**
    * Clear a given item from the index.
    *
    * @param ids the ids of the items to delete
    */
  def clearIds(ids: String*): Future[Unit]

  /**
    * Clear a given item from the index.
    *
    * @param key   the item key
    * @param value the item value
    */
  def clearKeyValue(key: String, value: String): Future[Unit]
}
