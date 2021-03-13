package services.search

import play.api.test.PlaySpecification

import scala.collection.immutable.ListMap

class FacetSpec extends PlaySpecification {

  private val testFacetClass = FieldFacetClass(
    key = "lang",
    name = "Language",
    param = "lang"
  )

  private val qs: Map[String,Seq[String]] = ListMap("lang" -> Seq("de", "fr"))


  "Facet query string ops" should {
    "allow adding facet to a query" in {
      val withFacet: String = pathWithFacet(testFacetClass, "en", "/foobar", qs)
      withFacet must equalTo("/foobar?lang=de&lang=en&lang=fr")
    }

    "allow removing facets from a query" in {
      val withoutFacet: String = pathWithoutFacet(testFacetClass, "fr", "/foobar", qs)
      withoutFacet must equalTo("/foobar?lang=de")
    }

    "allow adding generic facet to a query" in {
      val withFacet: String = pathWithGenericFacet(testFacetClass, "en", "/foobar", qs)
      withFacet must equalTo("/foobar?lang=de&lang=fr&facet=lang%3Aen")
    }

    "allow removing generic facets from a query" in {
      val withoutFacet: String = pathWithoutFacet(testFacetClass, "fr", "/foobar",
        ListMap("facet" -> Seq("lang:de", "lang:fr")))
      withoutFacet must equalTo("/foobar?facet=lang%3Ade")
    }
  }
}
