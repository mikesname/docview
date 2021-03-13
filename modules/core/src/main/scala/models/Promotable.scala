package models


trait Promotable extends Accessible {
  def isPromotable: Boolean
  def promoters: Seq[UserProfile]
  def demoters: Seq[UserProfile]

  def isPromoted: Boolean = promoters.size > demoters.size
  def isPromotedBy(user: UserProfile): Boolean = promoters.exists(_.id == user.id)
  def isDemotedBy(user: UserProfile): Boolean = demoters.exists(_.id == user.id)
  def promotionScore: Int = promoters.size - demoters.size
}
