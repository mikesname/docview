package services.search

import models.Readable
import services.data.{ApiUser, DataApi}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
  * Resolve search hits to DB items by the itemId field
  */
case class IdSearchResolver @Inject()(dataApi: DataApi)(implicit executionContext: ExecutionContext) extends SearchItemResolver {
  def resolve[MT: Readable](docs: Seq[SearchHit])(implicit apiUser: ApiUser): Future[Seq[Option[MT]]] =
    dataApi.withContext(apiUser).fetch(ids = docs.map(_.itemId))
}
