val scala3Version = "3.6.2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "OpenSearch SQL Distributed Testing",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "1.1.0" % Test,
    libraryDependencies += "org.scalameta" %% "munit-scalacheck" % "1.1.0" % Test,
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.18.1",
    libraryDependencies += "org.opensearch.client" % "opensearch-java" % "2.20.0",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "4.1.0",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.13.0",
    libraryDependencies += "com.github.pureconfig" %% "pureconfig-core" % "0.17.8"
  )
