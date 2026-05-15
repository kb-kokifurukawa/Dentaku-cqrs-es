package domain

import calc.v1 as pb
import com.google.protobuf.timestamp.Timestamp
import org.apache.pekko.persistence.query.EventEnvelope as PekkoEnvelope

object EventMapper:

  def digitToProto(d: Digit): pb.Digit = d match
    case Digit.Zero  => pb.Digit.DIGIT_ZERO
    case Digit.One   => pb.Digit.DIGIT_ONE
    case Digit.Two   => pb.Digit.DIGIT_TWO
    case Digit.Three => pb.Digit.DIGIT_THREE
    case Digit.Four  => pb.Digit.DIGIT_FOUR
    case Digit.Five  => pb.Digit.DIGIT_FIVE
    case Digit.Six   => pb.Digit.DIGIT_SIX
    case Digit.Seven => pb.Digit.DIGIT_SEVEN
    case Digit.Eight => pb.Digit.DIGIT_EIGHT
    case Digit.Nine  => pb.Digit.DIGIT_NINE
    case Digit.Dot   => pb.Digit.DIGIT_DOT

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

  def toProto(e: CalcEvent): pb.CalcEvent =
    val inner: pb.CalcEvent.Event = e match
      case CalcEvent.DigitEntered(d) =>
        pb.CalcEvent.Event.DigitEntered(pb.DigitEntered(digit = digitToProto(d)))
      case CalcEvent.OperatorSelected(o) =>
        pb.CalcEvent.Event.OperatorSelected(pb.OperatorSelected(operator = o))
      case CalcEvent.Calculated(r) =>
        pb.CalcEvent.Event.Calculated(pb.Calculated(result = r))
      case CalcEvent.Cleared =>
        pb.CalcEvent.Event.Cleared(pb.Cleared())
      case CalcEvent.Undone =>
        pb.CalcEvent.Event.Undone(pb.Undone())
    pb.CalcEvent(event = inner)

  def toProtoEnvelope(env: PekkoEnvelope): pb.EventEnvelope =
    val event = env.event.asInstanceOf[CalcEvent]
    val tsMillis = env.timestamp
    val timestamp = Timestamp(
      seconds = tsMillis / 1000,
      nanos = ((tsMillis % 1000) * 1000000).toInt
    )
    pb.EventEnvelope(
      persistenceId = env.persistenceId,
      seqNr = env.sequenceNr,
      timestamp = Some(timestamp),
      event = Some(toProto(event))
    )
