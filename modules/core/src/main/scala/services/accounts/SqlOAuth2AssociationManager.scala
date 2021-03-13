package services.accounts

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.Inject
import models.OAuth2Association
import play.api.db.Database

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps


object SqlOAuth2AssociationManager {
  private[accounts] val oAuthParser: RowParser[OAuth2Association] = {
    get[String]("oauth2_association.id") ~
    get[String]("oauth2_association.provider_id") ~
    get[String]("oauth2_association.provider") map {
      case id ~ providerId ~ provider => OAuth2Association(id, providerId, provider, None)
    }
  }

  private[accounts] val oAuthWithUser: RowParser[OAuth2Association] = oAuthParser ~ SqlAccountManager.userParser map {
    case association ~ user => association.copy(user = Some(user))
  }
}

case class SqlOAuth2AssociationManager @Inject()(db: Database)(implicit executionContext: ExecutionContext)
  extends OAuth2AssociationManager {

  import SqlOAuth2AssociationManager._

  def findByProviderInfo(providerUserId: String, provider: String): Future[Option[OAuth2Association]] = Future {
    db.withConnection { implicit connection =>
      getByInfo(providerUserId, provider)
    }
  }

  def findForAccount(id: String): Future[Seq[OAuth2Association]] = Future {
    db.withConnection { implicit conn =>
      getForAccount(id)
    }
  }

  def findAll: Future[Seq[OAuth2Association]] = Future {
    db.withConnection { implicit connection =>
      SQL"""
        SELECT
          users.*,
          oauth2_association.id,
          oauth2_association.provider_id,
          oauth2_association.provider
        FROM oauth2_association
        JOIN users ON oauth2_association.id =  users.id
      """.as(oAuthWithUser *)
    }
  }

  def addAssociation(id: String, providerId: String, provider: String): Future[Option[OAuth2Association]] = Future {
    db.withConnection { implicit connection =>
      SQL"""
        INSERT INTO oauth2_association (id, provider_id, provider)
        VALUES ($id, $providerId, $provider)
        """.executeInsert(SqlParser.scalar[String].singleOpt)
      getByInfo(providerId, provider)
    }
  }

  private def getForAccount(id: String)(implicit conn: Connection): Seq[OAuth2Association] = {
    SQL"""
      SELECT
        users.*,
        oauth2_association.id,
        oauth2_association.provider_id,
        oauth2_association.provider
      FROM oauth2_association
      JOIN users ON oauth2_association.id =  users.id
      WHERE users.id = $id
    """.as(oAuthWithUser *)
  }

  private def getByInfo(providerUserId: String, provider: String)(implicit conn: Connection): Option[OAuth2Association] = {
    SQL"""
      SELECT
        users.*,
        oauth2_association.id,
        oauth2_association.provider_id,
        oauth2_association.provider
      FROM oauth2_association
      JOIN users ON oauth2_association.id =  users.id
      WHERE oauth2_association.provider_id = $providerUserId
      AND oauth2_association.provider = $provider LIMIT 1
    """.as(oAuthWithUser.singleOpt)
  }
}
