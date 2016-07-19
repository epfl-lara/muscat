name := "scala-debug-concurrent"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "junit" % "junit" % "4.8.1" % "test"
)

javaOptions in Test ++= Seq("-Xmx4G", "-Dlog4j.configuration=log4j.properties")

fork in Test := true
