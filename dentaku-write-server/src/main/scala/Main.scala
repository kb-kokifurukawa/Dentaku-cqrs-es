package domain

import calc.v1 as pb
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.grpc.scaladsl.ServiceHandler
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.persistence.jdbc.query.scaladsl.JdbcReadJournal
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.stream.scaladsl.Source

import java.sql.DriverManager
import scala.concurrent.Future
import scala.io.Source as ScalaSource

// ==========================================
// gRPC service implementations
// ==========================================
final class CommandServiceImpl(calculator: ActorRef[Command])(using system: ActorSystem[?])
    extends pb.CommandService:

  override def pressDigit(in: pb.PressDigitRequest): Future[pb.PressDigitResponse] =
    calculator ! Command.PressDigit(EventMapper.digitFromProto(in.digit))
    Future.successful(pb.PressDigitResponse())

  override def pressOperator(in: pb.PressOperatorRequest): Future[pb.PressOperatorResponse] =
    calculator ! Command.PressOperator(in.operator)
    Future.successful(pb.PressOperatorResponse())

  override def pressEquals(in: pb.PressEqualsRequest): Future[pb.PressEqualsResponse] =
    calculator ! Command.PressEquals
    Future.successful(pb.PressEqualsResponse())

  override def pressClear(in: pb.PressClearRequest): Future[pb.PressClearResponse] =
    calculator ! Command.PressClear
    Future.successful(pb.PressClearResponse())

  override def pressUndo(in: pb.PressUndoRequest): Future[pb.PressUndoResponse] =
    calculator ! Command.PressUndo
    Future.successful(pb.PressUndoResponse())

final class EventStreamServiceImpl(readJournal: JdbcReadJournal)(using ActorSystem[?])
    extends pb.EventStreamService:

  override def subscribe(
      in: pb.EventStreamServiceSubscribeRequest
  ): Source[pb.EventStreamServiceSubscribeResponse, NotUsed] =
    readJournal
      .eventsByPersistenceId(in.persistenceId, in.fromSeqNr, Long.MaxValue)
      .map(env =>
        pb.EventStreamServiceSubscribeResponse(envelope = Some(EventMapper.toProtoEnvelope(env)))
      )

final class EventHistoryServiceImpl(readJournal: JdbcReadJournal)(using ActorSystem[?])
    extends pb.EventHistoryService:

  override def listEvents(in: pb.ListEventsRequest): Source[pb.ListEventsResponse, NotUsed] =
    readJournal
      .currentEventsByPersistenceId(in.persistenceId, 0L, Long.MaxValue)
      .map(env => pb.ListEventsResponse(envelope = Some(EventMapper.toProtoEnvelope(env))))

// ==========================================
// Entry point
// ==========================================
object Main:

  // Pekko Persistence JDBC は SQLite 向けスキーマを auto-create しないので、
  // 起動時に schema-sqlite.sql を適用する (CREATE TABLE IF NOT EXISTS なので冪等)
  private def initializeJournalSchema(): Unit =
    val source = ScalaSource.fromResource("schema-sqlite.sql")
    val sql = try source.mkString finally source.close()
    val conn = DriverManager.getConnection("jdbc:sqlite:write_side.db")
    try
      val stmt = conn.createStatement()
      sql.split(";").map(_.trim).filter(_.nonEmpty).foreach(stmt.execute)
    finally conn.close()

  def main(args: Array[String]): Unit =
    initializeJournalSchema()

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      given system: ActorSystem[Nothing] = context.system

      val calculator = context.spawn(Calculator("calc-1"), "CalculatorActor")
      val readJournal =
        PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

      val handler: HttpRequest => Future[HttpResponse] = ServiceHandler.concatOrNotFound(
        pb.CommandServiceHandler.partial(new CommandServiceImpl(calculator)),
        pb.EventStreamServiceHandler.partial(new EventStreamServiceImpl(readJournal)),
        pb.EventHistoryServiceHandler.partial(new EventHistoryServiceImpl(readJournal))
      )

      Http().newServerAt("0.0.0.0", 9000).bind(handler)

      context.log.info("🚀 Write gRPC Server is online at 0.0.0.0:9000")

      Behaviors.empty
    }

    ActorSystem[Nothing](rootBehavior, "DentakuWriteSystem")
