package domain

trait CborSerializable

sealed trait CalcEvent extends CborSerializable
object CalcEvent:
  case class DigitEntered(digit: String) extends CalcEvent
  case class OperatorSelected(operator: String) extends CalcEvent
  case class Calculated(result: String) extends CalcEvent
  case object Cleared extends CalcEvent