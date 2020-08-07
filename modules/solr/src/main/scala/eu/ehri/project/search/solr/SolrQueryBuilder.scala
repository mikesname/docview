package eu.ehri.project.search.solr

import javax.inject.Inject
import models.{EntityType, UserProfile}
import play.api.i18n.Lang
import play.api.{Configuration, Logger}
import services.search.SearchConstants._
import services.search._
import utils.PageParams

import scala.util.Try


private[solr] object SolrQueryBuilder {

  private val logger = Logger(getClass)

  val sortMap: Map[SearchSort.Value, String] = Map(
    SearchSort.Id -> "isParent.desc,identifier.asc",
    SearchSort.Score -> "score.desc",
    SearchSort.Name -> "name_sort.asc",
    SearchSort.DateNewest -> "lastUpdated.desc",
    SearchSort.Country -> "countryCode.asc",
    SearchSort.Holder -> "repositoryName.asc",
    SearchSort.Location -> "geodist().asc",
    SearchSort.Detail -> "charCount.desc",
    SearchSort.ChildCount -> "childCount.desc"
  )

  def escape(s: CharSequence): String = {
    val sb: StringBuffer = new StringBuffer()
    0.until(s.length()).foreach { i =>
      val c = s.charAt(i)
      if (c == '\\' || c == '!' || c == '(' || c == ')' ||
        c == ':' || c == '^' || c == '[' || c == ']' ||
        c == '{' || c == '}' || c == '~' || c == '*' || c == '?' ||
        c == '"' || c == ' '
      ) {
        sb.append('\\')
      }
      sb.append(c);
    }
    sb.toString
  }

  /**
    * Apply filters to the request based on a set of applied facets.
    */
  def facetFilterParams(facetClasses: Seq[FacetClass[Facet]],
    appliedFacets: Seq[AppliedFacet]): Seq[(String, String)] = {
    // See the spec for this to get some insight
    // into how this mess works...

    // filter the results by applied facets
    val filters = facetClasses.flatMap { fclass =>
      appliedFacets.filter(_.name == fclass.key).map(_.values).flatMap { paramVals =>
        if (paramVals.isEmpty) None
        else {
          val query: Option[String] = fclass match {
            case fc: FieldFacetClass =>
              // Choice facets need a tag in front of the parameter so they can be
              // excluded from count-limiting filters
              // http://wiki.apache.org/solr/SimpleFacetParameters#Multi-Select_Faceting_and_LocalParams
              val filter = paramVals.map(s => "\"" + escape(s) + "\"").mkString(" ")
              Some(s"${fc.key}:($filter)")
            case fc: QueryFacetClass =>
              val activeRanges = fc.facets.filter(f => paramVals.contains(f.value))
              if (activeRanges.nonEmpty) {
                val filter = activeRanges.map(SolrFacetParser.facetValue).mkString(" ")
                Some(s"${fc.key}:($filter)")
              } else None
            case e =>
              logger.warn(s"Unknown facet class type: $e")
              None
          }
          query.map { q =>
            val tag = if (fclass.multiSelect) "{!tag=" + fclass.key + "}" else ""
            tag + q
          }
        }
      }
    }
    filters.map(f => "fq" -> f)
  }

  def entityFilterParams(entities: Seq[EntityType.Value]): Seq[(String, String)] = {
    if (entities.nonEmpty) {
      val filter = entities.map(_.toString).mkString(" OR ")
      Seq("fq" -> s"$TYPE:($filter)")
    } else Seq.empty
  }

  def bboxParams(bbox: Option[BoundingBox]): Seq[(String, String)] = {
    bbox.map { b =>
      Seq("fq" -> s"location:[${b.latMin},${b.lonMin} TO ${b.latMax},${b.lonMax}]")
    }.toSeq.flatten
  }

  def latLngParams(latlng: Option[LatLng]): Seq[(String,String)] = {
    latlng.map { ll => Seq(
      "pt" -> s"${ll.lat},${ll.lon}",
      "sfield" -> "location"
    )}.toSeq.flatten
  }

  def accessFilterParams(userOpt: Option[UserProfile]): Seq[(String, String)] = {
    // Filter docs based on access. If the user is empty, only allow
    // through those which have accessibleTo:ALLUSERS.
    // If we have a user and they're not admin, add a filter against
    // all their groups.
    if (userOpt.isEmpty) {
      Seq("fq" -> s"$ACCESSOR_FIELD:$ACCESSOR_ALL_PLACEHOLDER")
    } else if (!userOpt.exists(_.isAdmin)) {
      // Create a boolean or query starting with the ALL placeholder, which
      // includes all the groups the user belongs to, included inherited ones,
      // i.e. accessibleTo:(ALLUSERS OR mike OR admin)
      val accessors = ACCESSOR_ALL_PLACEHOLDER +: userOpt.map(
        u => (u.id +: u.allGroups.map(_.id)).distinct).getOrElse(Nil)
      Seq("fq" -> s"$ACCESSOR_FIELD:(${accessors.mkString(" ")})")
    } else Seq.empty
  }

  def facetParams(facets: Seq[FacetClass[Facet]], asJson: Boolean): Seq[(String, String)] = {
    if (asJson) {
      import play.api.libs.json.Json
      import play.api.libs.json.Json.JsValueWrapper
      val jsonFacets: Seq[(String, JsValueWrapper)] = facets
        .flatMap(SolrFacetParser.facetAsJson)
        .map(kv => kv._1 -> Json.toJsFieldJsValueWrapper(kv._2))
      Seq("json.facet" -> Json.stringify(Json.obj(jsonFacets: _*)))
    } else {
      Seq(
        "facet" -> true.toString,
        "facet.mincount" -> 1.toString
      ) ++ facets.flatMap(SolrFacetParser.facetAsParams)
    }
  }

  def extraFilterParams(filters: Seq[(String, Any)]): Seq[(String, String)] = {
    filters.map { case (key, value) =>
      val filter = value match {
        // Have to quote strings
        case _: String => key + ":\"" + value + "\""
        // not value means the key is a query!
        case () => key
        case _ => s"$key:$value"
      }
      "fq" -> filter
    }
  }

  def groupParams(lang: Option[Lang]): Seq[(String, String)] = {
    // Group results by item id (as opposed to description id). Facet counts
    // are also set to reflect grouping as opposed to the number of individual
    // items.
    Seq(
      "group" -> true.toString,
      "group.field" -> ITEM_ID,
      "group.sort" -> "query({!v=$gsf}, 0.1) desc",
      "gsf" -> s"$LANGUAGE_CODE:${lang.getOrElse(Lang.defaultLang).locale.getISO3Language}",
      "group.facet" -> true.toString,
      "group.ngroups" -> true.toString,
      "group.cache.percent" -> 0.toString,
      "group.offset" -> 0.toString,
      "group.limit" -> 1.toString,
      "group.format" -> "simple"
    )
  }

  def excludeFilterParams(ids: Seq[String]): Seq[(String, String)] = {
    if (ids.nonEmpty) {
      Seq("fq" -> s"$ITEM_ID:(${ids.map(id => s"-$id").mkString(" ")})")
    } else Seq.empty
  }

  def idFilterParams(ids: Seq[String]): Seq[(String, String)] = {
    if (ids.nonEmpty) {
      Seq("fq" -> s"$ITEM_ID:(${ids.mkString(" ")})")
    } else Seq.empty
  }

  def basicParams(queryString: String, paging: PageParams, debug: Boolean): Seq[(String, String)] = Seq(
    "q" -> queryString,
    "wt" -> "json",
    "start" -> paging.offset.toString,
    "rows" -> paging.limit.toString,
    "debugQuery" -> debug.toString,
    "defType" -> "edismax",
    "mm.autoRelax" -> true.toString
  )

  def highlightParams(hasQuery: Boolean): Seq[(String, String)] = {
    // Highlight, but only if we have a query...
    if (hasQuery) Seq(
      "hl" -> true.toString,
      "hl.fl" -> "*",
      "hl.usePhraseHighlighter" -> true.toString,
      "hl.tag.pre" -> "<em class='highlight'>",
      "hl.tag.post" -> "</em>"
    ) else Seq.empty
  }

  def fieldParams(fields: Seq[SearchField.Value], boost: Seq[(String, Option[Double])], config: Configuration): Seq[(String, String)] = {
    // Apply search to specific fields.
    val basic = if (fields.nonEmpty) {
      Seq("qf" -> fields.mkString(" "))
    } else {
      val qfFields: String = boost.map { case (key, boostOpt) =>
        boostOpt.map(b => s"$key^$b").getOrElse(key)
      }.mkString(" ")
      logger.trace(s"Query fields: $qfFields")
      Seq("qf" -> qfFields)
    }

    // Set field aliases
    val aliases = for {
      config <- config.getOptional[Configuration]("search.fieldAliases").toSeq
      alias <- config.keys.toSeq
      fieldName <- config.getOptional[String](alias).toSeq
    } yield s"f.$alias.qf" -> fieldName

    basic ++ aliases
  }

  def spellcheckParams(config: Seq[(String, Option[String])]): Seq[(String, String)] = {
    Seq("spellcheck" -> true.toString) ++
      config.collect { case (key, Some(value)) =>
        s"spellcheck.$key" -> value
      }
  }

  def sortParams(sort: Option[SearchSort.Value]): Seq[(String, String)] =
    sort.flatMap(sortMap.get)
      .map { sort => "sort" -> sort.split("""\.""").mkString(" ") }.toSeq

  def filterParams(filters: Seq[String]): Seq[(String, String)] =
    filters.filter(_.contains(":")).map(f => "fq" -> f)

  def extraSearchConfig(config: Configuration): Seq[(String, String)] = {
    for {
      cf <- config.getOptional[Configuration]("search.extra").toSeq
      key <- (cf.keys ++ cf.subKeys).toSeq
      strVal <- Try(cf.getOptional[String](key)).getOrElse(Option.empty).toSeq
    } yield key -> strVal
  }
}


