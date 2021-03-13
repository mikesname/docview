package services.accounts

import java.sql.Connection
import javax.inject.Inject

import anorm.SqlParser._
import anorm._
import models.OpenIDAssociation
import play.api.db.Database

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object SqlOpenIdAssociationManager {
  private[accounts] val openIdParser: RowParser[OpenIDAssociation] = {
    get[String]("openid_association.id") ~
    get[String]("openid_association.openid_url") map {
      case id ~ url => OpenIDAssociation(id, url, None)
    }
  }

  private[accounts] val openIdWithUser: RowParser[OpenIDAssociation] = openIdParser ~ SqlAccountManager.userParser map {
    case association ~ user => association.copy(user = Some(user))
  }
}

case class SqlOpenIdAssociationManager @Inject()(db: Database)(implicit executionContext: ExecutionContext)
  extends OpenIdAssociationManager{

  import SqlOpenIdAssociationManager._

  def findByUrl(url: String): Future[Option[OpenIDAssociation]] = Future {
    db.withConnection { implicit connection =>
      getByUrl(url)
    }
  }

  def addAssociation(id: String, url: String): Future[Option[OpenIDAssociation]] = Future {
    db.withConnection { implicit connection =>
      SQL"INSERT INTO openid_association (id, openid_url) VALUES ($id, $url)"
        .executeInsert(SqlParser.scalar[String].singleOpt)
      getByUrl(url)
    }
  }

  def findAll: Future[Seq[OpenIDAssociation]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
        SELECT
          users.*,
          openid_association.id,
          openid_association.openid_url
        FROM openid_association
        JOIN users ON openid_association.id =  users.id
      """.as(openIdWithUser *)
    }
  }

  private def getByUrl(url: String)(implicit conn: Connection) =
    SQL"""
      SELECT
        users.*,
        openid_association.id,
        openid_association.openid_url
      FROM openid_association
      JOIN users ON openid_association.id =  users.id
      WHERE openid_association.openid_url = $url LIMIT 1
    """.as(openIdWithUser.singleOpt)
}
