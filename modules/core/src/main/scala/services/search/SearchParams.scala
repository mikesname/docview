package services.search

import models.EntityType
import play.api.libs.json.{Format, Json, Writes}
import play.api.mvc.QueryStringBindable
import utils.EnumUtils
import utils.binders._


case object SearchField extends Enumeration {
  type Field = Value
  val Identifier = Value("identifier")
  val Title = Value("title")
  val Creator = Value("creator")
  val Person = Value("person")
  val Place = Value("place")
  val Subject = Value("subject")
  val Address = Value("address")

  implicit val _fmt: Format[SearchField.Value] = EnumUtils.enumFormat(SearchField)
}

case object SearchSort extends Enumeration {
  type Sort = Value
  val Id = Value("id")
  val Score = Value("score")
  val Name = Value("name")
  val DateNewest = Value("updated")
  val Country = Value("country")
  val Holder = Value("holder")
  val Location = Value("location")
  val Detail = Value("detail")
  val ChildCount = Value("holdings")

  implicit val _fmt: Format[SearchSort.Value] = utils.EnumUtils.enumFormat(SearchSort)
}

case object SearchMode extends Enumeration {
  type Type = Value
  val DefaultAll = Value("all")
  val DefaultNone = Value("none")

  implicit val _fmt: Format[SearchMode.Value] = utils.EnumUtils.enumFormat(SearchMode)
}

case object FacetSort extends Enumeration {
  type FacetSort = Value
  val Name = Value("name")
  val Count = Value("count")
  val Fixed = Value("fixed")

  implicit val _fmt: Format[FacetSort.Value] = utils.EnumUtils.enumFormat(FacetSort)
}

case class BoundingBox(
  latMin: BigDecimal,
  lonMin: BigDecimal,
  latMax: BigDecimal,
  lonMax: BigDecimal
) {
  // NB: should we ensure lat/lon min is always less then max?
  // For now just check the range...
  def isValid: Boolean = latMin >= -90 && latMin <= 90 &&
                          lonMin >= -180 && lonMin <= 180 &&
                          latMax >= -90 && latMax <= 90 &&
                          lonMax >= -180 && lonMax <= 180
  override def toString: String = s"$latMin,$lonMin,$latMax,$lonMax"
}

object BoundingBox {
  def fromString(s: String): Either[String, BoundingBox] = {
    val err = s"Invalid bounding box format '$s', should be lat-min,lon-min,lat-max,lon-max"
    try {
      s.split(",").toList.map(BigDecimal.exact) match {
        case l1 :: l2 :: l3:: l4 :: Nil =>
          val box = BoundingBox(l1, l2, l3, l4)
          if (box.isValid) Right(box) else Left(err)
        case _ => Left(err)
      }
    } catch {
      case _: Throwable => Left(err)
    }
  }

  implicit val writes: Writes[BoundingBox] = Json.writes[BoundingBox]

  implicit def bboxQueryBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[BoundingBox] =
    new QueryStringBindable[BoundingBox] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, BoundingBox]] =
        params.get(key).flatMap(_.headOption).map(BoundingBox.fromString)

      override def unbind(key: String, value: BoundingBox): String = stringBinder.unbind(key, value.toString)
    }
}

case class LatLng(
  lat: BigDecimal,
  lon: BigDecimal
) {
  def isValid: Boolean = lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180

  override def toString: String = s"$lat,$lon"
}

object LatLng {
  def fromString(s: String): Either[String, LatLng] = {
    val err = s"Invalid point format '$s', should be lat,lon"
    try {
      s.split(",").toList.map(BigDecimal.exact) match {
        case l1 :: l2 :: Nil =>
          val point = LatLng(l1, l2)
          if (point.isValid) Right(point) else Left(err)
        case _ => Left(err)
      }
    } catch {
      case _: Throwable => Left(err)
    }
  }

  implicit val writes: Writes[LatLng] = Json.writes[LatLng]

  implicit def latLngQueryBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[LatLng] =
    new QueryStringBindable[LatLng] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LatLng]] =
        params.get(key).flatMap(_.headOption).map(LatLng.fromString)

      override def unbind(key: String, value: LatLng): String = stringBinder.unbind(key, value.toString)
    }
}

/**
  * Class encapsulating the parameters of a Solr search.
  */
