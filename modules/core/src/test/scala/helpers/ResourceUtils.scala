package helpers

import models.EntityType
import play.api.libs.json.{JsValue, Json}

import scala.io.{Codec, Source}

trait ResourceUtils {
  protected def readResource(v: EntityType.Value): JsValue = readResource(v.toString + ".json")

  protected def readResource(name: String): JsValue = Json.parse(resourceAsString(name))

  protected def resourceAsString(name: String): String = Source.fromInputStream(getClass
    .getClassLoader.getResourceAsStream(name))(Codec.UTF8)
    .getLines().mkString("\n")
}
