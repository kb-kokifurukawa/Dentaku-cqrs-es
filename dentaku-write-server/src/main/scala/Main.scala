package domain

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.util.{Failure, Success}

// ==========================================
// 1. JSONリクエストの型定義とパーサー
// ==========================================
case class DigitReq(digit: String)
case class OperatorReq(operator: String)

object JsonFormats {
  // Scala 3 で Spray JSON を安全に使うためのおまじない (.apply を渡す)
  implicit val digitFormat: RootJsonFormat[DigitReq] = jsonFormat1(DigitReq.apply)
  implicit val operatorFormat: RootJsonFormat[OperatorReq] = jsonFormat1(OperatorReq.apply)
}

// ==========================================
// 2. エントリーポイント
// ==========================================
object Main {
  import JsonFormats._

  def main(args: Array[String]): Unit = {
    // ルートアクター（システム全体を管理する親分）を定義
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      
      // 1. 電卓アクターを1つ生成（IDを "calc-1" とする）
      val calculator = context.spawn(Calculator("calc-1"), "CalculatorActor")

      // 2. HTTPのルーティング（APIエンドポイント）を定義
      val route =
        pathPrefix("command") {
          concat(
            // POST http://localhost:9000/command/digit
            path("digit") {
              post {
                entity(as[DigitReq]) { req =>
                  // アクターにメッセージを「投げ捨てる」（Fire and Forget: `!` メソッド）
                  calculator ! Command.PressDigit(req.digit)
                  complete("Accepted")
                }
              }
            },
            // POST http://localhost:9000/command/operator
            path("operator") {
              post {
                entity(as[OperatorReq]) { req =>
                  calculator ! Command.PressOperator(req.operator)
                  complete("Accepted")
                }
              }
            },
            // POST http://localhost:9000/command/equals
            path("equals") {
              post {
                calculator ! Command.PressEquals
                complete("Accepted")
              }
            },
            // POST http://localhost:9000/command/clear
            path("clear") {
              post {
                calculator ! Command.PressClear
                complete("Accepted")
              }
            }
          )
        }

      // 3. HTTPサーバーの起動（ポート9000を使用）
      implicit val system = context.system
      val bindingFuture = Http().newServerAt("localhost", 9000).bind(route)
      
      context.log.info("🚀 Write Server is online at http://localhost:9000/")

      Behaviors.empty
    }

    // ActorSystemを起動！
    ActorSystem[Nothing](rootBehavior, "DentakuWriteSystem")
  }
}