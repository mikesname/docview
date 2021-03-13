package services.search

import scala.concurrent.Future


trait SearchEngine {
  /**
    * Check service status
    *
    * @return a status message
    */
  def status(): Future[String]

  /**
    * Run a quick filter
    */
  def filter(config: SearchQuery): Future[SearchResult[FilterHit]]

  /**
    * Run a full search
    */
  def search(config: SearchQuery): Future[SearchResult[SearchHit]]
}