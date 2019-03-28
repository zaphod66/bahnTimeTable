name := """bahn-timetable"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

scalaVersion := "2.12.1"
//val scalazVersion = "7.2.15"
//
//libraryDependencies ++= Seq(
//  "org.scalaz" %% "scalaz-core" % scalazVersion,
//  "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
//  "org.scalaz" %% "scalaz-effect" % scalazVersion,
//  "org.scalaz" %% "scalaz-scalacheck-binding" % scalazVersion % "test",
//)

//libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
//libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
//libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.4" % "test"

libraryDependencies += "com.softwaremill.sttp" %% "core" % "0.0.17"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test
libraryDependencies += "com.h2database" % "h2" % "1.4.194"

scalacOptions += "-feature"

initialCommands in console := "import scalaz._, Scalaz._"

