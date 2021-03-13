package models


trait Holder[+T] extends Model {

  /**
    * Number of items 'below' this one.
    */
  def childCount: Option[Int] =
    meta.value.get(Entity.CHILD_COUNT).flatMap(_.asOpt[Int])

  /**
    * Whether this item has children.
    */
  def hasChildren: Boolean = childCount.exists(_ > 0)
}
