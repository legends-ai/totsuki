package io.asuna.totsuki

import buildinfo.BuildInfo
import io.asuna.asunasan.ConfigParser
import scopt.OptionParser

case class TotsukiConfig(
  region: String = "NA",
  bootstrapServers: String = "localhost:9092",
  bucket: String = "totsuki_fragments",
  writeInterval: Int = 60
)

object TotsukiConfigParser extends ConfigParser[TotsukiConfig](
  myService = BuildInfo.name,
  version = BuildInfo.version,
  port = 21215,
  healthPort = 21216,
  initial = TotsukiConfig()
) {

  opt[String]("region")
    .text("The region we're reading matches from. Note: this is not the S3 region. Defaults to `na`.")
    .valueName("<na|euw|eune|...>")
    .action((x, c) => c.copy(service = c.service.copy(region = x)))

  opt[String]("bootstrap_servers")
    .text("Kafka bootstrap servers.")
    .valueName("<servers>")
    .action((x, c) => c.copy(service = c.service.copy(bootstrapServers = x)))

  opt[String]("bucket")
    .text("S3 bucket to store fragments.")
    .valueName("<servers>")
    .action((x, c) => c.copy(service = c.service.copy(bucket = x)))

  opt[Int]("write_interval")
    .text("Interval to flush matches to S3.")
    .valueName("<seconds>")
    .action((x, c) => c.copy(service = c.service.copy(writeInterval = x)))

}