/**
  * Build a Solr query as a sequence of key/value parameter pairs.
  */
case class SolrQueryBuilder @Inject()(config: Configuration) extends QueryBuilder {

  import SearchConstants._
  import SolrQueryBuilder._

  private val jsonFacets = config.getOptional[Boolean]("search.jsonFacets").getOrElse(false)
  private val enableDebug = config.getOptional[Boolean]("search.debugTiming").getOrElse(false)

  /**
    * Look up boost values from configuration for default query fields.
    */
  private lazy val queryFieldsWithBoost: Seq[(String, Option[Double])] = Seq(
    ITEM_ID, IDENTIFIER, OTHER_IDENTIFIERS, NAME_EXACT, NAME_MATCH, OTHER_NAMES, PARALLEL_NAMES, ALT_NAMES, NAME_SORT, TEXT
  ).map(f => f -> config.getOptional[Double](s"search.boost.$f"))

  private lazy val spellcheckConfig: Seq[(String, Option[String])] = Seq(
    "count", "onlyMorePopular", "extendedResults", "accuracy",
    "collate", "maxCollations", "maxCollationTries", "maxResultsForSuggest"
  ).map(f => f -> config.getOptional[String](s"search.spellcheck.$f"))


  /**
    * Run a simple filter on the name_ngram field of all entities
    * of a given type.
    */
  override def simpleFilterQuery(query: SearchQuery, alphabetical: Boolean = false): Seq[(String, String)] = {

    val localParam = if (config.getOptional[Boolean]("search.andMode").getOrElse(false))
      "{!q.op=AND} " else ""
    val queryString = localParam + query.params.query.getOrElse("*").trim

    Seq(
      basicParams(queryString, query.paging, enableDebug),
      entityFilterParams(query.params.entities),
      bboxParams(query.params.bbox),
      latLngParams(query.params.latLng),
      accessFilterParams(query.user),
      idFilterParams(query.withinIds.toSeq.flatten),
      excludeFilterParams(query.params.excludes),
      filterParams(query.params.filters),
      groupParams(query.lang),
      extraFilterParams(query.filters.toSeq),
      Seq("qf" -> s"$ITEM_ID^0.5 $OTHER_IDENTIFIERS^0.3 $NAME_MATCH^2.0 $NAME_NGRAM"),
      Seq("fl" -> s"$ID $ITEM_ID $NAME_EXACT $TYPE $HOLDER_NAME $DB_ID"),
      if (alphabetical) Seq("sort" -> s"$NAME_SORT asc") else Seq.empty,
      query.extraParams.map(kp => kp._1 -> kp._2.toString).toSeq
    ).flatten
  }

  /**
    * Build a query given a set of search parameters.
    */
  override def searchQuery(query: SearchQuery): Seq[(String, String)] = {

    val defaultQuery = query.mode match {
      case SearchMode.DefaultAll => "*"
      case _ => "PLACEHOLDER_QUERY_RETURNS_NO_RESULTS" // FIXME! This sucks
    }

    // Child count to boost results seems to have an odd affect in making the
    // query only work on the default field - disabled for now...
    val localParam = if (config.getOptional[Boolean]("search.andMode").getOrElse(false))
      "{!q.op=AND} " else ""
    val queryString = localParam + query.params.query.getOrElse(defaultQuery).trim

    Seq(
      basicParams(queryString, query.paging, enableDebug),
      groupParams(query.lang),
      fieldParams(query.params.fields, queryFieldsWithBoost, config),
      sortParams(query.params.sort),
      facetParams(query.facetClasses, jsonFacets),
      facetFilterParams(query.facetClasses, query.appliedFacets),
      filterParams(query.params.filters),
      entityFilterParams(query.params.entities),
      bboxParams(query.params.bbox),
      latLngParams(query.params.latLng),
      accessFilterParams(query.user),
      idFilterParams(query.withinIds.toSeq.flatten),
      excludeFilterParams(query.params.excludes),
      extraFilterParams(query.filters.toSeq),
      query.extraParams.map(kp => kp._1 -> kp._2.toString).toSeq,
      highlightParams(query.params.query.isDefined),
      spellcheckParams(spellcheckConfig),
      extraSearchConfig(config),
    ).flatten
  }
}
