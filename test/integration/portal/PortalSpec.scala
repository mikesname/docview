package integration.portal

import java.util.zip.{ZipEntry, ZipInputStream}
import akka.util.ByteString
import controllers.portal.ReversePortal
import cookies.{SessionPreferences, SessionPrefs}
import helpers.IntegrationTestRunner
import play.api.libs.json.Json
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import services.search.SearchParams


class PortalSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  private val portalRoutes: ReversePortal = controllers.portal.routes.Portal

  override def getConfig = Map("recaptcha.skip" -> true)

  "Portal views" should {
    "show index page" in new ITestApp {
      val doc = FakeRequest(portalRoutes.index()).call()
      status(doc) must equalTo(OK)
    }

    "show index page in other languages" in new ITestApp {
      val doc = FakeRequest(portalRoutes.index())
        .withCookies(Cookie("PLAY_LANG", "fr", sameSite = Some(Cookie.SameSite.Lax)))
        .call()
      contentAsString(doc) must contain("Bienvenue sur")
    }

    "show contact page" in new ITestApp {
      val doc = FakeRequest(portalRoutes.contact()).call()
      status(doc) must equalTo(OK)
    }

    "send 301 when an item has been renamed" in new ITestApp {
      val oldRoute = controllers.portal.routes.DocumentaryUnits.browse("OLD")
      val newRoute = controllers.portal.routes.DocumentaryUnits.browse("NEW")
      val before = FakeRequest(oldRoute).call()
      status(before) must equalTo(NOT_FOUND)

      movedPages += oldRoute.url -> newRoute.url
      val rename = FakeRequest(oldRoute).call()
      status(rename) must equalTo(MOVED_PERMANENTLY)
      redirectLocation(rename) must beSome(newRoute.url)
    }

    "allow setting view preferences" in new ITestApp {
      val prefJson = FakeRequest(portalRoutes.prefs()).withUser(privilegedUser).call()
      (contentAsJson(prefJson) \ SessionPrefs.SHOW_USER_CONTENT).as[Boolean] must beTrue
      val setPrefs = FakeRequest(portalRoutes.updatePrefs())
        .withUser(privilegedUser)
        .withFormUrlEncodedBody(SessionPrefs.SHOW_USER_CONTENT -> "false")
        .withCsrf
        .call()
      status(setPrefs) must equalTo(SEE_OTHER)
      session(setPrefs).get(SessionPreferences.DEFAULT_STORE_KEY) must beSome.which { jsStr =>
        val json = Json.parse(jsStr)
        (json \ SessionPrefs.SHOW_USER_CONTENT).as[Boolean] must beFalse
      }
    }

    "allow setting the language" in new ITestApp {
      val about = FakeRequest(portalRoutes.about()).call()
      val setLang = FakeRequest(portalRoutes.changeLocale("de")).call()
      status(setLang) must equalTo(SEE_OTHER)
      cookies(setLang).get("PLAY_LANG") must beSome.which { cookie =>
        cookie must_== Cookie("PLAY_LANG", "de", httpOnly = false, sameSite = Some(Cookie.SameSite.Lax))
      }
    }

    "view docs" in new ITestApp {
      val doc = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4")).call()
      status(doc) must equalTo(OK)
    }

    "export docs as EAD" in new ITestApp {
      val ead = FakeRequest(controllers.portal.routes.DocumentaryUnits.export("c4")).call()
      status(ead) must equalTo(OK)
      contentType(ead) must beSome.which { ct =>
        ct must equalTo("text/xml")
      }
      contentAsString(ead) must contain("<ead")
    }

    "view repositories" in new ITestApp {
      val doc = FakeRequest(controllers.portal.routes.Repositories.browse("r1")).call()
      status(doc) must equalTo(OK)
    }

    "export country contents as EAG zip" in new ITestApp {
      val zip = FakeRequest("GET",
        controllers.portal.routes.Countries.export("nl").url + "?format=eag").call()
      val bytes = contentAsBytes(zip)
      status(zip) must equalTo(OK)
      contentType(zip) must beSome.which { ct =>
        ct must equalTo("application/zip")
      }
      zipEntries(bytes).size must_== 2
    }

    "export repositories as EAG" in new ITestApp {
      val eag = FakeRequest(controllers.portal.routes.Repositories.export("r1")).call()
      status(eag) must equalTo(OK)
      contentType(eag) must beSome.which { ct =>
        ct must equalTo("text/xml")
      }
      contentAsString(eag) must contain("<eag")
    }

    "export repository contents as EAD zip" in new ITestApp {
      val zip = FakeRequest("GET",
        controllers.portal.routes.Repositories.export("r1").url + "?format=ead").call()
      val bytes = contentAsBytes(zip)
      status(zip) must equalTo(OK)
      contentType(zip) must beSome.which { ct =>
        ct must equalTo("application/zip")
      }
      zipEntries(bytes).size must_== 3
    }

    "view historical agents" in new ITestApp {
      val doc = FakeRequest(controllers.portal.routes.HistoricalAgents.browse("a1")).call()
      status(doc) must equalTo(OK)
    }

    "view historical agent related items" in new ITestApp {
      // NB: we need to be a privileges user here since connected items are restricted
      val doc = FakeRequest(controllers.portal.routes.HistoricalAgents.browse("a1")).withUser(privilegedUser).call()
      contentAsString(doc) must contain(controllers.portal.routes.DocumentaryUnits.browse("c1").url)
      contentAsString(doc) must contain(controllers.portal.routes.DocumentaryUnits.browse("c3").url)
    }

    "export historical agents as EAC" in new ITestApp {
      val eac = FakeRequest(controllers.portal.routes.HistoricalAgents.export("a1")).call()
      status(eac) must equalTo(OK)
      contentType(eac) must beSome.which { ct =>
        ct must equalTo("text/xml")
      }
      contentAsString(eac) must contain("<eac-cpf")
    }

    "allow exporting as SKOS" in new ITestApp {
      val skos = FakeRequest(controllers.portal.routes.Vocabularies.exportSkos("cvoc1"))
        .withUser(privilegedUser).call()
      status(skos) must equalTo(OK)
      contentType(skos) must beSome.which { ct =>
        ct must equalTo("text/turtle")
      }
      contentAsString(skos) must contain("<http://data.ehri-project.eu/vocabularies/cvoc1>")
    }

    "view item history" in new ITestApp {
      val history = FakeRequest(portalRoutes.itemHistory("c4")).call()
      status(history) must equalTo(OK)
    }

    "fetch external pages" in new ITestApp {
      val faq = FakeRequest(portalRoutes.externalPage("faq")).call()
      status(faq) must equalTo(OK)
      contentAsString(faq) must contain(mockdata.externalPages("faq").toString())
    }

    "return 404 for external pages with a malformed id (bug #635)" in new ITestApp {
      val faq = FakeRequest(portalRoutes.externalPage(",b.name,k.length")).call()
      status(faq) must equalTo(NOT_FOUND)
    }

    "allow search filtering for non-logged in users" in new ITestApp {
      val filter = FakeRequest(controllers.portal.routes.Portal.filterItems(SearchParams(query = Some("C"))))
        .call()
      status(filter) must equalTo(OK)
    }
  }

  private def zipEntries(bytes: ByteString): List[ZipEntry] = {
    val is = bytes.iterator.asInputStream
    val zis = new ZipInputStream(is)
    try {
      LazyList
        .continually(zis.getNextEntry)
        .takeWhile(_ != null)
        .toList
    } finally {
      zis.close()
      is.close()
    }
  }
}
