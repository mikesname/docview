package models

import play.api.test.PlaySpecification

class DatePeriodSpec extends PlaySpecification {
  "DatePeriod validation" should {
    "accept date, month, and year formats" in {
      DatePeriod.dateValidator("1944-01-01") must beTrue
      DatePeriod.dateValidator("1944-01") must beTrue
      DatePeriod.dateValidator("1944") must beTrue

      DatePeriod.dateValidator("1944-13") must beFalse
      DatePeriod.dateValidator("1944-") must beFalse
      DatePeriod.dateValidator("1944-01-32") must beFalse
      DatePeriod.dateValidator("test") must beFalse
    }
  }

  "DatePeriod" should {
    "correctly render year periods" in {
      val dp = DatePeriodF(startDate = Some("1944-01"))
      dp.years must_== "1944"
      dp.copy(endDate = Some("1945")).years must_== "1944-1945"
    }

    "render correctly" in {
      DatePeriodF(startDate = Some("1940-09"), endDate = Some("1945-05")).toString must_== "Sep 1940 - May 1945"
      DatePeriodF(startDate = Some("1940-09")).toString must_== "Sep 1940"
      DatePeriodF(startDate = Some("1940"), endDate = Some("1945-05")).toString must_== "1940 - May 1945"
      DatePeriodF(startDate = Some("1940-09-20"), endDate = Some("1945-05")).toString must_== "20 Sep 1940 - May 1945"
      DatePeriodF(startDate = Some("1940"), endDate = Some("1945")).toString must_== "1940 - 1945"
    }
  }
}
