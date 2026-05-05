package domain

import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

// ==========================================
// 1. マーカー・トレイト
// (application.conf で "このトレイトを持つクラスはJSON/CBORに変換してDBに保存する" と設定するための目印)
// ==========================================
trait CborSerializable

// ==========================================
// 2. Commands (BFFから受け取る命令)
// ==========================================
enum Command extends CborSerializable:
  case PressDigit(digit: String)
  case PressOperator(operator: String)
  case PressEquals
  case PressClear

// ==========================================
// 3. Events (DBに保存される過去の事実)
// ==========================================
enum CalcEvent extends CborSerializable:
  case DigitEntered(digit: String)
  case OperatorSelected(operator: String)
  case Calculated(result: String)
  case Cleared

// ==========================================
// 4. State (アクターがメモリ上に持つ現在の状態)
// ==========================================
case class WriteState(
  displayValue: String = "0",
  storedValue: Option[Double] = None,
  currentOp: Option[String] = None,
  isNewInput: Boolean = true
) extends CborSerializable

// ==========================================
// 5. アクターの実体 (EventSourcedBehavior)
// ==========================================
object Calculator:

  // アクターの生成関数
  def apply(id: String): EventSourcedBehavior[Command, CalcEvent, WriteState] =
    EventSourcedBehavior[Command, CalcEvent, WriteState](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = WriteState(), // 起動時の初期状態
      commandHandler = (state, command) => handleCommand(state, command),
      eventHandler = (state, event) => handleEvent(state, event)
    )

  // ----------------------------------------------------
  // 【Write側の真髄】 コマンドハンドラー (Command -> Effect[Event])
  // 状態を見て「計算」や「バリデーション」を行い、発生させるイベントを決定する
  // ----------------------------------------------------
  private def handleCommand(state: WriteState, command: Command): Effect[CalcEvent, WriteState] =
    command match
      case Command.PressDigit(d) =>
        Effect.persist(CalcEvent.DigitEntered(d))

      case Command.PressOperator(op) =>
        Effect.persist(CalcEvent.OperatorSelected(op))

      case Command.PressEquals =>
        // ★ここで計算ロジックを実行！
        (state.storedValue, state.currentOp) match
          case (Some(stored), Some(op)) =>
            val current = state.displayValue.toDoubleOption.getOrElse(0.0)
            val result = op match
              case "+" => stored + current
              case "-" => stored - current
              case "*" => stored * current
              case "/" => if current != 0 then stored / current else 0.0
              case _   => 0.0
            
            // 計算結果を "事実" としてDBに保存（persist）する
            Effect.persist(CalcEvent.Calculated(result.toString))
            
          case _ =>
            // 演算子がないのに `=` が押された場合は何もせず無視する
            Effect.none

      case Command.PressClear =>
        Effect.persist(CalcEvent.Cleared)

  // ----------------------------------------------------
  // イベントハンドラー (State + Event -> NewState)
  // DBからイベントを復元した時や、persist成功後に、メモリ上の状態を更新する
  // ※ここには絶対に「計算」や「外部API通信」などの副作用を書いてはいけない！
  // ----------------------------------------------------
  private def handleEvent(state: WriteState, event: CalcEvent): WriteState =
    event match
      case CalcEvent.DigitEntered(d) =>
        if state.isNewInput then state.copy(displayValue = d, isNewInput = false)
        else state.copy(displayValue = state.displayValue + d)

      case CalcEvent.OperatorSelected(op) =>
        state.copy(
          storedValue = state.displayValue.toDoubleOption,
          currentOp = Some(op),
          isNewInput = true
        )

      case CalcEvent.Calculated(res) =>
        state.copy(
          displayValue = res,
          storedValue = None,
          currentOp = None,
          isNewInput = true
        )

      case CalcEvent.Cleared =>
        WriteState() // 初期状態のインスタンスを返す