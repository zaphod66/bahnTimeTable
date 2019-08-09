name := """bahn-timetable"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

scalaVersion := "2.12.6"


//libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
//libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
//libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.4" % "test"

libraryDependencies += "com.softwaremill.sttp" %% "core" % "1.5.19"

libraryDependencies += "cn.playscala" % "play-mongo_2.12" % "0.3.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.0"

libraryDependencies += "org.typelevel" %% "cats-core" % "2.0.0-RC1"
libraryDependencies += "org.typelevel" %% "cats-effect" % "1.3.1"
libraryDependencies += "org.typelevel" %% "cats-effect-laws" % "1.3.1" % "test"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test
libraryDependencies += "com.h2database" % "h2" % "1.4.194"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

scalacOptions += "-feature"

scalacOptions += "-Ypartial-unification"

//initialCommands in console := "import scalaz._, Scalaz._"

routesGenerator := InjectedRoutesGenerator
