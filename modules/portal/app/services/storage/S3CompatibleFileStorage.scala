package services.storage

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.alpakka.s3._
import akka.stream.alpakka.s3.headers.CannedAcl
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import play.api.Logger
import software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.AwsRegionProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.{GetObjectPresignRequest, PutObjectPresignRequest}

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}


object S3CompatibleFileStorage {
  def apply(config: com.typesafe.config.Config)(implicit actorSystem: ActorSystem, mat: Materializer, ec: ExecutionContext): S3CompatibleFileStorage = {
    val credentials = StaticCredentialsProvider.create(new AwsCredentials {
      override def accessKeyId(): String = config.getString("config.aws.credentials.access-key-id")
      override def secretAccessKey(): String = config.getString("config.aws.credentials.secret-access-key")
    })
    val region: AwsRegionProvider = new AwsRegionProvider {
      override def getRegion: Region = Region.of(config.getString("config.aws.region.default-region"))
    }

    val endpoint: Option[String] = if (config.hasPath("config.endpoint-url"))
      Some(config.getString("config.endpoint-url"))
    else None

    val bucket = config.getString("classifier")

    new S3CompatibleFileStorage(credentials, region, bucket, endpoint)
  }
}

case class S3CompatibleFileStorage(
  credentials: AwsCredentialsProvider,
  region: AwsRegionProvider,
  name: String,
  endpointUrl: Option[String] = None
)(implicit actorSystem: ActorSystem, mat: Materializer, ec: ExecutionContext) extends FileStorage {
  private val logger = Logger(getClass)

  private val s3Settings = endpointUrl
    .foldLeft(S3Settings.create(
      MemoryBufferType,
      credentials,
      region,
      ApiVersion.ListBucketVersion1
    ))(_.withEndpointUrl(_))
    .withAccessStyle(AccessStyle.pathAccessStyle)

  private val clientBuilder = S3Client.builder()
    .credentialsProvider(credentials)
    .region(region.getRegion)
  private val client = endpointUrl
    .fold(clientBuilder)(url => clientBuilder.endpointOverride(URI.create(url)))
    .build()

  override def uri(path: String, duration: FiniteDuration = 10.minutes, contentType: Option[String] = None, versionId: Option[String] = None): URI = {
    val expire = java.time.Duration.ofNanos(duration.toNanos)
    val preSignerBuilder = S3Presigner.builder()
      .credentialsProvider(credentials)
      .region(region.getRegion)
    val preSigner = endpointUrl.fold(preSignerBuilder)(url => preSignerBuilder.endpointOverride(URI.create(url)))
      .build()

    contentType.map { ct =>
      val r = PutObjectRequest.builder()
        .contentType(ct)
        .bucket(name)
        .key(path)
        .build()
      val pr = PutObjectPresignRequest.builder()
        .putObjectRequest(r)
        .signatureDuration(expire)
        .build()
      preSigner.presignPutObject(pr).url().toURI
    }.getOrElse {
      val rb = GetObjectRequest.builder()
        .bucket(name)
        .key(path)
      val r = versionId.fold(rb)(id => rb.versionId(id)).build()
      val pr = GetObjectPresignRequest.builder()
        .getObjectRequest(r)
        .signatureDuration(expire)
        .build()
      preSigner.presignGetObject(pr).url().toURI
    }
  }

  override def count(prefix: Option[String]): Future[Int] =
    countFilesWithPrefix(prefix)

  override def info(path: String, versionId: Option[String] = None): Future[Option[(FileMeta, Map[String, String])]] = Future {
    val rb = HeadObjectRequest.builder()
      .bucket(name)
      .key(path)

    val r = versionId.fold(rb)(id => rb.versionId(id)).build()
    try {
      val meta = client.headObject(r)
      val fm = FileMeta(
        name,
        path,
        meta.lastModified,
        meta.contentLength,
        Option(meta.eTag),
        Option(meta.contentType),
        Option(meta.versionId)
      )
      Some((fm, meta.metadata.asScala.toSeq.toMap))
    } catch {
      case _: SdkException => None
    }
  }(ec)

  override def get(path: String, versionId: Option[String] = None): Future[Option[(FileMeta, Source[ByteString, _])]] = {
    S3.download(name, path, versionId = versionId)
      .withAttributes(S3Attributes.settings(s3Settings))
      .runWith(Sink.headOption).map(_.flatten)
      .map {
        case Some((src, meta)) => Some(infoToMeta(path, meta) -> src)
        case _ => None
      }
  }

  override def putBytes(path: String, src: Source[ByteString, _], contentType: Option[String] = None,
    public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] = {
    val cType = contentType.map(ContentType.parse) match {
      case Some(Right(ct)) => ct
      case _ =>
        val mediaType: MediaType = MediaTypes.forExtension(path.substring(path.lastIndexOf(".") + 1))
        ContentType(mediaType, () => HttpCharsets.`UTF-8`)
    }
    logger.debug(s"Uploading file: $path to $name with content-type: $contentType")
    val acl = if (public) CannedAcl.PublicRead else CannedAcl.AuthenticatedRead

    val uploader = S3.multipartUpload(name, path, contentType = cType, cannedAcl = acl, metaHeaders = MetaHeaders(meta))
      .withAttributes(S3Attributes.settings(s3Settings))

    src.runWith(uploader).map(r => URI.create(r.location.toString()))
  }

  override def putFile(path: String, file: java.io.File, contentType: Option[String] = None,
    public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] =
    putBytes(path, FileIO.fromPath(file.toPath), contentType, public, meta)

  override def deleteFiles(paths: String*): Future[Seq[String]] =
    if (paths.isEmpty) Future.successful(Seq.empty) else Future(deleteKeys(paths))(ec)

  override def deleteFilesWithPrefix(prefix: String): Future[Seq[String]] = Future {
    @scala.annotation.tailrec
    def deleteBatch(done: Seq[String] = Seq.empty): Seq[String] = {
      val fm = listPrefix(Some(prefix), done.lastOption, max = 1000)
      val keys = fm.files.map(_.key)
      deleteKeys(keys)
      if (fm.truncated) deleteBatch(done ++ keys)
      else done ++ keys
    }

    deleteBatch()
  }(ec)

  override def streamFiles(prefix: Option[String]): Source[FileMeta, NotUsed] = {
    S3.listBucket(name, prefix)
      .withAttributes(S3Attributes.settings(s3Settings))
      .map(f => FileMeta(
        name,
        f.key,
        f.lastModified,
        f.size,
        // NB: S3 returns eTags wrapped in quotes, but Alpakka doesn't
        // hence for compatibility we add it here.
        Some(f.eTag).map(f => "\"" + f + "\"")
      ))
  }

  override def listFiles(prefix: Option[String], after: Option[String], max: Int): Future[FileList] = Future {
    listPrefix(prefix, after, max)
  }(ec)

  override def listVersions(path: String, after: Option[String] = None): Future[FileList] =
    listVersions(Some(path), None, after, max = 200)

  override def setVersioned(enabled: Boolean): Future[Unit] = Future {
    val status = if (enabled) BucketVersioningStatus.ENABLED
    else BucketVersioningStatus.SUSPENDED
    val vc = VersioningConfiguration.builder().status(status).build()
    val bvc = PutBucketVersioningRequest.builder()
      .bucket(name)
      .versioningConfiguration(vc)
      .build()

    client.putBucketVersioning(bvc)
    ()
  }(ec)

  override def isVersioned: Future[Boolean] = Future {
    val bvr = GetBucketVersioningRequest.builder().bucket(name).build()
    val bvc = client.getBucketVersioning(bvr)
    bvc.status() == BucketVersioningStatus.ENABLED
  }(ec)

  override def fromUri(uri: URI): Future[Option[(FileMeta, Source[ByteString, _])]] = {
    if (uri.getHost.startsWith(name)) get(uri.getPath)
    else if (uri.getPath.startsWith("/" + name)) get(uri.getPath.substring(name.length + 1))
    else Future.successful(Option.empty)
  }

  private def infoToMeta(path: String, meta: ObjectMetadata): FileMeta = FileMeta(
    name,
    path,
    java.time.Instant.ofEpochMilli(meta.lastModified.clicks),
    meta.getContentLength,
    meta.eTag,
    meta.contentType,
    meta.versionId
  )

  private def countFilesWithPrefix(prefix: Option[String] = None): Future[Int] = Future {
    @scala.annotation.tailrec
    def countBatch(done: Int = 0, last: Option[String] = None): Int = {
      val fm = listPrefix(prefix, last, max = 1000)
      val count = fm.files.size
      if (fm.truncated) countBatch(done + count, fm.files.lastOption.map(_.key))
      else done + count
    }

    countBatch()
  }(ec)

  private def listVersions(prefix: Option[String], after: Option[String], afterVersion: Option[String], max: Int): Future[FileList] = Future {
    listPrefixVersions(prefix, after, afterVersion, max)
  }(ec)

  private def listPrefixVersions(prefix: Option[String], after: Option[String], afterVersion: Option[String], max: Int): FileList = {
    val rb = ListObjectVersionsRequest.builder()
      .bucket(name)
      .maxKeys(max)
      .prefix(prefix.getOrElse(""))
    val rb2 = after.fold(rb)(key => rb.keyMarker(key))
    val lvr = afterVersion.fold(rb2)(id => rb2.versionIdMarker(id)).build()

    val r = client.listObjectVersions(lvr)
    FileList(r.versions().asScala.toSeq.map { f =>
      FileMeta(
        name,
        f.key,
        f.lastModified,
        f.size,
        eTag = Some(f.eTag),
        versionId = Some(f.versionId)
      )
    }, r.isTruncated)
  }

  private def deleteKeys(paths: Seq[String]): Seq[String] = {
    if (paths.isEmpty) paths else {
      val delete = Delete.builder()
        .objects(paths.map(key => ObjectIdentifier.builder().key(key).build()): _*)
        .build()
      val dor = DeleteObjectsRequest.builder()
        .bucket(name)
        .delete(delete)
        .build()
      client.deleteObjects(dor).deleted().asScala.map(_.key).toSeq
    }
  }

  private def listPrefix(prefix: Option[String], after: Option[String], max: Int) = {
    // FIXME: Update this to ListObjectsV2 when Digital Ocean
    // implement support for the StartAfter parameter.
    // For now the marker param in ListObject (v1) seems
    // to do the same thing.
    val rb = ListObjectsRequest.builder()
      .bucket(name)
      .maxKeys(max)
    val rb2 = after.fold(rb)(key => rb.marker(key))
    val lor = prefix.fold(rb2)(p => rb2.prefix(p)).build()

    val resp = client.listObjects(lor)
      FileList(resp.contents().asScala.toSeq.map { f =>
      FileMeta(
        name,
        f.key,
        f.lastModified,
        f.size,
        Some(f.eTag),
      )
    }.toList, resp.isTruncated)
  }
}
