package helpers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKitBase}
import akka.util.Timeout
import auth.handler.cookie.CookieIdContainer
import auth.handler.{AuthHandler, AuthIdContainer}
import auth.oauth2.MockOAuth2Service
import config.serviceBaseUrl
import cookies.SessionPreferences
import lifecycle.{ItemLifecycle, NoopItemLifecycle}
import models.{Account, CypherQuery, Feedback}
import org.jsoup.Jsoup
import org.specs2.execute.{AsResult, Result}
import play.api.db.Database
import play.api.http.{Status, Writeable}
import play.api.i18n.{Lang, MessagesApi}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}
import play.api.libs.json.{Json, Writes}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.libs.ws.WSClient
import play.api.mvc.request.{Cell, RequestAttrKey}
import play.api.mvc.{AnyContentAsFormUrlEncoded, Request, Session}
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, Configuration}
import services.accounts.{AccountManager, MockAccountManager}
import services.cypher.{CypherQueryService, MockCypherQueryService}
import services.data._
import services.feedback.{FeedbackService, MockFeedbackService}
import services.harvesting.{MockResourceSyncClient, ResourceSyncClient}
import services.htmlpages.{HtmlPages, MockHtmlPages}
import services.ingest.{EadValidator, MockEadValidatorService}
import services.oauth2.OAuth2Service
import services.redirects.{MockMovedPageLookup, MovedPageLookup}
import services.search.{MockSearchIndexMediator, _}
import utils.MockBufferedMailer

import java.nio.file.Paths
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random


/**
 * Mixin trait that provides some handy methods to test actions that
 * have authorisation, such as fakeApplication and fakeLoggedInHtmlRequest.
 */
trait TestConfiguration {

  this: PlayRunners with RouteInvokers with DefaultAwaitTimeout =>

  // Wait for up to 60 seconds for stuff to happen... this is excessive except
  // in some very slow CI environments
  override implicit def defaultAwaitTimeout: Timeout = 60.seconds


  // Stateful buffers for capturing stuff like feedback, search
  // parameters, and reset tokens. These persist across tests in
  // a very unclean way but are useful for determining the last-used
  // whatsit etc...
  protected val feedbackBuffer: mutable.HashMap[Int, Feedback] = collection.mutable.HashMap.empty[Int,Feedback]
  protected val cypherQueryBuffer: mutable.HashMap[Int, CypherQuery] = collection.mutable.HashMap.empty[Int,CypherQuery]
  protected val mailBuffer: ListBuffer[Email] = collection.mutable.ListBuffer.empty[Email]
  protected val searchParamBuffer: ListBuffer[ParamLog] = collection.mutable.ListBuffer.empty[ParamLog]
  protected val indexEventBuffer: ListBuffer[String] = collection.mutable.ListBuffer.empty[String]
  protected val movedPages: collection.mutable.ListBuffer[(String, String)] = collection.mutable.ListBuffer.empty[(String, String)]

  private def mockMailer: MailerClient = MockBufferedMailer(mailBuffer)
  private def mockIndexer: SearchIndexMediator = MockSearchIndexMediator(indexEventBuffer)
  private def mockFeedback: FeedbackService = MockFeedbackService(feedbackBuffer)
  private def mockCypherQueries: CypherQueryService = MockCypherQueryService(cypherQueryBuffer)

  // NB: The mutable state for the user DAO is still stored globally
  // in the mocks package.
  protected def mockAccounts: AccountManager = MockAccountManager(ExecutionContext.Implicits.global)
  private def mockOAuth2Flow: OAuth2Service = MockOAuth2Service()
  private def mockRelocator: MovedPageLookup = MockMovedPageLookup(movedPages)
  private def mockHtmlPages: HtmlPages = MockHtmlPages()

  // More or less the same as run config but synchronous (so
  // we can validate the actions)
  // Note: this is defined as an implicit object here so it
  // can be used by the DAO classes directly.
  protected val testEventHandler = new EventHandler {
    def handleCreate(ids: String*): Unit = mockIndexer.handle.indexIds(ids: _*)
    def handleUpdate(ids: String*): Unit = mockIndexer.handle.indexIds(ids: _*)
    def handleDelete(ids: String*): Unit = mockIndexer.handle.clearIds(ids: _*)
  }

  protected val searchLogger = new SearchLogger {
    override def log(params: ParamLog): Unit = searchParamBuffer += params
  }

  import play.api.inject.bind

