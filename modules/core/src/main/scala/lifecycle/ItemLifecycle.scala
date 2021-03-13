package lifecycle

import models.{EventType, Model}

import scala.concurrent.{ExecutionContext, Future}

trait ItemLifecycle {
  /**
    * A lifecycle hook called prior to saving an item.
    *
    * @param id    the item ID, if available
    * @param item  the existing item, if available
    * @param data  the data that is about to be saved
    * @param event the event type
    * @param ec    an implicit execution context
    * @tparam MT the type of the model
    * @return a future of (possibly modified) model data
    */
  def preSave[MT <: Model](id: Option[String], item: Option[MT], data: MT#T, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT#T]

  /**
    * A lifecycle hook called after saving an item.
    *
    * @param id    the item ID
    * @param item  the saved item
    * @param event the event type
    * @param ec    the implicit execution context
    * @tparam MT the type of the model
    * @return a future of the model
    */
  def postSave[MT <: Model](id: String, item: MT, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT]
}
