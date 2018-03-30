import Dependencies._
import Settings._

scalaVersion in Global := "2.12.5"

lazy val typedActor = (project in file("typed-actor")).
  configs(IntegrationTest).
  settings(commonSettingsPack: _*).
  settings(libraryDependencies ++= typedActorModuleDeps)

lazy val typedTestkit = (project in file("typed-testkit")).
  configs(IntegrationTest).
  settings(commonSettingsPack: _*).
  settings(libraryDependencies ++= typedTestkitModuleDeps).
  dependsOn(typedActor)

lazy val typedMessagesHttp = (project in file("typed-messages-http")).
  configs(IntegrationTest).
  settings(commonSettingsPack: _*).
  settings(libraryDependencies ++= typedHttpModuleDeps).
  dependsOn(typedActor)

resolvers in Global ++= Seq(
  "Sbt plugins"                   at "https://dl.bintray.com/sbt/sbt-plugin-releases",
  "Maven Central Server"          at "http://repo1.maven.org/maven2",
  "TypeSafe Repository Releases"  at "http://repo.typesafe.com/typesafe/releases/",
  "TypeSafe Repository Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Akka Snapshot Repository"      at "http://repo.akka.io/snapshots/"
)

cancelable in Global := true
parallelExecution in ThisBuild := false

