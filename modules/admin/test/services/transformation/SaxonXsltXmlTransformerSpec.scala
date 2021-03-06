package services.transformation

import akka.actor.ActorSystem
import akka.stream.Materializer
import helpers.ResourceUtils
import play.api.libs.json.Json
import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext

class SaxonXsltXmlTransformerSpec extends PlaySpecification with ResourceUtils {

  private implicit val ctx: ExecutionContext = scala.concurrent.ExecutionContext.global
  private implicit val as: ActorSystem = ActorSystem.create("test")
  private implicit val mat: Materializer = Materializer(as)

  private val testPayload = """<ead>
      |  <eadheader>
      |    <eadid>test-id</eadid>
      |  </eadheader>
      |</ead>
      |""".stripMargin

  "XML transformer should" should {
    "transform a simple file" in {
      val transformer = SaxonXsltXmlTransformer()
      val map = resourceAsString("simple-mapping.xsl")
      val out = transformer.transform(testPayload, map, Json.obj())
      out must contain("http://www.loc.gov/ead")
    }

    "handle parameters" in {
      val transformer = SaxonXsltXmlTransformer()
      val map = resourceAsString("simple-mapping.xsl")
      val out = transformer.transform(testPayload, map, Json.obj("test-param" -> "example", "test-value" -> "Hello, world"))
      out must contain("example")
      out must contain("Hello, world")
    }
  }
}
