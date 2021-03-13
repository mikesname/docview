package services.search

/**
 * A paged list of facets.
 */
case class FacetPage[+A](
  fc: FacetClass[Facet],
  items: Seq[A],
  offset: Int,
  limit: Int,
  total: Int
) extends utils.AbstractPage[A]