case class SearchParams(
  query: Option[String] = None,
  sort: Option[SearchSort.Value] = None,
  entities: Seq[EntityType.Value] = Nil,
  fields: Seq[SearchField.Value] = Nil,
  facets: Seq[String] = Nil,
  excludes: Seq[String] = Nil,
  filters: Seq[String] = Nil,
  bbox: Option[BoundingBox] = None,
  latLng: Option[LatLng] = None
) {
  /**
    * Is there an active constraint on these params?
    */
  def isFiltered: Boolean = !query.forall(_.trim.isEmpty)
}

object SearchParams {
  val SORT = "sort"
  val QUERY = "q"
  val FIELD = "qf"
  val FACET = "facet"
  val ENTITY = "st"
  val EXCLUDE = "ex"
  val FILTERS = "f"
  val BBOX = "bbox"
  val LATLNG = "latlng"

  def empty: SearchParams = SearchParams()

  implicit val writes: Writes[SearchParams] = Json.writes[SearchParams]

  implicit def _queryBinder(
    implicit seqStrBinder: QueryStringBindable[Seq[String]]): QueryStringBindable[SearchParams] with NamespaceExtractor = new QueryStringBindable[SearchParams] with NamespaceExtractor {

    private implicit val sortBinder: QueryStringBindable[SearchSort.Value] = queryStringBinder(SearchSort)

    // Backwards compatibility for old Solr-based `sort parameters. We fall
    // back to these if none of the new sort values are valid.
    def bindOldSort(key: String, params: Map[String, Seq[String]]): Option[SearchSort.Value] = {
      val oldSortMap: Map[String, SearchSort.Value] = Map(
        "identifier.asc" -> SearchSort.Id,
        "isParent.desc,identifier.asc" -> SearchSort.Id,
        "score.desc" -> SearchSort.Score,
        "name_sort.asc" -> SearchSort.Name,
        "lastUpdated.desc" -> SearchSort.DateNewest,
        "countryCode.asc" -> SearchSort.Country,
        "repositoryName.asc" -> SearchSort.Holder,
        "geodist().asc" -> SearchSort.Location,
        "charCount.desc" -> SearchSort.Detail,
        "childCount.desc" -> SearchSort.ChildCount
      )
      params
        .get(key)
        .map(_.flatMap(oldSortMap.get).headOption)
        .getOrElse(Option.empty)
    }


    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SearchParams]] = {
      val namespace: String = ns(key)
      val rawParams = SearchParams(
        bindOr(namespace + QUERY, params, Option.empty[String]).filter(_.trim.nonEmpty),
        bindOr(namespace + SORT, params, bindOldSort(namespace + SORT, params)),
        bindOr(namespace + ENTITY, params, Seq.empty[EntityType.Value])(tolerantSeqBinder(queryStringBinder(EntityType))),
        bindOr(namespace + FIELD, params, Seq.empty[SearchField.Value])(tolerantSeqBinder(queryStringBinder(SearchField))),
        bindOr(namespace + FACET, params, Seq.empty[String]),
        bindOr(namespace + EXCLUDE, params, Seq.empty[String]),
        bindOr(namespace + FILTERS, params, Seq.empty[String]),
        bindOr(namespace + BBOX, params, Option.empty[BoundingBox]),
        bindOr(namespace + LATLNG, params, Option.empty[LatLng])
      )
      // NB: Sorting by location is invalid without a valid `latlng` parameter
      val checked = if (rawParams.sort.contains(SearchSort.Location) && rawParams.latLng.isEmpty)
        rawParams.copy(sort = None)
      else rawParams

      Some(Right(checked))
    }

    override def unbind(key: String, params: SearchParams): String =
      utils.http.joinQueryString(toParams(params, ns(key)).distinct)

    private def toParams(p: SearchParams, ns: String = ""): Seq[(String, String)] = {
      p.query.map(q => ns + QUERY -> q).toSeq ++
        p.sort.map(s => ns + SORT -> s.toString).toSeq ++
        p.entities.map(e => ns + ENTITY -> e.toString) ++
        p.fields.map(f => ns + FIELD -> f.toString) ++
        p.facets.map(f => ns + FACET -> f) ++
        p.excludes.map(e => ns + EXCLUDE -> e) ++
        p.filters.map(f => ns + FILTERS -> f) ++
        p.bbox.map { box => ns + BBOX -> box.toString }.toSeq ++
        p.latLng.map { latLng => ns + LATLNG -> latLng.toString }.toSeq
    }
  }
}
