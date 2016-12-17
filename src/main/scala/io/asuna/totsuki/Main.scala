package io.asuna.totsuki

import io.asuna.proto.charon.CharonData.Match
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.spark._
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka010.{ ConsumerStrategies, KafkaUtils, LocationStrategies }
import scala.util.{ Success, Try }

object Main {

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("totsuki")

    val region = args(0)

    // The topic, which is just our region.
    val topic = s"bacchus:matches:${region}"

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

    val sql = SparkSession.builder().config(conf).getOrCreate()
    import sql.implicits._

    stream.foreachRDD { (rdd, time) =>
      // First, let's get the byte array.
      val byteRDD = rdd.map(_.value())

      // Next, let's parse all the matches.
      // TODO(igm): figure out if there's a way for us to not have to parse the entire message.
      // Possibly a nested message? need to benchmark.
      val tryParsed = byteRDD.map(bytes => Try { Match.parseFrom(bytes) })
      val parsed = tryParsed.collect { case Success(x) => x }

      // We'll split the RDD up now by version.
      // First, let's collect a list of all distinct versions.
      val versions = parsed.map(_.version).distinct.collect

      // Now, we'll create a dataframe for each version and write it out.
      versions.foreach { version =>
        // Serialize back to byte array
        val serialized = parsed.filter(_.version == version).map(_.toByteArray)

        val ds = serialized.toDS()

        // Write out to s3
        val outFile = s"s3n://bacchus-out/${region}/${version}/${time.milliseconds}.parquet"
        ds.write.parquet(outFile)
      }
    }
  }

}
