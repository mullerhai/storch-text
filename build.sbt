

import sbt.*
import Keys.*
import sbt.Def.settings
import scala.collection.immutable.Seq
ThisBuild / version := "0.1.0"
lazy val root = (project in file("."))
  .settings(
    name := "storch-text",
    javaCppVersion := (ThisBuild / javaCppVersion).value,
//    csrCacheDirectory := file("D:\\coursier"),
  )
transitiveClassifiers in Global := Seq("sources")
ThisBuild / tlBaseVersion := "0.0" // your current series x.y
//ThisBuild / CoursierCache := file("D:\\coursier")
ThisBuild / organization := "dev.storch"
ThisBuild / organizationName := "storch.dev"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("muller", "mullerhai")
)
ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "3.6.4"
ThisBuild / tlSonatypeUseLegacyHost := false


ThisBuild / tlSitePublishBranch := Some("main")

ThisBuild / apiURL := Some(new URL("https://storch.dev/api/"))
val scrImageVersion = "4.3.0" //4.0.34
val pytorchVersion =  "2.5.1"// "2.1.2" 2.5.1-1.5.11"
val cudaVersion = "12.6-9.5"  //"12.4.99" // "12.3-8.9"
val openblasVersion = "0.3.28"// "0.3.26"
val mklVersion = "2025.0"//"2024.0"
ThisBuild / scalaVersion := "3.6.2"
ThisBuild / javaCppVersion := "1.5.11"//"1.5.10"
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))
ThisBuild / githubWorkflowOSes := Seq("macos-latest", "ubuntu-latest", "windows-latest")

val enableGPU = settingKey[Boolean]("enable or disable GPU support")
val hasMKL = {
  val firstPlatform = org.bytedeco.sbt.javacpp.Platform.current.head
  firstPlatform == "linux-x86_64" || firstPlatform == "windows-x86_64"
}


// https://mvnrepository.com/artifact/org.apache.tika/tika-langdetect
//libraryDependencies += "org.apache.tika" % "tika-langdetect" % "3.1.0" pomOnly()
// https://mvnrepository.com/artifact/org.apache.tika/tika-serialization
// https://mvnrepository.com/artifact/org.apache.tika/tika
//libraryDependencies += "org.apache.tika" % "tika" % "3.1.0" pomOnly()
// https://mvnrepository.com/artifact/org.apache.tika/tika-parser-zip-commons

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-math3" % "3.6.1"

)
resolvers += "ctn" at "https://repo1.maven.org/maven2/"
// https://mvnrepository.com/artifact/org.apache.tika/tika-core
libraryDependencies += "org.apache.tika" % "tika-core" % "3.1.0"
// https://mvnrepository.com/artifact/org.apache.tika/tika-parser-pdf-module
libraryDependencies += "org.apache.tika" % "tika-parser-pdf-module" % "3.1.0"

libraryDependencies += "org.apache.tika" % "tika-parser-zip-commons" % "3.1.0"
// https://mvnrepository.com/artifact/org.apache.tika/tika-parser-image-module
libraryDependencies += "org.apache.tika" % "tika-parser-image-module" % "3.1.0"

libraryDependencies += "org.apache.tika" % "tika-serialization" % "3.1.0"
// https://mvnrepository.com/artifact/org.apache.tika/tika-parser-text-module
libraryDependencies += "org.apache.tika" % "tika-parser-text-module" % "3.1.0"
// https://mvnrepository.com/artifact/org.apache.tika/tika-parser-microsoft-module
libraryDependencies += "org.apache.tika" % "tika-parser-microsoft-module" % "3.1.0"
// https://mvnrepository.com/artifact/org.apache.tika/tika-parser-html-module
libraryDependencies += "org.apache.tika" % "tika-parser-html-module" % "3.1.0"
// https://mvnrepository.com/artifact/org.apache.tika/tika-parser-miscoffice-module
libraryDependencies += "org.apache.tika" % "tika-parser-miscoffice-module" % "3.1.0"
// https://mvnrepository.com/artifact/io.brunk.tokenizers/tokenizers
libraryDependencies += "io.brunk.tokenizers" %% "tokenizers" % "0.0.2"
// https://mvnrepository.com/artifact/io.github.56duong/huggingface-nlp
libraryDependencies += "io.github.56duong" % "huggingface-nlp" % "1.0.1"
// https://mvnrepository.com/artifact/org.clulab/scala-transformers-encoder
libraryDependencies += "org.clulab" %% "scala-transformers-encoder" % "0.7.0"
// https://mvnrepository.com/artifact/org.bytedeco/sentencepiece
libraryDependencies += "org.bytedeco" % "sentencepiece" % "0.2.0-1.5.11"
// https://mvnrepository.com/artifact/org.bytedeco/sentencepiece-platform
libraryDependencies += "org.bytedeco" % "sentencepiece-platform" % "0.2.0-1.5.11" //classifier
libraryDependencies +=   "dev.storch" % "core_3" % "0.2.1-1.15.1"
libraryDependencies +=   "dev.storch" % "vision_3" % "0.2.1-1.15.1"
libraryDependencies +=  "org.scalameta" %% "munit" % "0.7.29" //% Test
libraryDependencies +=  "org.scalameta" %% "munit-scalacheck" % "0.7.29" // % Test
ThisBuild  / assemblyMergeStrategy := {
  case v if v.contains("module-info.class")   => MergeStrategy.discard
  case v if v.contains("UnusedStub")          => MergeStrategy.first
  case v if v.contains("aopalliance")         => MergeStrategy.first
  case v if v.contains("inject")              => MergeStrategy.first
  case v if v.contains("jline")               => MergeStrategy.discard
  case v if v.contains("scala-asm")           => MergeStrategy.discard
  case v if v.contains("asm")                 => MergeStrategy.discard
  case v if v.contains("scala-compiler")      => MergeStrategy.deduplicate
  case v if v.contains("reflect-config.json") => MergeStrategy.discard
  case v if v.contains("jni-config.json")     => MergeStrategy.discard
  case v if v.contains("git.properties")      => MergeStrategy.discard
  case v if v.contains("reflect.properties")      => MergeStrategy.discard
  case v if v.contains("compiler.properties")      => MergeStrategy.discard
  case v if v.contains("scala-collection-compat.properties")      => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}