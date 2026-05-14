package domain

import calc.v1 as pb
import com.google.protobuf.timestamp.Timestamp
import org.apache.pekko.persistence.query.EventEnvelope as PekkoEnvelope

object EventMapper:

  def toProto(e: CalcEvent): pb.CalcEvent =
    val inner: pb.CalcEvent.Event = e match
      case CalcEvent.DigitEntered(d) =>
        pb.CalcEvent.Event.DigitEntered(pb.DigitEntered(digit = d))
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
