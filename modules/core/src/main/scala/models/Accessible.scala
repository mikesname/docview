package models


trait Accessible extends Model {
  /**
    * Get the set of accessors to whom this item is visible.
    */
  def accessors: Seq[Accessor]

  /**
    * Determine if a given item is private to a particular user.
    */
  def privateTo(accessor: Accessor): Boolean =
    accessors.size == 1 && accessors.head.id == accessor.id

  def latestEvent: Option[SystemEvent]
}
