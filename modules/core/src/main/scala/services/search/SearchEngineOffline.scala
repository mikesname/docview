package services.search

import services.ServiceException

/**
  * Indicates that we could not reach the search engine.
  *
  * @param msg a message
  * @param cause the underlying cause
  */
case class SearchEngineOffline(msg: String, cause: Throwable) extends ServiceException(msg, cause)