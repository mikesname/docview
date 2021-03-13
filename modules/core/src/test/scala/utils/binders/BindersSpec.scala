package utils.binders

import models.EntityType

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import play.api.test.PlaySpecification
import services.data.Constants.{FROM => TFROM, _}
import services.search.{BoundingBox, LatLng, SearchParams, SearchSort}
import services.search.SearchParams.{BBOX, ENTITY, LATLNG, QUERY, SORT}
import utils.{PageParams, RangeParams, SystemEventParams}

class BindersSpec extends PlaySpecification {
  "Binder" should {
    val binder = utils.binders.dateTimeQueryBinder

    "allow binding from partial dates in queries" in {
      binder.bind("date", Map("date" -> Seq("1949-10"))) must_== Some(Right(LocalDateTime.parse("1949-10-01T00:00")))
      binder.bind("date", Map("date" -> Seq("1949-10-01"))) must_== Some(Right(LocalDateTime.parse("1949-10-01T00:00")))
      binder.bind("date", Map("date" -> Seq("1949-10-01T10:10:10"))) must_== Some(Right(LocalDateTime.parse("1949-10-01T10:10:10")))
    }

    "give an error with invalid dates" in {
      binder.bind("date", Map("date" -> Seq("foo"))) must beSome.which { d: Either[String, LocalDateTime] =>
        d must_== Left("Invalid date format: foo")
      }
    }
  }

  "PageParams" should {
    "bind correctly with tolerant defaults" in {
      PageParams._queryBinder.bind("params", Map()) must_== Some(Right(PageParams.empty))
      PageParams._queryBinder
        .bind("params", Map(PAGE_PARAM -> Seq("-1"))) must_== Some(Right(PageParams.empty))
      PageParams._queryBinder
        .bind("params_a", Map("a" + PAGE_PARAM -> Seq("10"))) must_== Some(Right(PageParams(10)))
      PageParams._queryBinder
        .bind("params", Map(PAGE_PARAM -> Seq("foo"), LIMIT_PARAM -> Seq("10"))) must_== Some(Right(PageParams(limit = 10)))
    }
  }

  "RangeParams" should {
    "bind correctly with tolerant defaults" in {
      RangeParams._queryBinder.bind("range", Map()) must_== Some(Right(RangeParams.empty))
      RangeParams._queryBinder
        .bind("range", Map(OFFSET_PARAM -> Seq("-1"))) must_== Some(Right(RangeParams.empty))
      RangeParams._queryBinder
        .bind("range_a", Map("a" + OFFSET_PARAM -> Seq("10"))) must_== Some(Right(RangeParams(10)))
      RangeParams._queryBinder
        .bind("range", Map(OFFSET_PARAM -> Seq("foo"), LIMIT_PARAM -> Seq("10"))) must_== Some(Right(RangeParams(limit = 10)))
    }
  }

  "SystemEventParams" should {
    "bind correctly with tolerant defaults" in {
      SystemEventParams._queryBinder.bind("params", Map()) must_== Some(Right(SystemEventParams.empty))
      SystemEventParams._queryBinder
        .bind("params", Map(TFROM -> Seq("2017-01-01"))) must_== Some(Right(SystemEventParams(from = Some(
        LocalDate.parse("2017-01-01").atStartOfDay()))))
      SystemEventParams._queryBinder
        .bind("params", Map(TFROM -> Seq("2017-01"))) must_== Some(Right(SystemEventParams(from = Some(
        LocalDate.parse("2017-01-01").atStartOfDay()))))
      SystemEventParams._queryBinder
        .bind("params_a", Map("a" + TFROM -> Seq("2017-01-01"))) must_== Some(Right(SystemEventParams(from = Some(
        LocalDate.parse("2017-01-01", DateTimeFormatter.ISO_DATE).atStartOfDay()))))
      SystemEventParams._queryBinder
        .bind("params", Map(TFROM -> Seq("bad-date"))) must_== Some(Right(SystemEventParams.empty))
      SystemEventParams._queryBinder
        .bind("params", Map(ITEM_TYPE -> Seq("bad-item", EntityType.DocumentaryUnit.toString))) must_== Some(
        Right(SystemEventParams(itemTypes = Seq(EntityType.DocumentaryUnit))))
    }
  }

  "SearchParams" should {
    "bind correctly with tolerant defaults" in {
      SearchParams._queryBinder.bind("params", Map()) must_== Some(Right(SearchParams.empty))
      SearchParams._queryBinder
        .bind("params", Map(QUERY -> Seq("foo"))) must_== Some(Right(SearchParams(query = Some("foo"))))
      SearchParams._queryBinder
        .bind("params", Map(QUERY -> Seq(""))) must_== Some(Right(SearchParams(query = None)))
      SearchParams._queryBinder
        .bind("params_a", Map("a" + QUERY -> Seq("foo"))) must_== Some(Right(SearchParams(query = Some("foo"))))
      // one invalid entity name
      SearchParams._queryBinder
        .bind("params", Map(ENTITY -> Seq("bad"))) must_== Some(Right(SearchParams.empty))
      // one invalid entity name
      SearchParams._queryBinder
        .bind("params", Map(ENTITY -> Seq(EntityType.DocumentaryUnit.toString, "bad"))) must_== Some(
        Right(SearchParams(entities = Seq(EntityType.DocumentaryUnit))))
      // valid bounding box
      SearchParams._queryBinder
        .bind("params", Map(BBOX -> Seq("-1,-1,2,2"))) must_== Some(
        Right(SearchParams(bbox = Some(BoundingBox(-1,-1,2,2)))))
      // out-of-range bounding box
      SearchParams._queryBinder
        .bind("params", Map(BBOX -> Seq("-91,-181,0,0"))) must_== Some(Right(SearchParams.empty))
      // valid point
      SearchParams._queryBinder
        .bind("params", Map(LATLNG -> Seq("50,0"))) must_== Some(
        Right(SearchParams(latLng = Some(LatLng(50,0)))))
      // invalid point
      SearchParams._queryBinder
        .bind("params", Map(LATLNG -> Seq("-91,0"))) must_== Some(Right(SearchParams.empty))
      // ensure sort by location will only bind with a valid point
      SearchParams._queryBinder
        .bind("params", Map(SORT -> Seq("location"))) must_== Some(Right(SearchParams.empty))
      SearchParams._queryBinder
        .bind("params", Map(SORT -> Seq("location"), LATLNG -> Seq("50,0"))) must_== Some(
        Right(SearchParams(sort = Some(SearchSort.Location), latLng = Some(LatLng(50,0)))))
    }
  }
}
