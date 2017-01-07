package io.asuna.totsuki

import buildinfo.BuildInfo
import io.asuna.asunasan.ConfigParser

case class TotsukiConfig(
  bucket: String = "totsuki-fragments-dev",
  writeInterval: Int = 60,
  bacchusKeyspace: String = "bacchus_dev",
  cassandraHosts: Seq[String] = Seq("localhost:9042")
)

object TotsukiConfigParser extends ConfigParser[TotsukiConfig](
  myService = BuildInfo.name,
  version = BuildInfo.version,
  port = 21215,
  healthPort = 21216,
  initial = TotsukiConfig()
) {

  opt[String]("bucket")
    .text("S3 bucket to store fragments.")
    .valueName("<servers>")
    .action((x, c) => c.copy(service = c.service.copy(bucket = x)))

  opt[Int]("write_interval")
    .text("Interval to flush matches to S3.")
    .valueName("<seconds>")
    .action((x, c) => c.copy(service = c.service.copy(writeInterval = x)))

  opt[String]("bacchus_keyspace")
    .text("Cassandra keyspace for Bacchus.")
    .valueName("<keyspace>")
    .action((x, c) => c.copy(service = c.service.copy(bacchusKeyspace = x)))

  opt[Seq[String]]("cassandra_hosts")
    .text("Cassandra hosts.")
    .valueName("<hosts>")
    .action((x, c) => c.copy(service = c.service.copy(cassandraHosts = x)))

}
