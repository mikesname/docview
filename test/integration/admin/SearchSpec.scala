package integration.admin

import controllers.admin.{IndexTypes, Indexing}
import defines.EntityType
import helpers._
import models.{Group, GroupF, UserProfile, UserProfileF}
import play.api.http.MimeTypes
import play.api.libs.json.{JsString, Json}
import play.api.test.{FakeRequest, WithServer}
import utils.search.SearchConstants


/**
  * Spec to test various page views operate as expected.
  */
class SearchSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name = "test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name = "Administrators")))
  )


  "Search views" should {

    "search for hierarchical items with no query should apply a top-level filter" in new ITestApp {
      val search = FakeRequest(controllers.units.routes.DocumentaryUnits.search())
        .withUser(privilegedUser)
        .call()
      status(search) must equalTo(OK)
      searchParamBuffer
        .last.filters.get(SearchConstants.TOP_LEVEL) must equalTo(Some(true))
    }

    "search for hierarchical item with a query should not apply a top-level filter" in new ITestApp {
      val search = FakeRequest(GET, controllers.units.routes.DocumentaryUnits.search().url + "?q=foo")
        .withUser(privilegedUser)
        .call()
      status(search) must equalTo(OK)
      searchParamBuffer
        .last.filters.get(SearchConstants.TOP_LEVEL) must equalTo(None)
    }
  }

  "Search index mediator" should {
    val port = 9902
    "perform indexing correctly via Websocket endpoint" in new WithServer(app = appBuilder.build(), port = port) {
      val cmd = List(EntityType.DocumentaryUnit)
      val data = IndexTypes(cmd)
      val wsUrl = s"ws://127.0.0.1:$port${controllers.admin.routes.Indexing.indexer().url}"
      val ws = WebSocketClientWrapper(wsUrl,
        headers = Map(AUTH_TEST_HEADER_NAME -> testAuthToken(privilegedUser.id)))
      ws.client.connectBlocking()
      ws.client.send(Json.stringify(Json.toJson(data)).getBytes("UTF-8"))

      eventually {
        ws.messages.contains(JsString(Indexing.DONE_MESSAGE).toString)
        indexEventBuffer.lastOption must beSome.which { bufcmd =>
          bufcmd must equalTo(cmd.toString())
        }
      }
    }
  }

  "Search metrics" should {
    "response to JSON" in new ITestApp {
      val repoMetrics = FakeRequest(controllers.admin.routes.Metrics.repositoryCountries())
        .withUser(privilegedUser)
        .accepting(MimeTypes.JSON)
        .call()
      status(repoMetrics) must equalTo(OK)
      contentType(repoMetrics) must equalTo(Some(MimeTypes.JSON))
    }
  }
}
