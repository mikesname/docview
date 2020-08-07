package views.admin

import helpers.ResourceUtils
import models.{DocumentaryUnitDescription, DocumentaryUnitF, EntityType}
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import play.api.test.PlaySpecification
import play.twirl.api.Html


class FormSerializationSpec extends PlaySpecification with ResourceUtils {

  def formData(html: Html): Map[String,String] = {
    val elements: java.util.ArrayList[Element] = Jsoup.parse(html.body).select("input")
    import scala.jdk.CollectionConverters._
    elements.asScala.map(e => e.attr("name") -> e.attr("value")).toMap
  }

  "Documentary Unit form views" should {
    "serialize correctly" in {
      val data = readResource(EntityType.DocumentaryUnit).as[DocumentaryUnitF].descriptions.head
      val form = DocumentaryUnitDescription.form.fillAndValidate(data)
      val html = views.html.admin.documentaryUnit.hiddenDescriptionForm(form(""))
      val map = formData(html)
      val bound = DocumentaryUnitDescription.form.bind(map)
      form.value must beSome
      form.value must_== bound.value
    }
  }
}
