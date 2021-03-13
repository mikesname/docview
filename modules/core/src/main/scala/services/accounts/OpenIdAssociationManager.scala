package services.accounts

import models.OpenIDAssociation

import scala.concurrent.Future

trait OpenIdAssociationManager {

  def findByUrl(url: String): Future[Option[OpenIDAssociation]]

  def findAll: Future[Seq[OpenIDAssociation]]

  def addAssociation(id: String, assoc: String): Future[Option[OpenIDAssociation]]
}
