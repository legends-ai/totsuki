package io.asuna.totsuki

import cats.implicits._
import com.amazonaws.services.s3.transfer.TransferManager
import com.google.protobuf.empty.Empty
import io.asuna.proto.enums.Region
import java.io.{ File, FileOutputStream }
import monix.execution.Scheduler
import scala.concurrent.duration._
import io.asuna.asunasan.BaseService
import io.asuna.proto.bacchus.BacchusData.RawMatch
import io.asuna.proto.service_totsuki.TotsukiGrpc
import monix.reactive.subjects.PublishToOneSubject
import scala.concurrent.{ Future }

class TotsukiServer(args: Seq[String])(implicit scheduler: Scheduler)
    extends BaseService(args, TotsukiConfigParser, TotsukiGrpc.bindService) with TotsukiGrpc.Totsuki {

  // S3 transfer manager. This lets us open files.
  val tx = new TransferManager()

  val stream = PublishToOneSubject[RawMatch]()

  // Create the RawMatch processor
  stream
    .bufferTimed(config.service.writeInterval.seconds)
    .foreach(writeMatches)

  override def write(rawMatch: RawMatch): Future[Empty] = endpoint {
    for {
      _ <- stream.onNext(rawMatch)
    } yield Empty.defaultInstance
  }

  def writeMatches(matches: Seq[RawMatch]): Unit = {
    matches
      // First, we will group matches by region/version.
      .groupBy { m =>
        (m.id.map(_.region).getOrElse(Region.UNDEFINED_REGION), m.data.map(_.version).orEmpty)
      }

      // We filter out matches that don't have a region or version.
      // We aren't using Option here in case we get an undefined region or version object.
      .filterKeys {
        case (region, version) => region != Region.UNDEFINED_REGION && version != ""
      }

      // Then, we write the matches of each bucket.
      .foreach {
        case ((region, version), matches) => writeBucketMatches(region, version, matches)
      }
  }

  /**
    *  Writes matches of a specific region/version pair.
    */
  def writeBucketMatches(region: Region, version: String, matches: Seq[RawMatch]): Unit = {
    // First, we fetch a timestamp to distinguish this bucket write.
    val time = System.currentTimeMillis()

    val rgName = region.name.toLowerCase()

    // Next, we create a temp file to write the matches to.
    // This makes it possible for us to do multipart uploads to S3, rather than streaming
    // directly to S3. There is one temp file per region/version pair per time.
    val file = File.createTempFile(s"totsuki-${rgName}-${version}-${time}", ".tmp")

    // Here we write the matches to the tempfile.
    val fs = new FileOutputStream(file)
    matches.foreach(_.writeDelimitedTo(fs))
    fs.close()

    // Here we upload the temp file to S3.
    // TransferManager will automatically make this multipart if it will improve performance.
    tx.upload(config.service.bucket,
              s"${rgName}/${version}/${time}.protolist", file).waitForCompletion()

    // Now we delete the tempfile since the S3 upload is complete.
    file.delete()
  }

}
