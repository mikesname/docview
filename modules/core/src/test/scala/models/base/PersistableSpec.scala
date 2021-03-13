package models.base

import play.api.test.PlaySpecification
import models.{DatePeriod, DatePeriodF, Persistable}
import services.data.ErrorSet


class PersistableSpec extends PlaySpecification {

  import play.api.libs.json._
  import play.api.data.Form
  import play.api.data.Forms._

  case class SomeData(
    id: Option[String] = None,
    @models.relation("dates")
    dates: List[DatePeriodF] = Nil
  ) extends Persistable

  object SomeData {
    val form = Form(
      mapping(
        "id" -> optional(nonEmptyText),
        "dates" -> list(DatePeriod.form.mapping)
      )(SomeData.apply)(SomeData.unapply)
    )
  }

  val errorSet = Json.obj(
    "errors" -> Json.obj(),
    "relationships" -> Json.obj(
      "dates" -> Json.arr(
        Json.obj(
          "errors" -> Json.obj(
            "startDate" -> Json.arr(
              "Missing value"
            )
          ),
          "relationships" -> Json.obj()
        )
      )
    )
  )

  "ErrorSet" should {
    "validate correctly" in {
      errorSet.validate[ErrorSet].asOpt must beSome
    }
  }

  "Persistable class" should {
    "convert an ErrorSet to form errors" in {
      val someData = SomeData(
        id = Some("test"),
        dates = List(DatePeriodF(id = Some("foo")))
      )

      val errorForm: Form[SomeData] = someData.getFormErrors(errorSet.as[ErrorSet], SomeData.form)
      errorForm.hasErrors must beTrue
      errorForm.errors.headOption must beSome.which { err =>
        err.key must equalTo("dates[0].startDate")
        err.messages(0) must equalTo("Missing value")
      }
    }
  }

}