  protected val appBuilder: GuiceApplicationBuilder = new play.api.inject.guice.GuiceApplicationBuilder()
    .overrides(
      // since we run some concurrent requests as the same user its
      // important not to use the CacheIdContainer, since each new
      // login evicts the last.
      bind[AuthIdContainer].to(classOf[CookieIdContainer]),
      bind[MailerClient].toInstance(mockMailer),
      bind[OAuth2Service].toInstance(mockOAuth2Flow),
      bind[MovedPageLookup].toInstance(mockRelocator),
      bind[AccountManager].toInstance(mockAccounts),
      bind[SearchEngine].to[MockSearchEngine],
      bind[FeedbackService].toInstance(mockFeedback),
      bind[CypherQueryService].toInstance(mockCypherQueries),
      bind[EventHandler].toInstance(testEventHandler),
      bind[DataApi].to[DataApiService],
      bind[SearchIndexMediator].toInstance(mockIndexer),
      bind[HtmlPages].toInstance(mockHtmlPages),
      bind[ItemLifecycle].to[NoopItemLifecycle],
      bind[EadValidator].to[MockEadValidatorService],
      bind[ResourceSyncClient].to[MockResourceSyncClient],
      // NB: Graph IDs are not stable during testing due to
      // DB churn, so using the String ID resolver rather than
      // the more efficient GID one used in production
      bind[SearchItemResolver].to[IdSearchResolver],
    ).bindings(
      bind[SearchLogger].toInstance(searchLogger)
    )

  // Might want to mock the dataApi at at some point!
  protected def dataApi(implicit app: play.api.Application, apiUser: ApiUser, executionContext: ExecutionContext): DataApiHandle =
    app.injector.instanceOf[DataApi].withContext(apiUser)(executionContext)

  protected val AUTH_TEST_HEADER_NAME = "PLAY2_AUTH_TEST_TOKEN"
  protected val CSRF_TOKEN_NAME = "csrfToken"
  protected val fakeCsrfString = "fake-csrf-token"
  protected val testPassword = "testpass"
  protected val hostInstance = "test-" + Random.alphanumeric.take(10).mkString

  /**
   * Override this value for configuration common to
   * an entire class of specs.
   */
  protected def getConfig = Map[String,Any]("storage.instance" -> hostInstance)

  /**
    * Access an i18n message with default lang.
    */
  protected def message(key: String, args: Any*)(implicit messagesApi: MessagesApi, lang: Lang = Lang.defaultLang): String =
    messagesApi(key, args: _*)(lang)

  private def loadFixtures(f: () => Result)(implicit app: Application, ex: ExecutionContext): Future[Result] = {
    val config = app.injector.instanceOf[Configuration]

    val fixtures = Paths.get(this.getClass.getClassLoader.getResource("testdata.yaml").toURI).toFile
    val ws = app.injector.instanceOf[WSClient]
    val url = s"${serviceBaseUrl("ehridata", config)}/tools/__INITIALISE"
    import org.specs2.execute.{Error, Failure}
    // Integration tests assume a server running locally. We then use the
    // initialise endpoint to clean it before each individual test.
    ws.url(url).post(fixtures).map(_.status).map {
        case Status.NO_CONTENT => try f() catch { case e: Throwable => Error(e) }
        case s => Failure(s"Unable to initialise test DB, got a status of: $s")
      } recover {
        case e => Failure(s"Unable to initialise test DB, got exception: ${e.getMessage}")
      }

  }

  /**
   * Test running Fake Application. We have general all-test configuration,
   * handled in `config`, and per-test configuration (`specificConfig`) that
   * will be merged.
   * @param specificConfig A map of config values for this test
   */
  protected abstract class ITestApp(val specificConfig: Map[String,Any] = Map.empty) extends WithApplicationLoader(
    new GuiceApplicationLoader(appBuilder.configure(getConfig ++ specificConfig))) with Injecting {
    lazy implicit val system: ActorSystem = app.actorSystem
    implicit def implicitMaterializer: Materializer = inject[Materializer]
    implicit def implicitExecContext: ExecutionContext = inject[ExecutionContext]
    implicit def messagesApi: MessagesApi = inject[MessagesApi]

    override def around[T: AsResult](t: => T): Result = {

      await(loadFixtures(() => {
        // NB: this line is needed because since Play 2.8.2 the router is lazily
        // injected in such a way that they reverse routes can be resolved before
        // prefixes are added: https://github.com/playframework/playframework/issues/10311
        // Hopefully this bug will one day be fixed...
        inject[play.api.routing.Router]

        super.around(t)
      }))(defaultAwaitTimeout)
    }
  }

  /**
    * Same as ITestApp but with Akka TestKit() functionality.
    *
    * @param specificConfig A map of config values for this test
    */
  protected abstract class ITestAppWithAkka(specificConfig: Map[String,Any] = Map.empty)
    extends ITestApp(specificConfig) with TestKitBase with ImplicitSender

  /**
    * Same as ITestApp but running a server.
    * @param app the app to use
    * @param port the server port
    */
  protected abstract class ITestServer(app: Application = GuiceApplicationBuilder().configure(getConfig).build(),
    port: Int = Helpers.testServerPort) extends WithServer(app, port) with Injecting {

    implicit lazy val system: ActorSystem = inject[ActorSystem]
    implicit def implicitExecContext: ExecutionContext = inject[ExecutionContext]

    override def around[T: AsResult](t: => T): Result = await(loadFixtures(() => {
      // NB: this line is needed because since Play 2.8.2 the router is lazily
      // injected in such a way that they reverse routes can be resolved before
      // prefixes are added: https://github.com/playframework/playframework/issues/10311
      // Hopefully this bug will one day be fixed...
      inject[play.api.routing.Router]

      super.around(t)
    }))
  }

