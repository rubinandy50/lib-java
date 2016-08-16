//
//  Author: Hari Sekhon
//  Date: 2016-06-06 22:51:45 +0100 (Mon, 06 Jun 2016)
//
//  vim:ts=4:sts=4:sw=4:et:filetype=java
//
//  https://github.com/harisekhon/lib-java
//
//  License: see accompanying Hari Sekhon LICENSE file
//
//  If you're using my code you're welcome to connect with me on LinkedIn and optionally send me feedback to help improve or steer this or other code I publish
//
//  http://www.linkedin.com/in/harisekhon
//

// TODO: metadata from pom, description, URLs, different name to jar artifact id

// This comes out with spaces and capitalization with sbt assembly
organization := "com.linkedin.harisekhon"
name := "harisekhon-utils"

version := "1.17.0"

scalaVersion := "2.10.6"

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

// add assembly artifact to publish
artifact in (Compile, assembly) := {
    val art = (artifact in (Compile, assembly)).value
    art.copy(`classifier` = Some("assembly"))
}
addArtifact(artifact in (Compile, assembly), assembly)

enablePlugins(VersionEyePlugin)

existingProjectId in versioneye := "57616cdb0a82b20053182c74"
baseUrl in versioneye := "https://www.versioneye.com"
apiPath in versioneye := "/api/v2"
publishCrossVersion in versioneye := true

libraryDependencies ++= Seq (
    "commons-cli" % "commons-cli" % "1.3.1",
    "commons-lang" % "commons-lang" % "2.6",
    "jline" % "jline" % "2.14.2",
    "junit" % "junit" % "4.12",
    "log4j" % "log4j" % "1.2.17",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
    //"net.sf.jopt-simple" % "jopt-simple" % "4.9"
)
