package models.base

import play.api.test.PlaySpecification
import models.{Entity, EntityType, _}
import eu.ehri.project.definitions.Ontology
import play.api.libs.json.{JsObject, Json}
import play.api.i18n.{Lang, MessagesApi, MessagesImpl}
import Description._

case class TestDescriptionF(
  id: Option[String],
  isA: EntityType.Value = EntityType.DocumentaryUnitDescription,
  name: String,
  languageCode: String,
  @relation(Ontology.HAS_ACCESS_POINT)
  creationProcess: CreationProcess.Value = CreationProcess.Manual,
  accessPoints: Seq[AccessPointF] = Nil,
  maintenanceEvents: Seq[MaintenanceEventF] = Nil,
  @relation(Ontology.HAS_UNKNOWN_PROPERTY)
  unknownProperties: List[Entity] = Nil
) extends ModelData
  with Description {
  def toSeq = Seq.empty
  def displayText = None
}

case class TestModelF(
  id: Option[String] = None,
  isA: EntityType.Value = EntityType.DocumentaryUnit,
  @relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: List[TestDescriptionF] = Nil
) extends ModelData
  with Described {

  type D = TestDescriptionF
}

case class TestModel(
  data: TestModelF,
  meta: JsObject = Json.obj()
) extends Model with DescribedModel {

  type T = TestModelF
}

class ModelSpec extends PlaySpecification with play.api.i18n.I18nSupport {

  implicit val application = new play.api.inject.guice.GuiceApplicationBuilder().build
  implicit val messagesApi = application.injector.instanceOf[MessagesApi]

  val accessPoint1 = AccessPoint(data = AccessPointF(
    id = Some("ap1"),
    accessPointType = AccessPointF.AccessPointType.PersonAccess,
    name = "AP1"
  ))

  val accessPoint2 = AccessPoint(data = AccessPointF(
    id = Some("ap2"),
    accessPointType = AccessPointF.AccessPointType.CorporateBodyAccess,
    name = "AP2"
  ))

  val accessPoint3 = AccessPoint(data = AccessPointF(
    id = Some("ap3"),
    accessPointType = AccessPointF.AccessPointType.FamilyAccess,
    name = "AP3"
  ))

  val testModel = TestModel(
    TestModelF(
      id = Some("id1"),
      descriptions = List(
        TestDescriptionF(
          id = Some("did1"),
          name = "name1",
          languageCode = "eng",
          accessPoints = Seq(accessPoint1.data, accessPoint2.data)
        ),
        TestDescriptionF(
          id = Some("did2"),
          name = "name2",
          languageCode = "fra"
        )
      )
    )
  )

  "A Model instance" should {
    "pick the right locale-dependent name" in {
      testModel.toStringLang(MessagesImpl(Lang("en"), messagesApi)) must equalTo("name1")
      testModel.toStringLang(MessagesImpl(Lang("fr"), messagesApi)) must equalTo("name2")
      testModel.toStringLang(MessagesImpl(Lang("en", "GB"), messagesApi)) must equalTo("name1")
    }

    "count descriptions properly" in {
      testModel.descriptions.size must equalTo(2)
    }

    "order descriptions according to a supplied locale" in {
      val test = testModel.copy(
        data = testModel.data.copy(
          descriptions = testModel.data.descriptions.reverse))
      test.data.descriptions.headOption.map(_.languageCode) must beSome("fra")
      test.data.currentLangFirstDescriptions(messagesApi.preferred(Seq(Lang("en"))))
                .headOption.map(_.languageCode) must beSome("eng")
    }

    "categorise access point links properly" in {
      val links = Seq(
        Link(
          data = LinkF(
            id = Some("ln1"),
            linkType = LinkF.LinkType.Associative
          ),
          bodies = Seq(accessPoint1)
        )
      )
      testModel.accessPointLinks(links).headOption must beSome.which { case (link, ap) =>
        link.id must_== "ln1"
        ap.id must beSome("ap1")
      }
      testModel.externalLinks(links) must beEmpty
      testModel.annotationLinks(links) must beEmpty
    }

    "categorise external links properly" in {
      val links = Seq(
        Link(
          data = LinkF(
            id = Some("ln1"),
            linkType = LinkF.LinkType.Associative
          ),
          bodies = Seq(accessPoint3)
        )
      )
      testModel.externalLinks(links).headOption must beSome.which { ext =>
        ext.id must_== "ln1"
      }
      testModel.accessPointLinks(links) must beEmpty
      testModel.annotationLinks(links) must beEmpty
    }

    "categorise annotation links properly" in {
      val links = Seq(
        Link(
          data = LinkF(
            id = Some("ln1"),
            linkType = LinkF.LinkType.Associative
          ),
          targets = Seq(accessPoint3)
        )
      )
      testModel.annotationLinks(links).headOption must beSome.which { ext =>
        ext.id must_== "ln1"
      }
      testModel.accessPointLinks(links) must beEmpty
      testModel.externalLinks(links) must beEmpty
    }

    "categorise access point links by type" in {
      val links = Seq(
        Link(
          data = LinkF(
            id = Some("ln1"),
            linkType = LinkF.LinkType.Associative
          ),
          bodies = Seq(accessPoint1)
        )
      )
      val byType = testModel.accessPointLinksByType(links)
      byType.size must_== 1
      byType.toSeq.headOption must beSome.which { case (t, aps) =>
        t must_== AccessPointF.AccessPointType.PersonAccess
        aps.headOption must beSome.which { case (link, ap) =>
          link.id must_== "ln1"
          ap.id must beSome("ap1")
        }
      }
      testModel.annotationLinks(links) must beEmpty
      testModel.externalLinks(links) must beEmpty
    }
  }
}
