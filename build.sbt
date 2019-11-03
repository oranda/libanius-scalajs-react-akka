import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

import sbt.Keys._

import sbtcrossproject.CrossPlugin.autoImport.crossProject

organization := "com.github.oranda"
name := "libanius-scalajs-react-akka"

scalaJSStage in Global := FastOptStage

skip in packageJSDependencies := false

scalaJSUseMainModuleInitializer := true

scalacOptions += "-Ylog-classpath"

val app = crossProject(JSPlatform, JVMPlatform).settings(
  scalaVersion := "2.12.6",

  unmanagedSourceDirectories in Compile += baseDirectory.value  / "shared" / "main" / "scala",

  unmanagedBase := (baseDirectory(_ / "../shared/lib")).value,

  libraryDependencies ++= Seq(
    "com.github.oranda" %% "libanius-akka" % "0.4",
    "com.lihaoyi" %%% "scalatags" % "0.6.7",
    "com.lihaoyi" %%% "utest" % "0.6.3",
    "com.lihaoyi" %%% "upickle" % "0.6.6"
  ),
  testFrameworks += new TestFramework("utest.runner.Framework")

).jsSettings(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.7",
    "com.github.japgolly.scalajs-react" %%% "core" % "1.2.1",
    "com.github.japgolly.scalajs-react" %%% "extra" % "1.2.1",
    "com.lihaoyi" %%% "scalarx" % "0.4.0"
  ),
  // React itself (react-with-addons.js can be react.js, react.min.js, react-with-addons.min.js)
  jsDependencies ++= Seq(
    "org.webjars.bower" % "react" % "15.3.2" / "react-with-addons.js" minified "react-with-addons.min.js" commonJSName "React",
    "org.webjars.bower" % "react" % "15.3.2" / "react-dom.js" minified "react-dom.min.js" dependsOn "react-with-addons.js" commonJSName "ReactDOM",
    "org.webjars.bower" % "react" % "15.3.2" / "react-dom-server.js" minified  "react-dom-server.min.js" dependsOn "react-dom.js" commonJSName "ReactDOMServer"
  ),
  skip in packageJSDependencies := false // creates app-jsdeps.js with the react JS lib inside
).jvmSettings(
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.5.25",
    "com.typesafe.akka" %% "akka-http"   % "10.1.8",
    "com.typesafe.akka" %% "akka-stream" % "2.5.25",
    "com.typesafe.akka" %% "akka-persistence" % "2.5.25",
    "com.typesafe.akka" %% "akka-cluster-sharding" % "2.5.25",
    "com.softwaremill.akka-http-session" %% "core" % "0.5.10",
    "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    "com.typesafe.akka" %% "akka-testkit" % "2.5.25" % Test
  )
)

lazy val appJS = app.js.settings(
  // nothing special here yet
)

lazy val appJVM = app.jvm.settings(

  version := "0.6",

  // JS files like app-fastopt.js and app-jsdeps.js need to be copied to the server
  (resources in Compile) += (fastOptJS in (appJS, Compile)).value.data,
  (resources in Compile) += (packageJSDependencies in (appJS, Compile)).value,

  // copy resources like quiz.css to the server
  resourceDirectory in Compile := baseDirectory(_ / "../shared/src/main/resources").value,

  // application.conf too must be in the classpath
  unmanagedResourceDirectories in Compile += baseDirectory(_ / "../jvm/src/main/resources").value,

  // Use a different configuration for tests
  javaOptions in Test += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application-test.conf",

  // We need to fork a JVM process when testing so the Java options above are applied
  fork in Test := true

).enablePlugins(JavaAppPackaging)