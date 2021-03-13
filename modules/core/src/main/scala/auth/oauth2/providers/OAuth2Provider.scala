package auth.oauth2.providers

import java.net.URLEncoder

import auth.oauth2._
import com.fasterxml.jackson.core.JsonParseException
import play.api.Logger
import play.api.libs.json.Json

trait OAuth2Provider {

  protected val logger: Logger = play.api.Logger(getClass)

  implicit def config: play.api.Configuration


  /**
   * The name of this provider
   */
  def name: String

  /**
   * Build the OAuth2 redirect URL, given the URL to redirect
   * back to and the state key.
   *
   * @param handlerUrl the URL the provider will redirect the user
   *                   back to after authentication.
   * @param state the client-generated state, to prevent replay attacks
   */
  def buildRedirectUrl[A](handlerUrl: String, state: String): String = {
    val params = Seq(
      OAuth2Constants.ClientId -> settings.clientId,
      OAuth2Constants.RedirectUri -> handlerUrl,
      OAuth2Constants.ResponseType -> OAuth2Constants.Code,
      OAuth2Constants.State -> state,
      OAuth2Constants.Scope -> settings.scope
    )
    settings.authorizationUrl +
      params.map(p => p._1 + "=" + URLEncoder.encode(p._2, "UTF-8")).mkString("?", "&", "")
  }

  /**
   * The URL at which to access user info for this provider.
   *
   * @param info the OAuth2 access info
   */
  def getUserInfoUrl(info: OAuth2Info): String = settings.userInfoUrl

  /**
   * The parameters used to get the user info. Defaults to
   *  'access_token={token}'.
   *
   * @param info the OAuth2 access info
   */
  def getUserInfoParams(info: OAuth2Info): Seq[(String,String)] = Seq(
    OAuth2Constants.AccessToken -> info.accessToken
  )

  /**
   * The header data for fetching user info.
   *
   * @param info the OAuth2 access info
   */
  def getUserInfoHeader(info: OAuth2Info): Seq[(String, String)] = Seq.empty

  /**
   * The URL at which to fetch the access token.
   */
  def getAccessTokenUrl: String = settings.accessTokenUrl

  /**
   * The header data for fetching the access token.
   */
  def getAccessTokenHeaders: Seq[(String, String)] = Seq.empty

  /**
   * URL parameters for fetching the access token.
   *
   * @param code the code given to us by the provider after it
   *             redirects back to our handler after user authentication.
   *
   * @param handlerUrl the URL the client redirected us to after authentication
   */
  def getAccessTokenParams(code: String, handlerUrl: String): Map[String, Seq[String]] = Map(
    OAuth2Constants.ClientId -> Seq(settings.clientId),
    OAuth2Constants.ClientSecret -> Seq(settings.clientSecret),
    OAuth2Constants.GrantType -> Seq(OAuth2Constants.AuthorizationCode),
    OAuth2Constants.Code -> Seq(code),
    OAuth2Constants.RedirectUri -> Seq(handlerUrl)
  )

  /**
   * Parse the provider's user info data.
   *
   * @param data the provider's user data response body
   * @return a user data structure
   */
  def parseUserInfo(data: String): Option[UserData]

  /**
   * Parse the provider's access token info.
   *
   * @param data the provider's access token response body.
   * @return access token data
   */
  def parseAccessInfo(data: String): Option[OAuth2Info] = {
    try {
      val json = Json.parse(data)
      for {
        accessToken <- (json \ OAuth2Constants.AccessToken).asOpt[String]
      } yield OAuth2Info(
        accessToken,
        (json \ OAuth2Constants.TokenType).asOpt[String],
        (json \ OAuth2Constants.ExpiresIn).asOpt[Int],
        (json \ OAuth2Constants.RefreshToken).asOpt[String]
      )
    } catch {
      case e: JsonParseException => None
    }
  }

  /**
   * Get provider-specific settings.
   */
  protected def settings: ProviderSettings = ProviderSettings(
    getSetting(OAuth2Settings.ClientId),
    getSetting(OAuth2Settings.ClientSecret),
    getSetting(OAuth2Settings.AuthorizationUrl),
    getSetting(OAuth2Settings.AccessTokenUrl),
    getSetting(OAuth2Settings.UserInfoUrl),
    getSetting(OAuth2Settings.Scope)
  )

  private def getSetting(key: String): String = config.get[String](s"oauth2.$name.$key")
}

