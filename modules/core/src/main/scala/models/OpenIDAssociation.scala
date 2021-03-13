package models


case class OpenIDAssociation(
  id: String,
  url: String,
  user: Option[Account]
)