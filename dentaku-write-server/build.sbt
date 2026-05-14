lazy val root = (project in file("."))
  .enablePlugins(PekkoGrpcPlugin)
  .settings(
    name := "dentaku-write-server",
    version := "0.1.0",
    scalaVersion := "3.3.1",

    fork := true,

    // 共有 proto ディレクトリを参照
    Compile / PB.protoSources := Seq(baseDirectory.value / ".." / "proto"),
    Compile / PB.includePaths += baseDirectory.value / ".." / "proto",

    // Write は Server 側スタブだけ必要
    pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Server),
    pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala),

    libraryDependencies ++= {
      val pekkoVersion = "1.1.2"
      val pekkoHttpVersion = "1.1.0"
      val pekkoPersistenceJdbcVersion = "1.1.0"

      Seq(
        "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
        "org.apache.pekko" %% "pekko-persistence-typed" % pekkoVersion,
        "org.apache.pekko" %% "pekko-persistence-jdbc" % pekkoPersistenceJdbcVersion,
        "org.apache.pekko" %% "pekko-persistence-query" % pekkoVersion,
        "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
        "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
        "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion,
        "org.apache.pekko" %% "pekko-discovery" % pekkoVersion,

        "org.xerial" % "sqlite-jdbc" % "3.45.1.0",
        "ch.qos.logback" % "logback-classic" % "1.5.3"
      )
    }
  )
