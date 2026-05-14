package domain

import calc.v1 as pb

object EventMapper:

  def fromProto(pbEvent: pb.CalcEvent): CalcEvent =
    pbEvent.event match
      case pb.CalcEvent.Event.DigitEntered(de) =>
        CalcEvent.DigitEntered(de.digit)
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
