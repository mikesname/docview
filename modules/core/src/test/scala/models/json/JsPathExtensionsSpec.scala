package models.json

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.test.PlaySpecification

class JsPathExtensionsSpec extends PlaySpecification {

  "JsPath with extensions" should {
    "allow performing reads with value equality constraints" in {
      case class TestData(field1: String, field2: String)
      val testValidJson: JsValue = Json.obj(
        "field1" -> "good",
        "field2" -> "anotherval"
      )
      val testInvalidJson: JsValue = Json.obj(
        "field1" -> "bad",
        "field2" -> "anotherval"
      )
      val testJsonReads: Reads[TestData] = (
        (__ \ "field1").readIfEquals("good") and
        (__ \ "field2").read[String]
      )(TestData.apply _)
      testValidJson.validate(testJsonReads).asOpt must beSome.which { value =>
        value must equalTo(TestData("good", "anotherval"))
      }
      testInvalidJson.validate(testJsonReads).asEither must beLeft
    }

    "allow reading nullable lists" in {
      case class TestData(field1: String, field2: Seq[String])
      // valid
      val testJson1: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr("foo")
      )
      // valid - list will be empty
      val testJson2: JsValue = Json.obj(
        "field1" -> "val"
      )
      // invalid, list exists but is wrong type
      val testJson3: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr(
          Json.obj("foo" -> "bar")
        )
      )
      val testJsonReads: Reads[TestData] = (
        (__ \ "field1").read[String] and
        (__ \ "field2").readSeqOrEmpty[String]
      )(TestData.apply _)

      testJson1.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2.headOption must beSome.which { value =>
          value must equalTo("foo")
        }
      }
      testJson2.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2 must beEmpty
      }
      testJson3.validate(testJsonReads).asEither must beLeft
    }

    "allow writing nullable lists" in {
      case class TestData(field1: String, field2: Seq[String])
      val item1 = TestData("foo", Seq("bar"))
      val item2 = TestData("foo", Seq.empty)

      val testJsonWrites: Writes[TestData] = (
        (__ \ "field1").write[String] and
        (__ \ "field2").writeSeqOrEmpty[String]
      )(unlift(TestData.unapply))

      Json.toJson(item1)(testJsonWrites) must equalTo(Json.obj(
        "field1" -> "foo",
        "field2" -> Json.arr("bar")
      ))

      Json.toJson(item2)(testJsonWrites) must equalTo(Json.obj(
        "field1" -> "foo"
      ))
    }

    "allow reading the head of a nullable list" in {
      case class TestData(field1: String, field2: Option[String])
      // valid
      val testJson1: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr("foo")
      )
      // valid - list will be empty
      val testJson2: JsValue = Json.obj(
        "field1" -> "val"
      )
      // invalid, list exists but is wrong type
      val testJson3: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr(
          Json.obj("foo" -> "bar")
        )
      )
      val testJsonReads: Reads[TestData] = (
        (__ \ "field1").read[String] and
          (__ \ "field2").readHeadNullable[String]
        )(TestData.apply _)

      testJson1.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2 must beSome.which { value =>
          value must equalTo("foo")
        }
      }
      testJson2.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2 must beNone
      }
      testJson3.validate(testJsonReads).asEither must beLeft
    }

    "allow reading lists with single-item fallback" in {
      case class TestData(field1: String, field2: Seq[String])
      // valid
      val testJson1: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr("foo")
      )
      // valid - with single item
      val testJson2: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> "foo"
      )
      // invalid, list exists but is wrong type
      val testJson3: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr(
          Json.obj("foo" -> "bar")
        )
      )
      val testJsonReads: Reads[TestData] = (
        (__ \ "field1").read[String] and
        (__ \ "field2").readSeqOrSingle[String]
      )(TestData.apply _)

      testJson1.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2.headOption must beSome.which { value =>
          value must equalTo("foo")
        }
      }
      testJson2.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2.headOption must beSome.which { value =>
          value must equalTo("foo")
        }
      }
      testJson3.validate(testJsonReads).asEither must beLeft
    }

    "allow reading nullable lists with single-item fallback" in {
      case class TestData(field1: String, field2: Option[Seq[String]])
      // valid
      val testJson1: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr("foo")
      )
      // valid - with single item
      val testJson2: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> "foo"
      )
      // invalid, list exists but is wrong type
      val testJson3: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr(
          Json.obj("foo" -> "bar")
        )
      )
      val testJsonReads: Reads[TestData] = (
        (__ \ "field1").read[String] and
          (__ \ "field2").readSeqOrSingleNullable[String]
        )(TestData.apply _)

      testJson1.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2 must beSome.which { value =>
          value.headOption must beSome("foo")
        }
      }
      testJson2.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2 must beSome.which { value =>
          value.headOption must beSome("foo")
        }
      }
      testJson3.validate(testJsonReads).asEither must beLeft
    }

    "allow reading/writing nullable lists with single-item fallback" in {
      case class TestData(field1: Option[String], field2: Option[Seq[String]])
      // valid
      val testJson1: JsValue = Json.obj(
        "field2" -> Json.arr("foo")
      )
      // valid - with single item
      val testJson2: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> "foo"
      )
      // invalid, list exists but is wrong type
      val testJson3: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr(
          Json.obj("foo" -> "bar")
        )
      )
      val testJsonFormat: Format[TestData] = (
        (__ \ "field1").formatNullableWithDefault(Some("bar")) and
        (__ \ "field2").formatSeqOrSingleNullable[String]
      )(TestData.apply, unlift(TestData.unapply))

      testJson1.validate(testJsonFormat).asOpt must beSome.which { data =>
        data.field1 must beSome.which { value =>
          value must_== "bar"
        }
        data.field2 must beSome.which { value =>
          value.headOption must beSome("foo")
        }
      }

      testJson2.validate(testJsonFormat).asOpt must beSome.which { data =>
        data.field1 must beSome.which { value =>
          value must_== "val"
        }
        data.field2 must beSome.which { value =>
          value.headOption must beSome("foo")
        }
      }

      val obj = Json.toJson(
        TestData(field1 = None, field2 = Some(Seq("foo"))))(testJsonFormat)
      obj.validate(testJsonFormat).asOpt must beSome.which { data =>
        data.field1 must beSome.which { value =>
          value must_== "bar"
        }
        data.field2 must beSome.which { value =>
          value.headOption must beSome("foo")
        }
      }
    }
  }
}
