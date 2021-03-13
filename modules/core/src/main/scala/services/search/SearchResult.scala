package services.search

import utils.Page


case class SearchResult[+T](
  page: Page[T],
  params: SearchParams,
  facets: Seq[AppliedFacet] = Seq.empty,
  facetClasses: Seq[FacetClass[Facet]] = Seq.empty,
  facetInfo: Map[String, Any] = Map.empty,
  spellcheck: Option[(String,String)] = None
) {

  /**
   * Convenience method to convert this search result into
   * one with a different item type.
   */
  def mapItems[B](f: (T) => B): SearchResult[B] = copy(page = page.copy(items = page.items.map(f)))

  /**
   * Convenience method to replace items.
   */
  def withItems[B](items: Seq[B]): SearchResult[B] = copy(page = page.copy(items = items))

  def isEmpty: Boolean = page.isEmpty

  def nonEmpty: Boolean = page.nonEmpty
}

object SearchResult {
  def empty[T]: SearchResult[T] = new SearchResult(Page.empty[T], SearchParams.empty)
}
