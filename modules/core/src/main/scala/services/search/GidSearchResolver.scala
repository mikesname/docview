package services.search

import models.Readable
import play.api.Logger
import services.data.{ApiUser, DataApi}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
  * Resolve search hits to DB items by the GID field
  */
case class GidSearchResolver @Inject()(dataApi: DataApi)(implicit executionContext: ExecutionContext) extends SearchItemResolver {
  private val logger = Logger(classOf[GidSearchResolver])
  def resolve[MT: Readable](docs: Seq[SearchHit])(implicit apiUser: ApiUser): Future[Seq[Option[MT]]] = {
    logger.debug(s"Fetching GIDs: ${docs.map(_.gid)}")
    dataApi.withContext(apiUser).fetch(gids = docs.map(_.gid))
  }
}
