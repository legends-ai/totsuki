package io.asuna.totsuki

import buildinfo.BuildInfo
import scopt.OptionParser

case class Config (
  region: String = "NA",
  bootstrapServers: String = "localhost:9092"
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
