package utils

import javax.inject.Inject

import akka.actor.ActorSystem
import anorm._
import org.apache.commons.codec.digest.DigestUtils
import play.api.db.Database
import scala.concurrent.{ExecutionContext, Future}

case class SqlMovedPageLookup @Inject ()(implicit db: Database, actorSystem: ActorSystem) extends MovedPageLookup {

  implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  override def hasMovedTo(path: String): Future[Option[String]] = Future {
    import anorm.SqlParser.scalar
    val pathHash = DigestUtils.sha1Hex(path)
    db.withConnection { implicit conn =>
      val newPath: Option[String] = SQL"SELECT new_path FROM moved_pages WHERE original_path_sha1 = $pathHash"
        .as(scalar[String].singleOpt)
      newPath
    }
  }

  override def addMoved(moved: Seq[(String, String)]): Future[Int] = Future {
    // NB: Due to a very annoying character limit imposed by the ancient
    // version of MySql on which we are forced to run, the lookup key here
    // is a hash of the original path, rather than the path itself (which could
    // easily overflow the 255 varchar limit on MySql 5.0.-something-old.)

    // Unfortunate hack around SQL syntax variations
    def isPg = db.dataSource.getConnection
      .getMetaData.getDatabaseProductName.equals("PostgreSQL")

    if (moved.isEmpty) 0
    else db.withConnection { implicit conn =>
      val inserts = moved.map { case (from, to) =>
        Seq[NamedParameter](
          'hash -> DigestUtils.sha1Hex(from),
          'original -> from,
          'path -> to
        )
      }
      val q = """INSERT INTO moved_pages(original_path_sha1, original_path, new_path)
                        VALUES({hash}, {original}, {path})"""
      val conflict =
        if (isPg) """ ON CONFLICT (original_path_sha1) DO UPDATE SET new_path = {path}"""
        else """ ON DUPLICATE KEY UPDATE new_path = {path}"""
      val batch = BatchSql(q + conflict, inserts.head, inserts.tail: _*)
      val rows: Array[Int] = batch.execute()
      rows.count(_ > 0)
    }
  }
}
