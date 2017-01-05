package io.asuna.totsuki

import cats.implicits._
import cats.Apply
import com.amazonaws.services.s3.transfer.{ TransferManager, Upload }
import io.asuna.proto.bacchus.BacchusData.RawMatch
import java.io.{ File, FileOutputStream }
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.spark._
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka010.{ ConsumerStrategies, KafkaUtils, LocationStrategies }
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Success, Try }

object Main {

  def main(args: Array[String]): Unit = {
    val cfg = TotsukiConfigParser.mustParse(args).service
    val conf = new SparkConf().setAppName("totsuki")

    val region = cfg.region

    // The topic, which is just our region.
    val topic = s"bacchus.matches.${region}"

    println(s"Initializing Totsuki on topic ${topic}")

    // Write every 60 seconds. TODO(igm): make this configurable
    val ssc = new StreamingContext(conf, Seconds(cfg.writeInterval))

    // hostname:port for Kafka brokers, not Zookeeper
    val kafkaParams = Map(
      "bootstrap.servers" -> cfg.bootstrapServers,
      "group.id" -> cfg.region,
      "key.deserializer" -> classOf[ByteArrayDeserializer],
      "value.deserializer" -> classOf[ByteArrayDeserializer],
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    val stream = KafkaUtils.createDirectStream[Array[Byte], Array[Byte]](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[Array[Byte], Array[Byte]](Set(topic), kafkaParams)
    )

    // S3 Transfer Manager
    implicit val tx = new TransferManager()

    stream.foreachRDD { (rdd, time) =>
      // First, let's get the byte array.
      val byteRDD = rdd.map(_.value())

      // Next, let's parse all the matches.
      // TODO(igm): figure out if there's a way for us to not have to parse the entire message.
      // Possibly a nested message? need to benchmark.
      val tryParsed = byteRDD.map(bytes => Try { RawMatch.parseFrom(bytes) })
      val parsed = tryParsed.collect { case Success(x) => x }

      // We'll split the RDD up now by version.
      // First, let's collect a list of all distinct versions.
      val versions = parsed.map(_.version).distinct.collect

      // Now, we'll create a dataframe for each version and write it out.
      versions.foreach { version =>
        val matches = parsed.filter(_.version == version).collect
        implicit val ec = ExecutionContext.global
        writeMatches(cfg.bucket, region, version, time.milliseconds, matches)
      }
    }

    ssc.start()
    ssc.awaitTermination()
    tx.shutdownNow()
  }

  def writeMatches(
    bucket: String, region: String, version: String, millis: Long, matches: Array[RawMatch]
  )(implicit ec: ExecutionContext, tx: TransferManager): Unit = {
    val file = File.createTempFile("totsuki", ".tmp")
    val fs = new FileOutputStream(file)

    // Here we write the matches to the tempfile.
    matches.foreach { rawMatch =>
      rawMatch.writeDelimitedTo(fs)
    }
    fs.close()

    // Here we upload the temp file to S3.
    // TransferManager will automatically make this multipart if it will improve performance.
    tx.upload(bucket,
              s"${region}/${version}/${millis}.protolist", file).waitForCompletion()

    // Now we delete the tempfile since the S3 upload is complete
    file.delete()
  }

}
