import sbt.Keys._
import sbt._

object Settings {

  lazy val artifactSettings = Seq(
    organization := "com.github.ylobazov",
    version := "0.0.1-SNAPSHOT"
  )

  lazy val testSettings = Seq(
    fork in Test := false,
    parallelExecution in Test := false
  )

  lazy val integrationTestSettings = Defaults.itSettings ++ Seq(
    fork in IntegrationTest := false,
    parallelExecution in IntegrationTest := false,
    javaOptions in IntegrationTest := Seq("-Xmx2G", "-Xss256k", "-XX:+UseCompressedOops")
  )

  lazy val commonSettingsPack =
    artifactSettings ++
      testSettings ++
      integrationTestSettings

}
