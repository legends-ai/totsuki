package io.asuna.totsuki

import buildinfo.BuildInfo
import scopt.OptionParser

case class Config (
  region: String = "NA",
  bootstrapServers: String = "localhost:9092",
  bucket: String = "totsuki_fragments",
  writeInterval: Int = 60
)

object Config {

  val parser = new OptionParser[Config](BuildInfo.name) {

    head(BuildInfo.name, BuildInfo.version)

    opt[String]("region")
      .text("The region we're reading matches from. Note: this is not the S3 region. Defaults to `na`.")
      .valueName("<na|euw|eune|...>")
      .action((x, c) => c.copy(region = x))

    opt[String]("bootstrap_servers")
      .text("Kafka bootstrap servers.")
      .valueName("<servers>")
      .action((x, c) => c.copy(bootstrapServers = x))

    opt[String]("bucket")
      .text("S3 bucket to store fragments.")
      .valueName("<servers>")
      .action((x, c) => c.copy(bucket = x))

    opt[Int]("write_interval")
      .text("Interval to flush matches to S3.")
      .valueName("<seconds>")
      .action((x, c) => c.copy(writeInterval = x))
  }

  def mustParse(args: Array[String]): Config = {
    val result = parser.parse(args, Config())
    if (!result.isDefined) {
      // couldn't parse
      sys.exit(0)
    }
    result.get
  }

}
