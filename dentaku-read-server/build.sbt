lazy val root = (project in file("."))
  .settings(
    name := "dentaku-read-server",
    version := "0.1.0",
    scalaVersion := "3.3.1",
    fork := true, // SQLiteのネイティブライブラリ読み込みエラー回避のおまじない

    libraryDependencies ++= {
      val pekkoVersion = "1.1.2"
      val pekkoHttpVersion = "1.1.0"
      val pekkoPersistenceJdbcVersion = "1.1.0"

      Seq(
        "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
        "org.apache.pekko" %% "pekko-persistence-typed" % pekkoVersion,
        "org.apache.pekko" %% "pekko-persistence-jdbc" % pekkoPersistenceJdbcVersion,
        
        // ★NEW: DBからイベントをストリームとして読み出すためのモジュール
        "org.apache.pekko" %% "pekko-persistence-query" % pekkoVersion,
        
        "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion,
        "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
        "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpVersion,
        "org.xerial" % "sqlite-jdbc" % "3.45.1.0",
        "ch.qos.logback" % "logback-classic" % "1.5.3"
      )
    }
  )