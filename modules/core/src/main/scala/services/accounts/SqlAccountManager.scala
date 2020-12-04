package services.accounts

import java.sql.Connection
import java.time.ZonedDateTime
import java.util.UUID

import akka.actor.ActorSystem
import anorm.SqlParser._
import anorm._
import auth._
import javax.inject._
import models.Account
import play.api.db.Database
import utils.PageParams

import scala.concurrent.{ExecutionContext, Future}


@Singleton
case class SqlAccountManager @Inject()(db: Database, actorSystem: ActorSystem) extends AccountManager {

  import SqlAccountManager._

  override protected implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  override def oAuth2: OAuth2AssociationManager = SqlOAuth2AssociationManager(db)

  override def openId: OpenIdAssociationManager = SqlOpenIdAssociationManager(db)


  override def setLoggedIn(account: Account): Future[Account] = Future {
    db.withTransaction { implicit conn =>
      SQL"""UPDATE users
            SET last_login = NOW()
            WHERE id = ${account.id}
            RETURNING *""".as(userParser.single)
    }
  }

  private def passwordMatches(account: Account, pw: String): Boolean =
    account.password.exists(hpw => if (!account.isLegacy) hpw.check(pw) else  hpw.checkLegacy(pw))

  override def authenticateById(id: String, pw: String, verifiedOnly: Boolean): Future[Option[Account]] = Future {
    db.withTransaction { implicit conn =>
      getById(id).filter { account =>
        passwordMatches(account, pw)
      }.filter { account =>
        if (verifiedOnly) account.verified else true
      }
    }
  }

  override def authenticateByEmail(email: String, pw: String, verifiedOnly: Boolean): Future[Option[Account]] = Future {
    db.withTransaction { implicit conn =>
      getByEmail(email).filter { account =>
        passwordMatches(account, pw)
      }.filter { account =>
        if (verifiedOnly) account.verified else true
      }
    }
  }


  override def get(id: String): Future[Account] = Future {
    db.withConnection { implicit conn =>
      getById(id).map { account =>
        account
      }.getOrElse(throw new NoSuchElementException(id))
    }
  }

  override def verify(account: Account, token: String): Future[Option[Account]] = Future {
    db.withTransaction { implicit conn =>
      SQL"DELETE FROM token WHERE token = $token".execute()
      SQL"""UPDATE users
            SET verified = ${true}
            WHERE id = ${account.id}
            RETURNING *""".as(userParser.singleOpt)
    }
  }

  override def findById(id: String): Future[Option[Account]] = Future {
    db.withConnection { implicit conn =>
      getById(id)
    }
  }

  override def findAllById(ids: Seq[String]): Future[Seq[Account]] =
    if (ids.isEmpty) Future.successful(Seq.empty)
    else Future {
      db.withConnection { implicit conn =>
        SQL"SELECT * FROM users WHERE users.id IN ($ids)".as(userParser.*)
      }
    }

  override def findByToken(token: String, isSignUp: Boolean): Future[Option[Account]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
        SELECT u.*, t.token FROM users u, token t
          WHERE u.id = t.id AND t.token = $token
            AND is_sign_up = $isSignUp
            AND t.expires > NOW()
      """.as(userParser.singleOpt)
    }
  }

  override def create(account: Account): Future[Account] = Future {
    db.withConnection { implicit conn =>
      SQL"""INSERT INTO users
        (id, email, verified, active, staff, allow_messaging, password, is_legacy)
        VALUES (
          ${account.id},
          ${account.email},
          ${account.verified},
          ${account.active},
          ${account.staff},
          ${account.allowMessaging},
          ${account.password},
          ${account.isLegacy}
        )
        RETURNING *""".as(userParser.single)
    }
  }

  override def update(account: Account): Future[Account] = Future {
    db.withConnection { implicit connection =>
      SQL"""
        UPDATE users
        SET
          email = ${account.email},
          verified = ${account.verified},
          staff = ${account.staff},
          active = ${account.active},
          allow_messaging = ${account.allowMessaging},
          password = ${account.password},
          is_legacy = ${account.isLegacy}
        WHERE id = ${account.id}
        RETURNING *
        """.as(userParser.single)
    }
  }

  override def delete(id: String): Future[Boolean] = Future {
    db.withConnection { implicit connection =>
      val rows: Int = SQL"""DELETE FROM users WHERE id = $id""".executeUpdate()
      rows > 0
    }
  }

  override def expireTokens(id: String): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL"""DELETE FROM token WHERE id = $id""".executeUpdate()
    }
    ()
  }

  override def findAll(params: PageParams = PageParams.empty, filters: AccountFilters = AccountFilters()): Future[Seq[Account]] =
    Future {
      db.withConnection { implicit conn =>
        val limit = if (params.hasLimit) params.limit else Integer.MAX_VALUE
        SQL"""
             SELECT * FROM users
              WHERE (${filters.active} IS NULL OR active = ${filters.active})
                AND (${filters.verified} IS NULL OR verified = ${filters.verified})
                AND (${filters.staff} IS NULL OR staff = ${filters.staff})
             ORDER BY id
             LIMIT $limit OFFSET ${params.offset}"""
          .as(userParser.*)
      }
    }

  override def findByEmail(email: String): Future[Option[Account]] = Future {
    db.withConnection { implicit  conn =>
      getByEmail(email)
    }
  }

  override def createToken(id: String, uuid: UUID, isSignUp: Boolean): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      if (isSignUp) createSignupToken(id, uuid) else createResetToken(id, uuid)
    }
  }


  private def getByEmail(email: String)(implicit conn: Connection): Option[Account] =
    // NB: this query would be better if it used the index (or a computed index)
    SQL"SELECT * FROM users WHERE lower(users.email) = lower($email)".as(userParser.singleOpt)

  private def getById(id: String)(implicit conn: Connection): Option[Account] =
    SQL"SELECT * FROM users WHERE users.id = $id".as(userParser.singleOpt)

  private def createSignupToken(id: String, uuid: UUID)(implicit conn: Connection): Unit =
    // NB: calculating expires here to avoid DB compatibility issues...
    SQL"""INSERT INTO token (id, token, expires, is_sign_up)
           VALUES ($id, $uuid, ${ZonedDateTime.now().plusWeeks(1)}, TRUE)""".execute()

  private def createResetToken(id: String, uuid: UUID)(implicit conn: Connection): Unit =
    SQL"""INSERT INTO token (id, token, expires, is_sign_up)
           VALUES ($id, $uuid, ${ZonedDateTime.now().plusHours(1)}, FALSE)""".execute()
}

object SqlAccountManager {
  import anorm.{Column, ParameterMetaData, ToStatement, TypeDoesNotMatch}

  implicit def pwToStatement: ToStatement[HashedPassword] =
    (s: java.sql.PreparedStatement, index: Int, aValue: HashedPassword) => s.setString(index, aValue.s)

  implicit object HashedPasswordParameterMetaData extends ParameterMetaData[HashedPassword] {
    val sqlType: String = ParameterMetaData.StringParameterMetaData.sqlType
    val jdbcType: Int = ParameterMetaData.StringParameterMetaData.jdbcType
  }

  implicit def rowToPw: Column[HashedPassword] = {
    Column[HashedPassword] { (value, meta) =>
      value match {
        case v: String => Right(HashedPassword.fromHashed(v))
        case _ => Left(TypeDoesNotMatch(
          s"Cannot convert $value:${value.asInstanceOf[AnyRef].getClass} to hashed password for column ${meta.column}"))
      }
    }
  }

  private[accounts] val userParser = {
    str("users.id") ~
      str("users.email") ~
      bool("users.verified") ~
      bool("users.staff") ~
      bool("users.active") ~
      bool("users.allow_messaging") ~
      get[Option[ZonedDateTime]]("users.created") ~
      get[Option[ZonedDateTime]]("users.last_login") ~
      get[Option[HashedPassword]]("users.password") ~
      get[Boolean]("users.is_legacy") map {
      case id ~ email ~ verified ~ staff ~ active ~ allowMessaging ~ created ~ login ~ pw ~ legacy =>
        Account(id, email, verified, staff, active, allowMessaging, created, login, pw, legacy)
    }
  }
}
