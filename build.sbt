name := "PdfSplit"

version := "0.1"

scalaVersion := "2.12.4"

// https://mvnrepository.com/artifact/org.apache.pdfbox/pdfbox
libraryDependencies += "org.apache.pdfbox" % "pdfbox" % "2.0.19"

// https://mvnrepository.com/artifact/org.apache.pdfbox/pdfbox-tools
libraryDependencies += "org.apache.pdfbox" % "pdfbox-tools" % "2.0.19"

// https://mvnrepository.com/artifact/org.scala-lang.modules/scala-swing
libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "2.1.1"

mainClass in assembly := Some("MainApp")