  /**
   * Test running Fake Application. We have general all-test configuration,
   * handled in `config`, and per-test configuration (`specificConfig`) that
   * will be merged.
   * @param specificConfig A map of config values for this test
   */
  protected abstract class DBTestApp(resource: String = "", specificConfig: Map[String,Any] = Map.empty)
      extends ITestApp(specificConfig) {
    lazy val db = app.injector.instanceOf[Database]
    override def around[T: AsResult](t: => T): Result =
      super.around(withDatabaseFixture(db, resource)(implicit db => AsResult.effectively(t)))
  }

  /**
   * Run a spec after loading the given resource name as SQL fixtures.
   */
  protected abstract class WithSqlFile(val resource: String)(implicit appLoader: play.api.ApplicationLoader)
    extends WithApplicationLoader(appLoader) {
    override def around[T: AsResult](t: => T): Result =
      running(app)(withDatabaseFixture(resource)(implicit db => AsResult.effectively(t)))
  }

  protected def formData(html: String): Map[String, Seq[String]] = {
    import scala.jdk.CollectionConverters._
    val doc = Jsoup.parse(html)
    val inputData = doc.select("input,textarea").asScala
        .foldLeft(Map.empty[String,Seq[String]]) { case (acc, elem) =>
      val name = elem.attr("name")
      val data = elem.`val`()
      acc.updated(name, acc.getOrElse(name, Seq.empty[String]) :+ data)
    }
    doc.select("select").asScala.foldLeft(inputData) { case (acc, elem) =>
      val name = elem.attr("name")
      val data = elem.select("option[selected]").asScala.map(_.attr("value"))
      acc.updated(name, acc.getOrElse(name, Seq.empty[String]) ++ data)
    }
  }

  protected def formData(r: Future[play.api.mvc.Result]): Map[String, Seq[String]] =
    formData(contentAsString(r))

  /**
    * Generate a test authentication token for the given user ID.
    */
  protected def testAuthToken(id: String)(implicit app: Application): String = {
    import scala.concurrent.duration._
    val handler: AuthHandler = app.injector.instanceOf[AuthHandler]
    Await.result(handler.newSession(id), 10.seconds)
  }

  /**
   * Convenience extensions for the FakeRequest object.
   */
  protected implicit class RequestExtensions[A](fr: Request[A]) {

    def withLoggedIn(implicit app: Application): String => Request[A] = { id =>
      fr.withHeaders(fr.headers.add(AUTH_TEST_HEADER_NAME -> testAuthToken(id)))
    }

    /**
     * Set the request to be authenticated for the given user.
     */
    def withUser(user: Account)(implicit app: play.api.Application): Request[A] = {
      fr.withLoggedIn(app)(user.id)
    }

    def withSession(s: (String, String)*): Request[A] = {
      val newSession = Session(fr.session.data ++ s)
      fr.withAttrs(fr.attrs.updated(RequestAttrKey.Session, Cell(newSession)))
    }

    def withFormUrlEncodedBody(data: (String, String)*): Request[AnyContentAsFormUrlEncoded] = {
      fr.withBody(body = AnyContentAsFormUrlEncoded(play.utils.OrderPreserving.groupBy(data.toSeq)(_._1)))
    }

    /**
     * Add a dummy CSRF to the fake request.
     */
    def withCsrf: Request[A] = CSRFTokenHelper.addCSRFToken(fr)

    /**
     * Add a serialized preferences object to the fake request's session.
     */
    def withPreferences[T: Writes](p: T): Request[A] =
      fr.withSession(SessionPreferences.DEFAULT_STORE_KEY -> Json.stringify(Json.toJson(p)(implicitly[Writes[T]])))

    /**
     * Set the accepting header to the given mime-types.
     */
    def accepting(m: String*): Request[A] = m.foldLeft(fr) { (c, m) =>
      c.withHeaders(fr.headers.add(ACCEPT -> m))
    }

    /**
     * Call the request.
     */
    def call()(implicit app: play.api.Application, w: Writeable[A]): Future[play.api.mvc.Result] =
      route(app, fr).getOrElse(sys.error(s"Unexpected null route for ${fr.uri} (${fr.method})"))

    /**
     * Call the request with the given body.
     */
    def callWith[T](body: T)(implicit app: play.api.Application, w: Writeable[T]): Future[play.api.mvc.Result] =
      route(app, fr, body).getOrElse(sys.error(s"Unexpected null route for ${fr.uri} (${fr.method})"))
  }
}
