import sourcecode.File

version := "1.0-SNAPSHOT"

resolvers += Resolver.sonatypeRepo("public")
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

libraryDependencies += "com.lihaoyi" %% "pprint" % "0.5.6"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test
libraryDependencies += "com.h2database" % "h2" % "1.4.194"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

lazy val scalaXml    = "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
lazy val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
lazy val dispatchV   = "0.12.0"
lazy val dispatch    = "net.databinder.dispatch" %% "dispatch-core" % dispatchV

scalacOptions += "-feature"
scalacOptions += "-Ypartial-unification"

//initialCommands in console := "import scalaz._, Scalaz._"

routesGenerator := InjectedRoutesGenerator

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(ScalaxbPlugin)
  .settings(
    name := """bahn-timetable""",
    libraryDependencies ++= Seq(dispatch),
    libraryDependencies ++= Seq(scalaXml, scalaParser))
  .settings(
    scalaxbDispatchVersion in (Compile, scalaxb) := dispatchV,
    scalaxbXsdSource := file(s"${project.base}/xsd"),
    scalaxbPackageName in (Compile, scalaxb)     := "generated"
//  logLevel in (Compile, scalaxb) := Level.Debug
  )
