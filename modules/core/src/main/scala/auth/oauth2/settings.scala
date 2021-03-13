package auth.oauth2

import auth.AuthenticationError

/**
 * OAuth2 authentication code, largely adapted from
 * SecureSocial (thanks Jorge!)
 */
object OAuth2Settings {
  val AuthorizationUrl = "authorizationUrl"
  val AccessTokenUrl = "accessTokenUrl"
  val UserInfoUrl = "userInfoUrl"
  val ClientId = "clientId"
  val ClientSecret = "clientSecret"
  val Scope = "scope"
}

object OAuth2Constants {
  val ClientId = "client_id"
  val ClientSecret = "client_secret"
  val RedirectUri = "redirect_uri"
  val Scope = "scope"
  val ResponseType = "response_type"
  val State = "state"
  val GrantType = "grant_type"
  val AuthorizationCode = "authorization_code"
  val AccessToken = "access_token"
  val Error = "error"
  val Code = "code"
  val TokenType = "token_type"
  val ExpiresIn = "expires_in"
  val RefreshToken = "refresh_token"
  val AccessDenied = "access_denied"
}

class OAuth2Error(msg: String) extends AuthenticationError(msg)

case class UserData(
  providerId: String,
  email: String,
  name: String,
  imageUrl: Option[String]
)

case class ProviderSettings(
  clientId: String,
  clientSecret: String,
  authorizationUrl: String,
  accessTokenUrl: String,
  userInfoUrl: String,
  scope: String
)

case class OAuth2Settings(
  authorizationUrl: String,
  accessTokenUrl: String,
  clientId: String,
  clientSecret: String,
  scope: Option[String]
)

case class OAuth2Info(
  accessToken: String,
  tokenType: Option[String] = None,
  expiresIn: Option[Int] = None,
  refreshToken: Option[String] = None,
  userGuid: Option[String] = None
)
