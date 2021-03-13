package services.oauth2

import auth.oauth2.providers.OAuth2Provider
import auth.oauth2.{OAuth2Info, UserData}

import scala.concurrent.Future

trait OAuth2Service {
  /**
   * Second step after OAuth2 initial redirect to provider. Use code from provider
   * to fetch access token info.
   *
   * @param provider the OAuth2 provider
   * @param handlerUrl the redirect URL
   * @param code the provider's code
   * @return the OAuth2 access info
   */
  def getAccessToken(provider: OAuth2Provider, handlerUrl: String, code: String): Future[OAuth2Info]

  /**
   * Third step, fetch the user's info, using the access data retrieved in the
   * second step.
   *
   * @param provider the provider
   * @param info the access info
   * @return the user's data
   */
  def getUserData(provider: OAuth2Provider, info: OAuth2Info): Future[UserData]
}
