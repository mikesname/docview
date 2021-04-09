package services.data

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import config.serviceBaseUrl
import play.api.Logger
import play.api.http._
import play.api.libs.json._
import play.api.libs.ws._
import services._
import utils.{Page, RangePage, RangeParams}

import java.net.{ConnectException, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}


trait RestService {

  implicit def config: play.api.Configuration
  def ws: WSClient

  import HttpVerbs._
  import play.api.libs.ws.{EmptyBody, WSBody, WSResponse}

  private def logger: Logger = Logger(this.getClass)

  /**
    * Wrapper for WS.
    */
  private[data] case class BackendRequest(
    url: String,
    headers: Seq[(String, String)] = Seq.empty,
    queryString: Seq[(String, String)] = Seq.empty,
    method: String = GET,
    body: WSBody = EmptyBody,
    timeout: Option[Duration] = None
  )(implicit apiUser: ApiUser) {

    lazy val credentials: Option[(String, String)] = for {
      username <- config.getOptional[String]("services.ehridata.username")
      password <- config.getOptional[String]("services.ehridata.password")
    } yield (username, password)

    private def fullUrl: String =
      if (queryString.nonEmpty) s"$url?${utils.http.joinQueryString(queryString)}" else url

    private def holderWithAuth: WSRequest = {
      val holder = ws.url(url)
        .addQueryStringParameters(queryString: _*)
        .addHttpHeaders(headers: _*)
        .withBody(body)
      val hc = credentials.fold(holder) { case (un, pw) =>
        holder.withAuth(un, pw, WSAuthScheme.BASIC)
      }
      timeout.fold(hc)(t => hc.withRequestTimeout(t))
    }

    private def runWs(implicit ec: ExecutionContext): Future[WSResponse] = {
      logger.debug(s"WS: $apiUser $method $fullUrl")
      holderWithAuth
        .execute(method)
        .map(r => checkError(r, Some(fullUrl)))
        .recover {
          case e: ConnectException => throw ServiceOffline(fullUrl, e)
        }
    }

    def stream()(implicit ec: ExecutionContext): Future[WSResponse] = {
      logger.debug(s"WS (stream): $apiUser $method $fullUrl")
      holderWithAuth.stream().recover {
        case e: ConnectException => throw ServiceOffline(fullUrl, e)
      }
    }

    def get()(implicit ec: ExecutionContext): Future[WSResponse] = copy(method = GET).execute()

    def post()(implicit ec: ExecutionContext): Future[WSResponse] = withMethod(POST).execute()

    def post[T](body: T)(implicit wrt: BodyWritable[T], ct: ContentTypeOf[T], ec: ExecutionContext): Future[WSResponse] =
      withMethod(POST).withBody(body).execute()

    def put[T](body: T)(implicit wrt: BodyWritable[T], ct: ContentTypeOf[T], ec: ExecutionContext): Future[WSResponse] =
      withMethod(PUT).withBody(body).execute()

    def delete()(implicit ec: ExecutionContext): Future[WSResponse] = withMethod(DELETE).execute()

    def head()(implicit ec: ExecutionContext): Future[WSResponse] = holderWithAuth.execute().recover {
      case e: ConnectException => throw ServiceOffline(fullUrl, e)
    }

    /**
      * Sets the body for this request. Copy and paste from WSRequest :(
      */
    def withBody[T](body: T)(implicit wrt: BodyWritable[T], ct: ContentTypeOf[T]): BackendRequest = {
      val wsBody: WSBody = wrt.transform(body)
      if (headers.toMap.contains(HeaderNames.CONTENT_TYPE)) {
        withBody(wsBody)
      } else {
        ct.mimeType.fold(withBody(wsBody)) { contentType =>
          withBody(wsBody).withHeaders(HeaderNames.CONTENT_TYPE -> contentType)
        }
      }
    }

    def withBody(body: WSBody): BackendRequest = copy(body = body)

    def withHeaders(hrds: (String, String)*): BackendRequest =
      copy(headers = headers ++ hrds)

    def withQueryString(parameters: (String, String)*): BackendRequest =
      copy(queryString = queryString ++ parameters)

    def withMethod(method: String): BackendRequest = copy(method = method)

    def withTimeout(duration: Duration): BackendRequest = copy(timeout = Some(duration))

    def execute()(implicit ec: ExecutionContext): Future[WSResponse] = runWs
  }

  import Constants._
  import play.api.http.Status._

  /**
    * Header to add for log messages.
    */
  protected def msgHeader(msg: Option[String]): Seq[(String, String)] =
    msg
      .map(m => Seq(LOG_MESSAGE_HEADER_NAME -> URLEncoder.encode(m, StandardCharsets.UTF_8.name)))
      .getOrElse(Seq.empty)

  /**
    * Standard headers we sent to every Neo4j/EHRI Server request.
    */
  protected val headers = Map(
    HeaderNames.ACCEPT_CHARSET -> StandardCharsets.UTF_8.name
  )

  /**
    * Transform a query string map into a sequence of tuples.
    */
  protected def unpack(m: Map[String, Seq[String]]): Seq[(String, String)] = m.toSeq.flatMap {
    case (k, vals) => vals.map(v => k -> v)
  }

  /**
    * Headers to add to outgoing request...
    *
    * @return
    */
  private[data] def authHeaders(implicit apiUser: ApiUser): Map[String, String] = apiUser match {
    case AuthenticatedUser(id) => headers + (AUTH_HEADER_NAME -> id)
    case AnonymousUser => headers
  }

  /**
    * Create a web request with correct auth parameters for the REST API.
    */
  private lazy val includeProps = config
    .getOptional[Seq[String]]("ehri.services.includedProperties")
    .getOrElse(Seq.empty)


