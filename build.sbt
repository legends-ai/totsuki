name := "totsuki"
organization := "io.asuna"
version := "0.1.0"
scalaVersion := "2.11.8"
scalaVersion in ThisBuild := "2.11.8"

libraryDependencies ++= Seq(
  // Spark stuff
  "org.apache.spark" %% "spark-core" % "2.0.2" % "provided",
  "org.apache.spark" %% "spark-streaming" % "2.0.2" % "provided",
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.0.2" % "provided",

  // Asuna standard lib
  "io.asuna" %% "asunasan" % "0.7.2",

  // Scalatest
  "org.scalactic" %% "scalactic" % "3.0.0" % "test",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.3" % "test" // prop tests
)

mainClass in assembly := Some("io.asuna.totsuki.Main")

assemblyMergeStrategy in assembly := {
  case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
  case x if x contains "publicsuffix" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
assemblyJarName in assembly := s"${name.value}-assembly.jar"

awsProfile := "asuna"
s3region := com.amazonaws.services.s3.model.Region.US_West
s3acl := com.amazonaws.services.s3.model.CannedAccessControlList.AuthenticatedRead

// Resolver
resolvers ++= Seq[Resolver](
  Resolver.bintrayRepo("websudos", "oss-releases"),
  s3resolver.value("Aincrad", s3("aincrad.asuna.io"))
)

// testing
testOptions in Test += Tests.Argument("-oDF")

// Needed for ENSIME
scalaVersion in ThisBuild := "2.11.8"

// Docker stuff
enablePlugins(DockerPlugin)

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("gettyimages/spark:2.0.2-hadoop-2.7")
    add(artifact, artifactTargetPath)
    entryPoint("spark-submit",
               "--conf", "spark.cassandra.connection.host=127.0.0.1",
               "--class", "ai.legends.athena.Main",
               "--master", "local[4]", artifactTargetPath)
  }
}

val base = "096202052535.dkr.ecr.us-west-2.amazonaws.com"
imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"${base}/${name.value}:latest"),
  ImageName(s"${base}/${name.value}:${version.value}")
)
