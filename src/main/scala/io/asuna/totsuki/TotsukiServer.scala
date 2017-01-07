package io.asuna.totsuki

import cats.implicits._
import com.amazonaws.services.s3.transfer.TransferManager
import com.google.protobuf.empty.Empty
import io.asuna.proto.enums.Region
import io.asuna.proto.ids.MatchId
import java.io.{ File, FileOutputStream }
import java.util.UUID
import monix.execution.{ Ack, Scheduler }
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

  val db = TotsukiDatabase.fromConfig(config)

  // Create the RawMatch processor.
  stream

    // First, we will group matches by region/version.
    .groupBy { m =>
      (m.id.map(_.region).getOrElse(Region.UNDEFINED_REGION), m.data.map(_.version).orEmpty)
    }

    // We filter out matches that don't have a region or version.
    // We aren't using Option here in case we just have a malformed object.
    .filter { observable =>
      observable.key match {
        case (region, version) => region != Region.UNDEFINED_REGION && version != ""
      }
    }

    // Now we iterate over all of the region/version combinations.
    // TODO(igm): evaluate whether we want to wait potentially longer to store more matches.
    // We may also want to apply back pressure. See https://monix.io/api/2.1/monix/reactive/Observable.html.
    .foreach { obs =>
      val (region, version) = obs.key
      obs
        .bufferTimed(config.service.writeInterval.seconds)
        .subscribe(writeBucketMatches(region, version, _))
    }

  override def write(rawMatch: RawMatch): Future[Empty] = endpoint {
    println(s"Received match ${rawMatch.id}")
    stream.onNext(rawMatch).map(_ => Empty.defaultInstance)
  }

  /**
    *  Writes matches of a specific region/version combination.
    */
  def writeBucketMatches(region: Region, version: String, matches: Seq[RawMatch]): Future[Ack] = {
    // First, we generate a unique id to distinguish this bucket write.
    // We will also provide a timestamp to make it easy to know what the latest write was.
    val id = s"${System.currentTimeMillis}_${UUID.randomUUID()}"

    val rgName = region.name.toLowerCase()

    // Next, we create a temp file to write the matches to.
    // This makes it possible for us to do multipart uploads to S3, rather than streaming
    // directly to S3. There is one temp file per region/version pair per time.
    val file = File.createTempFile(s"totsuki_${rgName}_${version}_${id}", ".tmp")

    // Here we write the matches to the tempfile.
    val fs = new FileOutputStream(file)
    matches.foreach(_.writeDelimitedTo(fs))
    fs.close()

    // Here we upload the temp file to S3.
    // TransferManager will automatically make this multipart if it will improve performance.
    tx.upload(config.service.bucket,
              s"${rgName}/${version}/${id}.protolist", file).waitForCompletion()

    // Now we delete the tempfile since the S3 upload is complete.
    file.delete()

    // Add all of the match ids to cassandra.
    matches.map { m =>
      m.id.map { id =>
        // Add id to DB and return it if successful
        db.matchSet.add(id).map(_ => id)
      }
    }.collect {
      // Unwrap futures
      case Some(fut) => fut
    }.toList.sequence.map(_ => Ack.Continue)
  }

}
