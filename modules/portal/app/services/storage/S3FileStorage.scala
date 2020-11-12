package services.storage

import java.net.URI

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider}
import com.amazonaws.regions.AwsRegionProvider
import javax.inject.Inject

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}


case class S3FileStorage @Inject()(config: play.api.Configuration)(implicit actorSystem: ActorSystem, mat: Materializer) extends FileStorage {

  private implicit val ec: ExecutionContext = mat.executionContext

  private val creds = new AWSCredentialsProvider {
    override def getCredentials: AWSCredentials = new AWSCredentials {
      override def getAWSAccessKeyId: String = config.get[String]("alpakka.s3.aws.credentials.access-key-id")
      override def getAWSSecretKey: String = config.get[String]("alpakka.s3.aws.credentials.secret-access-key")
    }
    override def refresh(): Unit = {}
  }

  private val region = new AwsRegionProvider {
    override def getRegion: String = config.get[String]("alpakka.s3.aws.region.default-region")
  }

  private val ops = S3CompatibleOperations(None, creds, region)

  override def info(classifier: String, path: String): Future[Option[FileMeta]] = ops.info(classifier, path)

  override def get(classifier: String, path: String): Future[Option[(FileMeta, Source[ByteString, _])]] =
    ops.get(classifier, path)

  override def uri(classifier: String, path: String, duration: FiniteDuration = 10.minutes, contentType: Option[String] = None): URI =
    ops.uri(classifier, path, duration, contentType)

  override def putBytes(bucket: String, path: String, src: Source[ByteString, _], contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] =
    ops.putBytes(bucket, path, src, contentType, public, meta)

  override def putFile(classifier: String, path: String, file: java.io.File, contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] =
    ops.putFile(classifier, path, file, contentType, public, meta)

  override def streamFiles(classifier: String, prefix: Option[String]): Source[FileMeta, NotUsed] =
    ops.streamFiles(classifier, prefix)

  override def listFiles(classifier: String, prefix: Option[String], after: Option[String] = None, max:  Int = -1): Future[FileList] =
    ops.listFiles(classifier, prefix, after, max)

  override def deleteFiles(classifier: String, paths: String*): Future[Seq[String]] =
    ops.deleteFiles(classifier, paths: _*)

  override def deleteFilesWithPrefix(classifier: String, prefix: String): Future[Seq[String]] =
    ops.deleteFilesWithPrefix(classifier, prefix)

  override def count(classifier: String, prefix: Option[String]): Future[Int] =
    ops.countFilesWithPrefix(classifier, prefix)

  override def enableVersioning(classifier: String): Future[Unit] = ops.enableVersioning(classifier)
}
