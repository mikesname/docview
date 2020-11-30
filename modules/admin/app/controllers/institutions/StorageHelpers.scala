package controllers.institutions

import java.net.URLEncoder

import defines.FileStage
import play.api.Configuration
import play.api.mvc.RequestHeader

/**
  * Helpers for controllers which need to access files from storage
  * via Repository and dataset IDs and their associated bucket.
  */
trait StorageHelpers {

  protected def config: Configuration

  protected val bucket: String =
    config.get[String]("storage.dam.classifier")

  protected def instance(implicit request: RequestHeader): String =
    URLEncoder.encode(config.getOptional[String]("storage.instance").getOrElse(request.host), "UTF-8")

  protected def prefix(id: String, ds: String, stage: FileStage.Value)(implicit request: RequestHeader): String =
    s"$instance/$id/$ds/$stage/"
}
