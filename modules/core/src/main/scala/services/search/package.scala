package services

import play.api.libs.json.Format
import play.api.mvc.RequestHeader
import utils.EnumUtils

package object search {

  type FacetBuilder = RequestHeader => Seq[FacetClass[Facet]]

  object FacetQuerySort extends Enumeration {
    val Name = Value("name")
    val Count = Value("count")

    implicit val format: Format[FacetQuerySort.Value] = EnumUtils.enumFormat(FacetQuerySort)
  }

  /**
    * Get the URL without any of the given facet classes.
    *
    * @param fcs  the set of facet classes
    * @param path the current path
    * @param qs   the current query string
    * @return a new URL with the facet classes removed
    */
  def pathWithoutFacets(fcs: Seq[FacetClass[_]], path: String, qs: Map[String, Seq[String]]): String = {
    val params = fcs.map(_.param)
    utils.http.joinPath(path, qs.collect {
      case (q, values) if !params.contains(q) => q -> values
    })
  }

  /**
    * Get the path without a specific facet value.
    *
    * @param fc   the facet class
    * @param f    the specific facet
    * @param path the current path
    * @param qs   the current query string
    * @return a new URL with the facet removed
    */
  def pathWithoutFacet(fc: FacetClass[_], f: String, path: String, qs: Map[String, Seq[String]]): String =
    utils.http.joinPath(path, qs.collect {
      // Specific facets
      case (q, values) if q == fc.param => q -> values.filter(_ != f)
      // Generic facets
      case (k, values) if k == SearchParams.FACET => k -> values.filter(_ != s"${fc.param}:$f")
      case pair => pair
    })

  /**
    * Get the path with a specific facet added.
    *
    * @param fc   the facet class
    * @param f    the specific facet
    * @param path the current path
    * @param qs   the current query string
    * @return a new URL with the facet added
    */
  def pathWithFacet(fc: FacetClass[_], f: String, path: String, qs: Map[String, Seq[String]]): String =
    utils.http.joinPath(path, if (qs.contains(fc.param)) {
      qs.collect {
        case (q, values) if q == fc.param => q -> (values :+ f).distinct.sorted
        case pair => pair
      }
    } else qs.updated(fc.param, Seq(f)))

  /**
    * Get the path with a generic facet added.
    *
    * @param fc   the facet class
    * @param f    the generic facet
    * @param path the current path
    * @param qs   the current query string
    * @return a new URL with the facet added
    */
  def pathWithGenericFacet(fc: FacetClass[_], f: String, path: String, qs: Map[String, Seq[String]]): String =
    utils.http.joinPath(path, if (qs.contains(SearchParams.FACET)) {
      qs.collect {
        case (k, values) if k == SearchParams.FACET => k -> (values :+ s"${fc.param}:$f").distinct.sorted
        case pair => pair
      }
    } else qs.updated(SearchParams.FACET, Seq(s"${fc.param}:$f")))
}
