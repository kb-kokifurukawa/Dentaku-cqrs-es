package domain

import calc.v1 as pb

object EventMapper:

  def digitFromProto(p: pb.Digit): Digit = p match
    case pb.Digit.DIGIT_ZERO  => Digit.Zero
    case pb.Digit.DIGIT_ONE   => Digit.One
    case pb.Digit.DIGIT_TWO   => Digit.Two
    case pb.Digit.DIGIT_THREE => Digit.Three
    case pb.Digit.DIGIT_FOUR  => Digit.Four
    case pb.Digit.DIGIT_FIVE  => Digit.Five
    case pb.Digit.DIGIT_SIX   => Digit.Six
    case pb.Digit.DIGIT_SEVEN => Digit.Seven
    case pb.Digit.DIGIT_EIGHT => Digit.Eight
    case pb.Digit.DIGIT_NINE  => Digit.Nine
    case pb.Digit.DIGIT_DOT   => Digit.Dot
    case other =>
      throw new IllegalArgumentException(s"invalid digit: $other")

  def fromProto(pbEvent: pb.CalcEvent): CalcEvent =
    pbEvent.event match
      case pb.CalcEvent.Event.DigitEntered(de) =>
        CalcEvent.DigitEntered(digitFromProto(de.digit))
      case pb.CalcEvent.Event.OperatorSelected(os) =>
        CalcEvent.OperatorSelected(os.operator)
      case pb.CalcEvent.Event.Calculated(c) =>
        CalcEvent.Calculated(c.result)
      case pb.CalcEvent.Event.Cleared(_) =>
        CalcEvent.Cleared
      case pb.CalcEvent.Event.Undone(_) =>
        CalcEvent.Undone
      case pb.CalcEvent.Event.Empty =>
        throw new IllegalStateException("Empty CalcEvent oneof received")

  def toProtoState(s: WriteState): pb.CalculatorState =
    pb.CalculatorState(
      displayValue = s.displayValue,
      storedValue = s.storedValue,
      currentOp = s.currentOp,
      isNewInput = s.isNewInput
    )
