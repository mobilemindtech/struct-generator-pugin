
name := "Struct Generator Plugin"

lazy val root = project
  .in(file("."))
  .enablePlugins()
  .settings(
    organization := "br.com.mobilemind",
    name         := "sg4s",
    version      := "0.1.0-SNAPSHOT",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
    ),
    scalaVersion := "3.8.4",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.17.3"
    ),
    addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
  )

