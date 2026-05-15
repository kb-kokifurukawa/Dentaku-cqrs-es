package domain

// NOTE: dentaku-write-server の Calculator.scala の handleEvent と必ず同期させること。
// 学習用プロジェクトのため意図的に重複させている（共有モジュール化は将来課題）。

sealed trait CalcEvent
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
):
  def snapshot: WriteState = copy(history = Nil)

object Projection:

  private def computeOp(left: Double, op: String, right: Double): Double = op match
    case "+" => left + right
    case "-" => left - right
    case "*" => left * right
    case "/" => if right != 0 then left / right else 0.0
    case _   => 0.0

  def handleEvent(state: WriteState, event: CalcEvent): WriteState = event match
    case CalcEvent.DigitEntered(d) =>
      val newDisplay = if state.isNewInput then d else state.displayValue + d
      state.copy(
        displayValue = newDisplay,
        isNewInput = false,
        history = state.snapshot :: state.history
      )

    case CalcEvent.OperatorSelected(op) =>
      // 保留中の演算 (stored + op) があり、続けて数字が入力されている場合は
      // 新しい op をセットする前にチェイン評価する。Calculator.scala と同期。
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
