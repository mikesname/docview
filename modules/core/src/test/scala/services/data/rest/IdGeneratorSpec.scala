package services.data.rest

import cookies.FakeApp
import play.api.test.PlaySpecification
import services.data.IdGenerator

class IdGeneratorSpec extends PlaySpecification {

  "IdGenerator" should {
    "give a valid first ID from a list of strings non numeric strings" in new FakeApp {
      IdGenerator.nextNumericId(Seq("foo", "bar")) must equalTo(1)
    }

    "give a valid ID from a list of numeric strings" in  new FakeApp {
      IdGenerator.nextNumericId(Seq("foo3", "bar1")) must equalTo(4)
    }
  }
}
