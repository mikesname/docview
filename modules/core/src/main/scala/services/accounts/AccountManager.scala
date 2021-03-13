package services.accounts

import java.util.UUID

import models.Account
import utils.PageParams

import scala.concurrent.{ExecutionContext, Future}

case class AccountFilters(
  staff: Option[Boolean] = None,
  verified: Option[Boolean] = None,
  active: Option[Boolean] = None
)

trait AccountManager {

  def oAuth2: OAuth2AssociationManager

  def openId: OpenIdAssociationManager

  protected implicit def executionContext: ExecutionContext

  /**
    * Authenticate a user via an email/password pair.
    *
    * @param email        the user's email
    * @param pw           the user's password
    * @param verifiedOnly whether or not the user must be verified
    * @return an optional account, if authenticated
    */
  def authenticateByEmail(email: String, pw: String, verifiedOnly: Boolean = false): Future[Option[Account]]

  /**
    * Authenticate a user via an id/password pair.
    *
    * @param id           the user's id
    * @param pw           the user's password
    * @param verifiedOnly whether or not the user must be verified
    * @return an account, if authenticated
    */
  def authenticateById(id: String, pw: String, verifiedOnly: Boolean = false): Future[Option[Account]]

  /**
    * Get a user account via id. The future will fail with a
    * `NoSuchElement` if the account does not exist.
    *
    * @param id the user's id
    * @return the account
    */
  def get(id: String): Future[Account]

  /**
    * Find an account by id.
    *
    * @param id the user's id
    * @return the account, if found
    */
  def findById(id: String): Future[Option[Account]]

  /**
    * Find an account by email.
    *
    * @param email the user's email
    * @return the account, if found
    */
  def findByEmail(email: String): Future[Option[Account]]

  /**
    * Found an account via a reset token.
    *
    * @param token    the token
    * @param isSignUp whether or not this is a signup token (as
    *                 opposed to a reset one)
    * @return the account, if found
    */
  def findByToken(token: String, isSignUp: Boolean = false): Future[Option[Account]]

  /**
    * Fetch all accounts.
    *
    * @param params the pagination parameters
    * @return a seq of accounts
    */
  def findAll(params: PageParams = PageParams.empty, filters: AccountFilters = AccountFilters()): Future[Seq[Account]]

  /**
    * Fetch all accounts with the given ids.
    *
    * @param ids a seq of ids
    * @return a seq of accounts
    */
  def findAllById(ids: Seq[String]): Future[Seq[Account]]

  /**
    * Update the account's `lastLogin` flag.
    *
    * @param account the account to update
    * @return the updated account
    */
  def setLoggedIn(account: Account): Future[Account]

  /**
    * Set the account verified and delete the verification token.
    *
    * @param account the account to update
    * @param token   the verification token
    * @return the updated account, if the token was found
    */
  def verify(account: Account, token: String): Future[Option[Account]]

  /**
    * Create a new account.
    *
    * @param account the account to create
    * @return the created account
    */
  def create(account: Account): Future[Account]

  /**
    * Update an account.
    *
    * @param account the account to update
    * @return the updated account
    */
  def update(account: Account): Future[Account]

  /**
    * Delete an account.
    *
    * @param id the account's id
    * @return whether or not the account was found and deleted
    */
  def delete(id: String): Future[Boolean]

  /**
    * Create a reset token for signup email verification or
    * password reset.
    *
    * @param id   the account id
    * @param uuid a universally unique id
    */
  def createToken(id: String, uuid: UUID, isSignUp: Boolean): Future[Unit]

  /**
    * Expire all tokens for the given account id.
    *
    * @param id an account id
    */
  def expireTokens(id: String): Future[Unit]
}
