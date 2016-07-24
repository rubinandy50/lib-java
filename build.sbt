//
//  Author: Hari Sekhon
//  Date: 2016-06-06 22:51:45 +0100 (Mon, 06 Jun 2016)
//
//  vim:ts=4:sts=4:sw=4:et
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
name := "harisekhon-utils"

version := "1.16.0"

scalaVersion := "2.10.6"

libraryDependencies ++= Seq (
    "commons-cli" % "commons-cli" % "1.2",
    "commons-lang" % "commons-lang" % "2.6",
    "junit" % "junit" % "4.12",
    "log4j" % "log4j" % "1.2.17",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
    //"net.sf.jopt-simple" % "jopt-simple" % "4.9"
)
