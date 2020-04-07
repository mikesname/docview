package controllers.institutions

import java.io.PrintWriter
import java.net.{URI, URLEncoder}
import java.util.UUID

import actors.IngestActor
import akka.actor.Props
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import services.data.{AuthenticatedUser, DataHelpers}
import services.ingest.IngestApi.{IngestData, IngestJob}
import services.ingest.{EadValidator, IngestApi, IngestParams, XmlValidationError}
import services.search._
import services.storage.{DOFileStorage, FileMeta, FileStorage}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

case class FileToUpload(name: String, `type`: String, size: Long)
object FileToUpload {
  implicit val _json: Format[FileToUpload] = Json.format[FileToUpload]
}

@Singleton
case class RepositoryData @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  fileStorage: FileStorage,
  eadValidator: EadValidator,
  ingestApi: IngestApi
)(
  implicit mat: Materializer
) extends AdminController
  with Read[Repository]
  with Update[Repository] {

  // TEMP TEMP TEMP TESTING FIXME FIXME FIXME FIXME
  override val staffOnly = false

  private val repositoryDataRoutes = controllers.institutions.routes.RepositoryData

  private val fileForm = Form(single("file" -> text))
  private val storage = DOFileStorage(config)(mat.system, mat)
  private val bucket = "ehri-assets"
  private def instance(implicit request: RequestHeader): String =
    URLEncoder.encode(config.getOptional[String]("storage.instance").getOrElse(request.host), "UTF-8")
  private def prefix(id: String)(implicit request: RequestHeader): String = s"$instance/ingest/$id/"



  def manager(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.data.manager(request.item))
  }

  def validateEad(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.validateEad(Map.empty[String, Seq[XmlValidationError]], request.item, fileForm,
      repositoryDataRoutes.validateEadPost(id)))
  }

  def validateEadPost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    request.body.asMultipartFormData.map { data =>
      val results: Seq[Future[(String, Seq[XmlValidationError])]] = data.files.map { file =>
        eadValidator.validateEad(file.ref.toPath).map(errs => file.filename -> errs)
      }

      Future.sequence(results).map { out =>
        Ok(views.html.admin.repository.validateEad(out.sortBy(_._1).toMap, request.item, fileForm,
          repositoryDataRoutes.validateEadPost(id)))
      }
    }.getOrElse {
      immediate(Redirect(repositoryDataRoutes.validateEad(id)))
    }
  }

  def validateEadFromStorage(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.streamFiles(bucket, prefix = Some(prefix(id))).runWith(Sink.seq).flatMap  { files =>
      val results: Seq[Future[(String, Seq[XmlValidationError])]] = files.map { file =>
        eadValidator.validateEad(Uri(storage.uri(bucket, file.key).toString))
          .map(errs => file.key.replace(prefix(id), "") -> errs)
      }

      Future.sequence(results).map { out =>
        Ok(views.html.admin.repository.validateEad(out.sortBy(_._1).toMap, request.item, fileForm,
          repositoryDataRoutes.validateEadPost(id)))
      }
    }
  }

  def validateFiles(id: String): Action[Seq[String]] = Action.async(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id)}$path")
    val results: Seq[Future[(String, Seq[XmlValidationError])]] = keys.map { key =>
      eadValidator.validateEad(Uri(storage.uri(bucket, key).toString))
        .map(errs => key.replace(prefix(id), "") -> errs)
    }
    Future.sequence(results).map { out =>
      Ok(Json.toJson(out.toMap))
    }
  }

  def listFiles(id: String, path: Option[String], after: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(bucket, prefix = Some(prefix(id + path.getOrElse(""))), after, max = 50).map { list =>
      Ok(Json.toJson(list.copy(files = list.files.map(f => f.copy(key = f.key.replace(prefix(id), ""))))))
    }
  }

  def deleteFiles(id: String): Action[Seq[String]] = EditAction(id).async(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id)}$path")
    storage.deleteFiles(bucket, keys: _*).map { deleted =>
      Ok(Json.toJson(deleted.map(_.replace(prefix(id), ""))))
    }
  }

  def deleteAll(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>

    def deleteBatch(batch: Seq[FileMeta]): Future[Boolean] = {
      storage
        .deleteFiles(bucket, batch.map(_.key): _*)
        .map(_.size == batch.size)
    }

    val r: Future[Boolean] = storage
      .streamFiles(bucket, Some(prefix(id)))
      .grouped(200)
      .mapAsync(2)(deleteBatch)
      .runWith(Sink.seq)
      .map((s: Seq[Boolean]) => s.forall(g => g))
    r.map { (ok: Boolean) =>
      Ok(Json.obj("ok" -> ok))
    }
  }

  def fileUrls(id: String): Action[Seq[String]] = EditAction(id).apply(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id)}$path")
    val result = keys.map(key => key.replace(prefix(id), "") -> storage.uri(bucket, key)).toMap
    Ok(Json.toJson(result))
  }

  def ingestFiles(id: String): Action[Seq[String]] = Action.async(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id)}$path")
    val urls = keys.map(key => storage.uri(bucket, key)).mkString("\n")

    // Tag this task with a unique ID...
    val jobId = UUID.randomUUID().toString

    // Type is text, since it's just a list of URLs
    val contentType = play.api.http.ContentTypes.TEXT

    val temp = SingletonTemporaryFileCreator.create("ingest", ".txt")
    val writer = new PrintWriter(temp.path.toString, "UTF-8")
    writer.write(urls)
    writer.close()

    val task = IngestParams(
      scopeType = defines.ContentTypes.Repository,
      scope = id,
      log = "Ingest TEST TEST TEST",
      file = Some(temp)
    )

    val ingestTask = IngestData(task, IngestApi.IngestDataType.EadSync, contentType, AuthenticatedUser("mike"))
    val runner = mat.system.actorOf(Props(IngestActor(ingestApi)), jobId)
    runner ! IngestJob(jobId, ingestTask)

    immediate {
      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks.taskMonitorWS(jobId).webSocketURL(globalConfig.https),
        "jobId" -> jobId
      ))
    }
  }

  def uploadHandle(id: String): Action[FileToUpload] = EditAction(id).apply(parse.json[FileToUpload]) { implicit request =>
    val path = s"${prefix(id)}${request.body.name}"
    val url = storage.uri(bucket, path, contentType = Some(request.body.`type`))
    Ok(Json.obj("presignedUrl" -> url))
  }

  def uploadData(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.streamFiles(bucket, prefix = Some(prefix(id))).runWith(Sink.seq).map  { files =>
      val stripPrefix = files.map(f => f.copy(key = f.key.replaceFirst(prefix(id), "") ))
      Ok(views.html.admin.repository.uploadData(request.item, stripPrefix, fileForm,
        repositoryDataRoutes.uploadDataPost(id)))
    }
  }

  def uploadDataDirect(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.streamFiles(bucket, prefix = Some(prefix(id))).runWith(Sink.seq).map  { files =>
      val stripPrefix = files.map(f => f.copy(key = f.key.replaceFirst(prefix(id), "") ))
      if (isAjax) Ok(views.html.admin.repository.uploadDataList(request.item, stripPrefix))
      else Ok(views.html.admin.repository.uploadData(request.item, stripPrefix, fileForm,
        repositoryDataRoutes.uploadDataDirect(id)))
    }
  }

  def uploadDataDirectPost(id: String, fileName: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    val path = s"${prefix(id)}$fileName"
    val uri = storage.uri(bucket, path, contentType = request.contentType)
    Ok(Json.obj("uri" -> uri))
  }

  def deleteDataPost(id: String, fileName: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.deleteFiles(bucket, s"${prefix(id)}$fileName").map { r =>
      Ok(Json.obj("ok" -> true))
    }
  }

  def uploadDataPost(id: String): Action[AnyContent] = EditAction(id).async(parse.anyContent(Some(parse.UNLIMITED))) { implicit request =>
    request.body.asMultipartFormData.map { data =>

      val uris: Seq[Future[URI]] = data.files.map { file =>
        val path = s"${prefix(id)}${file.filename}"
        storage.putFile(bucket, path, file.ref.path.toFile)
      }
      Future.sequence(uris).map { _ =>
        Redirect(repositoryDataRoutes.uploadData(id))
          .flashing("success" -> "That worked!")
      }
    }.getOrElse {
      immediate(Redirect(repositoryDataRoutes.uploadData(id)))
    }
  }
}