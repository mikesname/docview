package models

trait Hierarchical[+C <: Hierarchical[C]] extends Model {

  /**
    * The parent item of this item.
    */
  def parent: Option[C]

  /**
    * List of ancestor items 'above' this one, including the parent.
    */
  def ancestors: Seq[C] =
    (parent.map(p => p +: p.ancestors) getOrElse Seq.empty[C]).distinct

  /**
    * Get the top level of the hierarchy, which may or may
    * not be the current item.
    */
  def topLevel: C = ancestors.lastOption.getOrElse(this.asInstanceOf[C])

  /**
    * Determine if an item is top level, i.e. has no parents.
    */
  def isTopLevel: Boolean = parent.isEmpty
}

