package integration.api.v1

import helpers.IntegrationTestRunner
import models.EntityType
import models.api.v1.JsonApiV1
import org.everit.json.schema.ValidationException
import org.everit.json.schema.loader.SchemaLoader
import org.json.{JSONObject, JSONTokener}
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json._
import play.api.test.FakeRequest
import utils.FieldFilter


class ApiV1Spec extends IntegrationTestRunner {

  private val apiRoutes = controllers.api.v1.routes.ApiV1

  private def validateJson(json: JsValue): Unit = {
    val is = getClass.getResourceAsStream("/jsonapi-schema.json")
    try {
      val rawSchema = new JSONObject(new JSONTokener(is))
      val schema = SchemaLoader.load(rawSchema)
      schema.validate(new JSONObject(Json.prettyPrint(json)))
    } catch {
      case se: ValidationException =>
        println(se.getMessage)
        import scala.jdk.CollectionConverters._
        se.getCausingExceptions.asScala.foreach { e =>
          println(" - " + e.getMessage)
        }
        throw se
    } finally {
      is.close()
    }
  }

  "API/V1" should {
    "say forbodden when authorization enabled" in new ITestApp(
      Map(
        "ehri.api.v1.authorization.enabled" -> true,
        "ehri.api.v1.authorization.tokens" -> List("allowed")
      )
    ) {
      val idx1 = FakeRequest(apiRoutes.search())
        .withHeaders(ACCEPT -> ContentTypes.JSON).call()
      status(idx1) must_== FORBIDDEN
      contentAsJson(idx1) \ "errors" \ 0 \ "detail" must_== JsDefined(JsString("Token required"))

      val idx2 = FakeRequest(apiRoutes.search())
        .withHeaders(HeaderNames.AUTHORIZATION -> "Bearer allowed")
        .call()
      status(idx2) must_== OK
    }

    "give Not Acceptable with a modified Accept header" in new ITestApp {
      val idx = FakeRequest(apiRoutes.search())
        .withHeaders(HeaderNames.ACCEPT -> (JsonApiV1.JSONAPI_MIMETYPE + ";encoding=utf8"))
        .call()
      status(idx) must_== NOT_ACCEPTABLE
    }

    "forbid fetching protected items" in new ITestApp {
      val fetch = FakeRequest(apiRoutes.fetch("c1")).call()
      status(fetch) must_== FORBIDDEN
      validateJson(contentAsJson(fetch))
    }

    "allow fetching items" in new ITestApp {
      val fetch = FakeRequest(apiRoutes.fetch("c4")).call()
      status(fetch) must_== OK
      validateJson(contentAsJson(fetch))
      contentAsJson(fetch) \ "data" \ "attributes" \ "descriptions" \ 0 \
          "scopeAndContent" must_== JsDefined(JsString("Some description text for c4"))
    }

    "send CORS headers" in new ITestApp {
      // `/api` is configured as a prefix in play.filters.cors.pathPrefixes
      val testOrigin = "http://example.com"
      val testHost = "localhost"

      val api = FakeRequest(apiRoutes.search())
        .withHeaders(HeaderNames.HOST -> testHost, HeaderNames.ORIGIN -> testOrigin)
        .call()
      header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, api) must beSome(testOrigin)

      val nonApi = FakeRequest(GET, "/")
        .withHeaders(HeaderNames.HOST -> testHost, HeaderNames.ORIGIN -> testOrigin)
        .call()
      header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, nonApi) must beNone
    }

    "contain the right metadata" in new ITestApp {
      val fetch = FakeRequest(apiRoutes.fetch("r1")).call()
      status(fetch) must_== OK
      validateJson(contentAsJson(fetch))
      contentAsJson(fetch) \ "data" \ "meta" \ "subitems" must_== JsDefined(JsNumber(3))
    }

    "contain the right attributes with sparse fieldsets" in new ITestApp {
      val fetch = FakeRequest(apiRoutes.fetch("r1",
        fields = Seq(FieldFilter(EntityType.Repository, Seq("name"))))).call()
      status(fetch) must_== OK
      validateJson(contentAsJson(fetch))
      val attrs = (contentAsJson(fetch) \ "data" \ "attributes")
        .asOpt[JsObject].map(_.value) must beSome.which { obj =>
        obj must haveKey("name")
        obj must not(haveKey("otherFormsOfName"))
      }
    }

    "allow searching all items" in new ITestApp {
      val search = FakeRequest(apiRoutes.search()).call()
      status(search) must_== OK
      validateJson(contentAsJson(search))
    }

    "allow searching in items" in new ITestApp {
      val search = FakeRequest(apiRoutes.searchIn("r1")).call()
      status(search) must_== OK
      validateJson(contentAsJson(search))
    }

    "include context when search in items" in new ITestApp {
      val search = FakeRequest(GET, apiRoutes.searchIn("r1").url + "?limit=1&page=2").call()
      status(search) must_== OK
      validateJson(contentAsJson(search))
      contentAsJson(search) \ "included" \ 0 \ "id" must_== JsDefined(JsString("r1"))
    }
  }
}