  protected def userCall(url: String, params: Seq[(String, String)] = Seq.empty)(implicit apiUser: ApiUser): BackendRequest = {
    BackendRequest(url)
      .withHeaders(authHeaders.toSeq: _*)
      .withQueryString(params: _*)
      .withQueryString(includeProps.map(p => Constants.INCLUDE_PROPERTIES_PARAM -> p): _*)
  }

  /**
    * Fetch a range, working out if there are more items by going one-beyond-the-end.
    */
  protected def fetchRange[T](req: BackendRequest, params: RangeParams, context: Option[String])(
    implicit reader: Reads[T], ec: ExecutionContext): Future[RangePage[T]] = {
    val incParams = if (params.hasLimit) params.copy(limit = params.limit + 1) else params
    req.withHeaders(STREAM_HEADER_NAME -> true.toString)
      .withQueryString(incParams.queryParams: _*)
      .get().map { r =>
      val page = parsePage(r, context)(reader)
      val more = if (params.hasLimit) page.size > params.limit else false
      val pageItems = if (params.hasLimit) page.items.take(params.limit) else page.items
      RangePage(params.offset, params.limit, more = more, items = pageItems)
    }
  }

  /**
    * Encode a bunch of URL parts.
    */
  protected def enc(base: String, s: Any*): String = {
    // FIXME: Clean up/revise/destroy/test this mess of a function
    def clean(segment: Any): String =
      segment.toString.replace("?", "%3F").replace("#", "%23")
    import java.net.URI
    val url = new java.net.URL((base +: s.map(clean)).mkString("/"))
    val uri: URI = new URI(url.getProtocol, url.getUserInfo,
      url.getHost, url.getPort, url.getPath, url.getQuery, url.getRef)
    uri.toString
  }

  protected def baseUrl: String = serviceBaseUrl("ehridata", config)

  protected def typeBaseUrl: String = enc(baseUrl, "classes")

  protected def checkError(response: WSResponse, context: Option[String] = None): WSResponse = {
    logger.trace(s"Response body ! : ${response.body}")
    response.status match {
      case OK | CREATED | NO_CONTENT => response
      case FORBIDDEN => response.json.validate[PermissionDenied].fold(
        _ => throw PermissionDenied(),
        perm => {
          logger.error(s"Permission denied error! : ${response.json}")
          throw perm
        }
      )
      case BAD_REQUEST | CONFLICT => try {
        response.json.validate[ValidationError]
          .orElse(response.json.validate[InputDataError])
          .orElse(response.json.validate[HierarchyError])
          .fold(
            e => {
              // Temporary approach to handling random Deserialization errors.
              // In practice this should happen
              if ((response.json \ "error").asOpt[String].contains("DeserializationError")) {
                logger.error(s"Derialization error! : ${response.json}")
                throw DeserializationError()
              } else {
                logger.error("Bad request: " + response.body)
                throw sys.error(s"Unexpected BAD REQUEST: $e \n${response.body}")
              }
            },
            ve => {
              logger.warn(s"Validation or Data error! : ${response.json}")
              throw ve
            }
        )
      } catch {
        case _: JsonParseException =>
          throw JsonError(response.body)
      }
      case NOT_FOUND => try {
        response.json.validate[ItemNotFound].fold(
          _ => throw new ItemNotFound(),
          err => throw err
        )
      } catch {
        case e@(_: JsonParseException | _: JsonMappingException) =>
          sys.error(s"Backend 404 at $context: ${e.getMessage}: '${response.body}")
      }
      case _ =>
        val err = s"Unexpected response at ${context.getOrElse("(?)")}: ${response.status}: '${response.body}'"
        logger.error(err)
        sys.error(err)
    }
  }

  private[data] def checkErrorAndParse[T](response: WSResponse)(implicit reader: Reads[T]): T =
    jsonReadToRestError(checkError(response).json, reader, context = None)

  private[data] def checkErrorAndParse[T](response: WSResponse, context: Option[String])(implicit reader: Reads[T]): T =
    jsonReadToRestError(checkError(response).json, reader, context)

  private[data] def parsePage[T](response: WSResponse)(implicit rd: Reads[T]): Page[T] =
    parsePage(response, None)(rd)

  /**
    * List header parser
    */
  private[data] val PaginationExtractor =
    """offset=(-?\d+); limit=(-?\d+); total=(-?\d+)""".r

  private[data] def parsePagination(response: WSResponse, context: Option[String]): Option[(Int, Int, Int)] = {
    val pagination = response.header(HeaderNames.CONTENT_RANGE).getOrElse("")
    PaginationExtractor.findFirstIn(pagination) match {
      case Some(PaginationExtractor(offset, limit, total)) => Some((offset.toInt, limit.toInt, total.toInt))
      case _ => None
    }
  }

  private[data] def parsePage[T](response: WSResponse, context: Option[String])(implicit rd: Reads[T]): Page[T] = {
    checkError(response).json.validate(Reads.seq(rd)).fold(
      invalid => throw BadJson(
        invalid,
        url = context,
        data = Some(Json.prettyPrint(response.json))
      ),
      items => parsePagination(response, context) match {
        case Some((offset, limit, total)) => Page(
          items = items,
          offset = offset,
          limit = limit,
          total = total
        )
        case _ => Page(items = items, total = -1)
      }
    )
  }

  private[data] def jsonReadToRestError[T](json: JsValue, reader: Reads[T], context: Option[String] = None): T = {
    json.validate(reader).fold(
      invalid => throw BadJson(
        invalid,
        url = context,
        data = Some(Json.prettyPrint(json))
      ),
      valid => valid
    )
  }
}
