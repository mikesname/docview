package lifecycle

import models.{EventType, Model}

import scala.concurrent.{ExecutionContext, Future}

case class NoopItemLifecycle() extends ItemLifecycle {

  private val logger = play.api.Logger(classOf[NoopItemLifecycle])

  override def preSave[MT <: Model](id: Option[String], item: Option[MT], data: MT#T, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT#T] = {
    logger.debug(s"Pre-save: $event: $data")
    Future.successful(data)
  }

  override def postSave[MT <: Model](id: String, item: MT, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT] = {
    logger.debug(s"Post-save: $event: ${item.data}")
    Future.successful(item)
  }
}
