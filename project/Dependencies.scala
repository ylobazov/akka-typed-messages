import sbt._

object Dependencies {

  val configVersion                 = "1.3.1"
  val circeVersion                  = "0.8.0"
  val akkaVersion                   = "2.5.8"
  val akkaHttpVersion               = "10.0.+"
  val scalaLoggingVersion           = "3.8.0"

  val scalaTestVersion    = "3.0.+"
  val scalaCheckVersion   = "1.13.+"
  val logbackVersion     = "1.2.3"
  val slf4jVersion       = "1.7.25"

  val logback =
    "ch.qos.logback"              %  "logback-classic"           % logbackVersion           % "provided"
  val slf4j =
    "org.slf4j"                  %  "slf4j-api"                  % slf4jVersion             % "provided"

  val scalaLogging =
    "com.typesafe.scala-logging" %% "scala-logging"              % scalaLoggingVersion      % "provided"

  val circeCore =
    "io.circe"                   %%  "circe-core"                % circeVersion             % "optional"
  val circeGeneric =
    "io.circe"                   %%  "circe-generic"             % circeVersion             % "optional"
  val circeGenericExtras =
    "io.circe"                   %%  "circe-generic-extras"      % circeVersion             % "optional"
  val circeJava8 =
    "io.circe"                   %%  "circe-java8"               % circeVersion             % "optional"
  val circeParser =
    "io.circe"                   %% "circe-parser"               % circeVersion             % "optional"

  val akka =
    "com.typesafe.akka"          %%  "akka-actor"                % akkaVersion              % "optional"
  val akkaTestkit =
    "com.typesafe.akka"          %% "akka-testkit"               % akkaVersion
  val akkaHttp =
    "com.typesafe.akka"          %% "akka-http"                  % akkaHttpVersion          % "optional"
  val akkaHttpTestkit =
    "com.typesafe.akka"          %% "akka-http-testkit"          % akkaHttpVersion          % "test"

  val scalaTest =
    "org.scalatest"              %% "scalatest"                  % scalaTestVersion         % "test, it"

  val scalaCheck =
    "org.scalacheck"             %% "scalacheck"                 % scalaCheckVersion        % "test, it"


  val commonDeps = Seq(
    logback,
    slf4j,
    scalaLogging,
    scalaTest,
    scalaCheck,
    akka
  )

  val typedActorModuleDeps = commonDeps :+ akkaTestkit % "test, it"
  val typedTestkitModuleDeps = commonDeps :+ akkaTestkit % "compile, test, it"
  val typedHttpModuleDeps = commonDeps ++
    Seq(akkaHttp, akkaHttpTestkit, circeCore, circeGeneric, circeGenericExtras, circeJava8, circeParser)

}
