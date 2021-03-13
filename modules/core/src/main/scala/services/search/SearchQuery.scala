package services.search

import models.UserProfile
import play.api.i18n.Lang
import utils.PageParams

/**
  * A representation of a search query.
  */
case class SearchQuery(
  params: SearchParams = SearchParams.empty,
  paging: PageParams = PageParams.empty,
  filters: Map[String, Any] = Map.empty,
  withinIds: Option[Seq[String]] = None,
  appliedFacets: Seq[AppliedFacet] =  Seq.empty,
  facetClasses: Seq[FacetClass[Facet]] = Seq.empty,
  extraParams: Map[String, Any] = Map.empty,
  mode: SearchMode.Value = SearchMode.DefaultAll,
  user: Option[UserProfile] = None,
  lang: Lang
)
