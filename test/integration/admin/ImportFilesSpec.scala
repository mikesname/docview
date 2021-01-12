package integration.admin

import akka.util.ByteString
import controllers.institutions.FileToUpload
import defines.FileStage
import helpers._
import models._
import org.apache.commons.codec.digest.DigestUtils
import play.api.http.{ContentTypes, HeaderNames, MimeTypes, Writeable}
import play.api.libs.json.Json
import play.api.mvc.{Headers, Result}
import play.api.test.FakeRequest
import services.storage.{FileList, FileMeta}

import scala.concurrent.Future


class ImportFilesSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.privilegedUser

  private val repoDataRoutes = controllers.institutions.routes.ImportFiles

  // Mock user who belongs to admin
  val userProfile = UserProfile(
    data = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name = "test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name = "Administrators")))
  )

  private val testPayload = ByteString(
    """<ead>
      |  <eadheader>
      |    <eadid>test-id</eadid>
      |  </eadheader>
      |</ead>
      |""".stripMargin)
  private val repoId = "r1"
  private val datasetId = "default"
  private val stage = FileStage.Input
  private val testFileName = "test.xml"

  private def testFilePath(implicit app: play.api.Application): String = {
    val bucket = app.configuration.get[String]("storage.dam.classifier")
    val testPrefix = s"https://$bucket.mystorage.com/localhost/ingest-data/$repoId/$datasetId/$stage/"
    testPrefix + testFileName
  }


  private implicit val writeBytes: Writeable[ByteString] = new Writeable[ByteString](s => s, Some(ContentTypes.XML))

  private def putFile()(implicit app: play.api.Application): Future[Result] = {
    FakeRequest(repoDataRoutes.uploadStream(repoId, datasetId, stage, testFileName))
      .withHeaders(Headers(
        HeaderNames.CONTENT_TYPE -> ContentTypes.XML,
        HeaderNames.HOST -> "localhost"
      ))
      .withUser(privilegedUser)
      .callWith(testPayload)
  }

  "Repository Data API" should {

    "provide PUT urls" in new ITestApp {
      val r = FakeRequest(repoDataRoutes.uploadHandle(repoId, datasetId, stage)).withUser(privilegedUser).callWith(
        Json.toJson(FileToUpload(
          name = testFileName,
          `type` = ContentTypes.XML,
          size = testPayload.size
        )))

      contentAsJson(r) must_== Json.parse("{\"presignedUrl\":\"" + testFilePath + "\"}")
    }

    "upload via server" in new ITestApp {
      val r = putFile()
      contentAsJson(r) must_== Json.parse("{\"url\":\"" + testFilePath + "\"}")
    }

    "fetch data" in new ITestApp {
      await(putFile())
      val r = FakeRequest(repoDataRoutes.download(repoId, datasetId, stage, testFileName))
        .withUser(privilegedUser)
        .call()

      contentAsBytes(r) must_== testPayload
      contentType(r) must beSome(MimeTypes.XML)
    }

    "list files" in new ITestApp {
      await(putFile())
      val r = FakeRequest(repoDataRoutes.listFiles(repoId, datasetId, stage))
        .withUser(privilegedUser)
        .call()

      contentType(r) must beSome(MimeTypes.JSON)
      val list = contentAsJson(r).as[FileList]
      list.files.map(_.key) must contain(testFileName)
    }

    "get file info" in new ITestApp {
      await(putFile())
      val r = FakeRequest(repoDataRoutes.info(repoId, datasetId, stage, testFileName))
        .withUser(privilegedUser)
        .call()

      contentType(r) must beSome(MimeTypes.JSON)
      val versions = (contentAsJson(r) \ "versions").as[Seq[FileMeta]]
      versions.map(_.key) must contain(testFileName)
      versions.map(_.versionId) must contain(Some("1"))
    }

    "delete files" in new ITestApp {
      await(putFile())
      val r = FakeRequest(repoDataRoutes.deleteFiles(repoId, datasetId, stage))
        .withHeaders(Headers(
          HeaderNames.HOST -> "localhost"
        ))
        .withUser(privilegedUser)
        .callWith(Json.arr(testFileName))

      contentAsJson(r) must_== Json.arr(testFileName)
    }

    "validate files" in new ITestApp {
      await(putFile())
      val tag = DigestUtils.md5Hex(testPayload.utf8String)
      val r = FakeRequest(repoDataRoutes.validateFiles(repoId, datasetId, stage))
        .withHeaders(Headers(
          HeaderNames.HOST -> "localhost"
        ))
        .withUser(privilegedUser)
        .callWith(Json.obj(tag -> testFileName))

      // Validator will object because EAD without the namespace is not
      // recognised as EAD.
      contentAsJson(r) must_== Json.parse(
        s"""[{
          "key": "$testFileName",
          "eTag": "$tag",
          "errors": [{
            "line": 1,
            "pos": 6,
            "error": "element \\"ead\\" not allowed anywhere; expected element \\"ead\\" (with xmlns=\\"urn:isbn:1-931666-22-9\\")"
           }]
          }]"""
      )
    }
  }
}
