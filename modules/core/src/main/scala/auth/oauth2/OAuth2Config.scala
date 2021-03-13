package auth.oauth2

import auth.oauth2.providers.OAuth2Provider

trait OAuth2Config {
  def providers: Seq[OAuth2Provider]
}
