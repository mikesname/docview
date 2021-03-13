package services.search

import models.Readable

import scala.concurrent.Future
import services.data.ApiUser

/**
 * Component responsible for resolving items from the
 * database from items returned from the search engine.
 *
 * Different resolvers could use either global identifiers
 * or more optimised internal graph IDs for faster resolution.
 */
trait SearchItemResolver {
  def resolve[MT: Readable](results: Seq[SearchHit])(implicit apiUser: ApiUser): Future[Seq[Option[MT]]]
}
