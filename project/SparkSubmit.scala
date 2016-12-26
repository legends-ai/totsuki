import sbtsparksubmit.SparkSubmitPlugin.autoImport._

object SparkSubmit {
  lazy val settings =
    SparkSubmitSetting(
      "spark-submit-local",
      Seq(
        "--conf", "spark.cassandra.connection.host=127.0.0.1",
        "--class", "io.asuna.totsuki.Main",
        "--master", "local[4]"
      ),
      Seq("NA", "localhost:9092")
    )
}
