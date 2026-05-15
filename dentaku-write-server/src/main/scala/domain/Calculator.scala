package domain

import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

trait CborSerializable

sealed trait Command extends CborSerializable
object Command:
  case class PressDigit(digit: String) extends Command
  case class PressOperator(operator: String) extends Command
  case object PressEquals extends Command
  case object PressClear extends Command
  case object PressUndo extends Command

sealed trait CalcEvent extends CborSerializable
object CalcEvent:
  case class DigitEntered(digit: String) extends CalcEvent
  case class OperatorSelected(operator: String) extends CalcEvent
  case class Calculated(result: String) extends CalcEvent
  case object Cleared extends CalcEvent
  case object Undone extends CalcEvent

case class WriteState(
  displayValue: String = "0",
  storedValue: Option[Double] = None,
  currentOp: Option[String] = None,
  isNewInput: Boolean = true,
  history: List[WriteState] = Nil
) extends CborSerializable:
  // history を持たない自分のコピー（履歴スタックに積む用）
  def snapshot: WriteState = copy(history = Nil)

object Calculator:

  def apply(id: String): EventSourcedBehavior[Command, CalcEvent, WriteState] =
    EventSourcedBehavior[Command, CalcEvent, WriteState](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = WriteState(),
      commandHandler = (state, command) => handleCommand(state, command),
      eventHandler = (state, event) => handleEvent(state, event)
    )

  private def computeOp(left: Double, op: String, right: Double): Double = op match
    case "+" => left + right
    case "-" => left - right
    case "*" => left * right
    case "/" => if right != 0 then left / right else 0.0
    case _   => 0.0

  private def handleCommand(state: WriteState, command: Command): Effect[CalcEvent, WriteState] =
    command match
      case Command.PressDigit(d) =>
        Effect.persist(CalcEvent.DigitEntered(d))

      case Command.PressOperator(op) =>
        Effect.persist(CalcEvent.OperatorSelected(op))

      case Command.PressEquals =>
        (state.storedValue, state.currentOp) match
          case (Some(stored), Some(op)) =>
            val current = state.displayValue.toDoubleOption.getOrElse(0.0)
            Effect.persist(CalcEvent.Calculated(computeOp(stored, op, current).toString))
          case _ =>
            Effect.none

      case Command.PressClear =>
        Effect.persist(CalcEvent.Cleared)

      case Command.PressUndo =>
        if state.history.isEmpty then Effect.none
        else Effect.persist(CalcEvent.Undone)

  private def handleEvent(state: WriteState, event: CalcEvent): WriteState =
    event match
      case CalcEvent.DigitEntered(d) =>
        val newDisplay = if state.isNewInput then d else state.displayValue + d
        state.copy(
          displayValue = newDisplay,
          isNewInput = false,
          history = state.snapshot :: state.history
        )

      case CalcEvent.OperatorSelected(op) =>
        // 保留中の演算 (stored + op) があり、続けて数字が入力されている場合は
        // 新しい op をセットする前にチェイン評価する。これが古い電卓の標準挙動。
        val (newDisplay, newStored) =
          (state.storedValue, state.currentOp, state.isNewInput) match
            case (Some(stored), Some(prevOp), false) =>
              val current = state.displayValue.toDoubleOption.getOrElse(0.0)
              val r = computeOp(stored, prevOp, current)
              (r.toString, Some(r))
            case _ =>
              (state.displayValue, state.displayValue.toDoubleOption)

        state.copy(
          displayValue = newDisplay,
          storedValue = newStored,
          currentOp = Some(op),
          isNewInput = true,
          history = state.snapshot :: state.history
        )

      case CalcEvent.Calculated(res) =>
        state.copy(
          displayValue = res,
          storedValue = None,
          currentOp = None,
          isNewInput = true,
          history = state.snapshot :: state.history
        )

      case CalcEvent.Cleared =>
        WriteState(history = state.snapshot :: state.history)

      case CalcEvent.Undone =>
        state.history match
          case prev :: rest => prev.copy(history = rest)
          case Nil          => state
