package services.accounts

import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}

import models.{Account, OAuth2Association, OpenIDAssociation}
import utils.PageParams

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
case class MockAccountManager @Inject()(executionContext: ExecutionContext) extends AccountManager {

  private val self = this

  private def updateWith(acc: Account): Account = {
    mockdata.accountFixtures += acc.id -> acc
    acc
  }

  override def oAuth2: OAuth2AssociationManager = new OAuth2AssociationManager {
    protected implicit def executionContext: ExecutionContext = self.executionContext

    def addAssociation(id: String, providerId: String, provider: String): Future[Option[OAuth2Association]] =
      immediate {
        val oauth = OAuth2Association(id, providerId, provider, user= mockdata.accountFixtures.get(id))
        mockdata.oauth2AssociationFixtures += oauth
        Some(oauth)
      }

    def findAll: Future[Seq[OAuth2Association]] =
      immediate(mockdata.oauth2AssociationFixtures.toSeq)

    def findByProviderInfo(providerUserId: String, provider: String): Future[Option[OAuth2Association]] =
      immediate(mockdata.oauth2AssociationFixtures.find {
        assoc => assoc.providerId == providerUserId && assoc.provider == provider
      })

    def findForAccount(id: String): Future[Seq[OAuth2Association]] =
      immediate(mockdata.oauth2AssociationFixtures.filter(_.id == id).toSeq)
  }

  override def openId: OpenIdAssociationManager = new OpenIdAssociationManager {
    protected implicit def executionContext: ExecutionContext = self.executionContext

    def addAssociation(id: String, assoc: String): Future[Option[OpenIDAssociation]] =
      immediate {
        val oid = OpenIDAssociation(id, url = assoc, user = mockdata.accountFixtures.get(id))
        mockdata.openIdAssociationFixtures += oid
        Some(oid)
      }

    def findByUrl(url: String): Future[Option[OpenIDAssociation]] =
      immediate(mockdata.openIdAssociationFixtures.find(_.url == url))

    def findAll: Future[Seq[OpenIDAssociation]] = immediate(mockdata.openIdAssociationFixtures.toSeq)
  }

  override def get(id: String): Future[Account] =
    immediate(mockdata.accountFixtures.getOrElse(id, throw new NoSuchElementException(id)))

  override def findById(id: String): Future[Option[Account]] =
    immediate(mockdata.accountFixtures.get(id))

  override def findByEmail(email: String): Future[Option[Account]] =
    immediate(mockdata.accountFixtures.values.find(_.email.toLowerCase == email.toLowerCase))

  override def findAllById(ids: Seq[String]): Future[Seq[Account]] =
    immediate(mockdata.accountFixtures.filter(kv => ids.contains(kv._1)).values.toSeq)

  override def authenticateById(id: String, pw: String, verifiedOnly: Boolean = false): Future[Option[Account]] = immediate {
    for {
      acc <- mockdata.accountFixtures.values.find(_.id == id)
      hashed <- acc.password
      if hashed.check(pw) && (if(verifiedOnly) acc.verified else true)
    } yield acc
  }

  override def authenticateByEmail(email: String, pw: String, verifiedOnly: Boolean = false): Future[Option[Account]] = immediate {
    for {
      acc <- mockdata.accountFixtures.values.find(_.email.toLowerCase() == email.toLowerCase)
      hashed <- acc.password
      if hashed.check(pw) && (if(verifiedOnly) acc.verified else true)
    } yield acc
  }

  override def setLoggedIn(account: Account): Future[Account] =
    immediate(updateWith(account.copy(lastLogin = Some(ZonedDateTime.now()))))

  override def verify(account: Account, token: String): Future[Option[Account]] =
    immediate(Some(updateWith(account.copy(verified = true))))

  override def createToken(id: String, token: UUID, isSignUp: Boolean): Future[Unit] =
    immediate(mockdata.tokenFixtures += ((token.toString, id, isSignUp)))

  override def findByToken(token: String, isSignUp: Boolean): Future[Option[Account]] = {
    mockdata.tokenFixtures.find(t => t._1 == token && t._3 == isSignUp) match {
      case Some((t, p, s)) => findById(p)
      case _ => immediate(None)
    }
  }

  override def create(account: Account): Future[Account] =
    immediate(updateWith(account.copy(created = Some(ZonedDateTime.now()))))

  override def update(account: Account): Future[Account] =
    immediate(updateWith(account))

  override def delete(id: String): Future[Boolean] = immediate {
    mockdata.accountFixtures -= id
    true
  }

  override def expireTokens(id: String): Future[Unit] = immediate {
    val indicesToDelete = for {
      (t, i) <- mockdata.tokenFixtures.zipWithIndex if t._2 == id
    } yield i
    for (i <- (mockdata.tokenFixtures.size -1) to 0 by -1) if (indicesToDelete contains i) mockdata.tokenFixtures remove i
  }

  override def findAll(params: PageParams, filters: AccountFilters = AccountFilters()): Future[Seq[Account]] = immediate {
    val all = mockdata.accountFixtures.values.toSeq.sortBy(_.id).drop(params.offset)
      .filter(a => filters.active.forall(_ == a.active))
      .filter(a => filters.verified.forall(_ == a.verified))
      .filter(a => filters.staff.forall(_ == a.staff))
    if (params.hasLimit) all.drop(params.limit) else all
  }
}
