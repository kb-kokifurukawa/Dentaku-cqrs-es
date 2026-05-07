package domain

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.persistence.jdbc.query.scaladsl.JdbcReadJournal
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import java.sql.DriverManager

// BFF に返す JSON の型
case class StateResponse(displayValue: String)
object StateResponse {
  // implicit val format = jsonFormat1(StateResponse.apply)
  implicit val format: RootJsonFormat[StateResponse] = jsonFormat1(StateResponse.apply)
}

object Main {
  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
      implicit val system = context.system

      // ==========================================
      // 1. Read DB (SQLite) 操作用のヘルパー関数
      // ==========================================
      val dbUrl = context.system.settings.config.getString("read-db.url")

      def updateDisplayValue(newValue: String): Unit = {
        val conn = DriverManager.getConnection(dbUrl)
        try {
          val stmt = conn.prepareStatement("UPDATE calculator_view SET display_value = ? WHERE id = 1")
          stmt.setString(1, newValue)
          stmt.executeUpdate()
        } finally {
          conn.close()
        }
      }

      def getDisplayValue(): String = {
        val conn = DriverManager.getConnection(dbUrl)
        try {
          val stmt = conn.prepareStatement("SELECT display_value FROM calculator_view WHERE id = 1")
          val rs = stmt.executeQuery()
          if (rs.next()) rs.getString("display_value") else "0"
        } finally {
          conn.close()
        }
      }

      // ==========================================
      // 2. プロジェクション (Write DB -> Read DB の同期)
      // ==========================================
      // Write DB の変更を監視する "ReadJournal" を起動
      val readJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
      
      // "calc-1" のイベントを最初 (0L) から無限ストリームとして購読
      readJournal
        .eventsByPersistenceId("calc-1", 0L, Long.MaxValue)
        .runWith(Sink.foreach { envelope =>
          envelope.event match {
            case CalcEvent.Calculated(res) =>
              println(s"📥 [Read] Calculated($res) を検知！ Read DB を更新します。")
              updateDisplayValue(res)
              
            case CalcEvent.DigitEntered(d) =>
              // 本当は「新規入力か？」などの判定が必要ですが、今回は分かりやすく上書きで表現します
              println(s"📥 [Read] DigitEntered($d) を検知！ Read DB を更新します。")
              val current = getDisplayValue()
              if (current == "0" || current.contains(".")) updateDisplayValue(d)
              else updateDisplayValue(current + d)
              
            case CalcEvent.Cleared =>
              println(s"📥 [Read] Cleared を検知！ Read DB をリセットします。")
              updateDisplayValue("0")
              
            case _ => // OperatorSelected などは画面の数字は変わらないので Read DB は更新しない
          }
        })

      // ==========================================
      // 3. HTTP サーバーの起動 (BFFへの提供用 API)
      // ==========================================
      val route = path("state") {
        get {
          // BFF からリクエストが来たら、Read DB の値をサクッと返すだけ！ (計算は一切しない)
          complete(StateResponse(getDisplayValue()))
        }
      }
      
      Http().newServerAt("localhost", 9001).bind(route)
      context.log.info("👀 Read Server is online at http://localhost:9001/")

      Behaviors.empty
    }, "DentakuReadSystem")
  }
}