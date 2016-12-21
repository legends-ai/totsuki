package io.asuna.totsuki

import cats.implicits._
import cats.Apply
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import io.asuna.proto.bacchus.BacchusData.RawMatch
import java.io.{ PipedInputStream, PipedOutputStream }
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.spark._
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka010.{ ConsumerStrategies, KafkaUtils, LocationStrategies }
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Success, Try }

object Main {

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("totsuki")

    val region = args(0)

    // The topic, which is just our region.
    val topic = s"bacchus.matches.${region}"

    println(s"Initializing Totsuki on topic ${topic}")

    // Write parquet every 60 seconds. TODO(igm): make this configurable
    val ssc = new StreamingContext(conf, Seconds(60))

    // hostname:port for Kafka brokers, not Zookeeper
    val kafkaParams = Map(
      "metadata.broker.list" -> "localhost:9092",
      "key.deserializer" -> classOf[ByteArrayDeserializer],
      "value.deserializer" -> classOf[ByteArrayDeserializer]
    )

    val stream = KafkaUtils.createDirectStream[Array[Byte], Array[Byte]](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[Array[Byte], Array[Byte]](Set(topic), kafkaParams)
    )

    stream.foreachRDD { (rdd, time) =>
      // First, let's get the byte array.
      val byteRDD = rdd.map(_.value())

      // Next, let's parse all the matches.
      // TODO(igm): figure out if there's a way for us to not have to parse the entire message.
      // Possibly a nested message? need to benchmark.
      val tryParsed = byteRDD.map(bytes => Try { RawMatch.parseFrom(bytes) })
      val parsed = tryParsed.collect { case Success(x) => x }

      // We'll split the RDD up now by version.
      // TODO(pradyuman): change patch to version in BacchusData.RawMatch
      // First, let's collect a list of all distinct versions.
      val versions = parsed.map(_.patch).distinct.collect

      // Now, we'll create a dataframe for each version and write it out.
      versions.foreach { version =>
        val matches = parsed.filter(_.patch == version).collect
        implicit val ec = ExecutionContext.global
        writeMatches(region, version, time.milliseconds, matches)
      }
    }
  }

  def writeMatches(region: String, version: String, millis: Long, matches: Array[RawMatch])(implicit ec: ExecutionContext): Unit = {
    val is = new PipedInputStream()
    val os = new PipedOutputStream(is)

    // Here we write

    val s3 = new AmazonS3Client()

    // Here we read the matches and write it to the output stream.
    val writeFut = Future {
      matches.foreach { rawMatch =>
        rawMatch.writeDelimitedTo(os)
      }
    }

    // Here we read the input stream and write it to S3.
    val readFut = Future {
      s3.putObject("bacchus-out",
                   s"${region}/${version}/${millis}.protolist", is, new ObjectMetadata())
    }

    // Now we read and write concurrently
    Await.result(Apply[Future].tuple2(writeFut, readFut), Duration.Inf)

    // Close streams when we are done
    is.close()
    os.close()
  }

}
