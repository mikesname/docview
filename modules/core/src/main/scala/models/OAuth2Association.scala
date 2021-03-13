package models

case class OAuth2Association(
  id: String,
  providerId: String,
  provider: String,
  user: Option[Account]
)