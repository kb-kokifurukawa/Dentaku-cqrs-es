lazy val root = (project in file("."))
  .enablePlugins(PekkoGrpcPlugin)
  .settings(
    name := "dentaku-read-server",
    version := "0.1.0",
    scalaVersion := "3.3.1",
    fork := true,

    // 共有 proto ディレクトリを参照
    Compile / PB.protoSources := Seq(baseDirectory.value / ".." / "proto"),
    Compile / PB.includePaths += baseDirectory.value / ".." / "proto",

    // Server (StateQuery / StateStream) と Client (EventStream) の両方を生成
    pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Server, PekkoGrpc.Client),
    pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala),

    libraryDependencies ++= {
      val pekkoVersion = "1.1.2"
      val pekkoHttpVersion = "1.1.0"

      Seq(
        "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
        "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
        "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
        "org.apache.pekko" %% "pekko-discovery" % pekkoVersion,

        "org.xerial" % "sqlite-jdbc" % "3.45.1.0",
        "ch.qos.logback" % "logback-classic" % "1.5.3"
      )
    }
  )
