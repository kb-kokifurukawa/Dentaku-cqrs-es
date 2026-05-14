package domain

import calc.v1 as pb
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.grpc.GrpcClientSettings
import org.apache.pekko.grpc.scaladsl.ServiceHandler
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}

import java.sql.{DriverManager, Types}
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import scala.concurrent.Future
import scala.util.{Failure, Success}

// ==========================================
// Read DB ヘルパー
// ==========================================
object ReadDb:
  def initialize(dbUrl: String): Unit =
    val conn = DriverManager.getConnection(dbUrl)
    try
      val stmt = conn.createStatement()
      stmt.execute("DROP TABLE IF EXISTS calculator_view")
      stmt.execute(
        """CREATE TABLE calculator_view (
          |  id INTEGER PRIMARY KEY,
          |  display_value TEXT NOT NULL,
          |  stored_value REAL,
          |  current_op TEXT,
          |  is_new_input INTEGER NOT NULL DEFAULT 1
          |)""".stripMargin
      )
      stmt.execute("INSERT INTO calculator_view (id, display_value, is_new_input) VALUES (1, '0', 1)")
    finally conn.close()

  def update(dbUrl: String, state: WriteState): Unit =
    val conn = DriverManager.getConnection(dbUrl)
    try
      val stmt = conn.prepareStatement(
        "UPDATE calculator_view SET display_value=?, stored_value=?, current_op=?, is_new_input=? WHERE id=1"
      )
      stmt.setString(1, state.displayValue)
      state.storedValue match
        case Some(v) => stmt.setDouble(2, v)
        case None    => stmt.setNull(2, Types.REAL)
      state.currentOp match
        case Some(o) => stmt.setString(3, o)
        case None    => stmt.setNull(3, Types.VARCHAR)
      stmt.setInt(4, if state.isNewInput then 1 else 0)
      stmt.executeUpdate()
    finally conn.close()

  def read(dbUrl: String): pb.CalculatorState =
    val conn = DriverManager.getConnection(dbUrl)
    try
      val stmt = conn.prepareStatement(
        "SELECT display_value, stored_value, current_op, is_new_input FROM calculator_view WHERE id=1"
      )
      val rs = stmt.executeQuery()
      if rs.next() then
        val sv = rs.getObject("stored_value")
        val co = rs.getString("current_op")
        pb.CalculatorState(
          displayValue = rs.getString("display_value"),
          storedValue = if sv == null then None else Some(sv.asInstanceOf[Number].doubleValue),
          currentOp = Option(co),
          isNewInput = rs.getInt("is_new_input") == 1
        )
      else
        pb.CalculatorState(displayValue = "0", isNewInput = true)
    finally conn.close()

// ==========================================
// gRPC service impls
// ==========================================
final class StateQueryServiceImpl(dbUrl: String)(using ActorSystem[?])
    extends pb.StateQueryService:

  override def getState(in: pb.GetStateRequest): Future[pb.GetStateResponse] =
    Future.successful(pb.GetStateResponse(state = Some(ReadDb.read(dbUrl))))

final class StateStreamServiceImpl(
    broadcast: Source[pb.CalculatorState, NotUsed],
    dbUrl: String
)(using ActorSystem[?]) extends pb.StateStreamService:

  override def subscribe(
      in: pb.StateStreamServiceSubscribeRequest
  ): Source[pb.StateStreamServiceSubscribeResponse, NotUsed] =
    // 接続直後に現在状態を1発送ってから、以降の更新を流す
    val current = Source.single(ReadDb.read(dbUrl))
    current
      .concat(broadcast)
      .map(s => pb.StateStreamServiceSubscribeResponse(state = Some(s)))

// ==========================================
// Entry point
// ==========================================
object Main:
  def main(args: Array[String]): Unit =
    val behavior = Behaviors.setup[Nothing] { context =>
      given system: ActorSystem[Nothing] = context.system
      import system.executionContext

      val config = system.settings.config
      val dbUrl = config.getString("read-db.url")
      val writeHost = config.getString("write-server.host")
      val writePort = config.getInt("write-server.port")
      val persistenceId = config.getString("projection.persistence-id")

      // 1. Read DB を毎起動でリセット（冪等性は「常に初期状態から再生」で担保）
      ReadDb.initialize(dbUrl)
      context.log.info(s"🧹 Read DB initialized at $dbUrl")

      // 2. Broadcast Hub: プロジェクションが publish、StateStream 購読者が consume
      val (stateQueue, stateBroadcast) =
        Source
          .queue[pb.CalculatorState](256, OverflowStrategy.dropHead)
          .toMat(BroadcastHub.sink[pb.CalculatorState](bufferSize = 16))(Keep.both)
          .run()
      // BroadcastHub は最低1つの subscriber が必要なので idle sink を繋いでおく
      stateBroadcast.runWith(Sink.ignore)

      // 3. プロジェクション用 in-memory state
      val stateRef = new AtomicReference[WriteState](WriteState())

      // 4. Write Server への gRPC クライアント
      val writeClient = pb.EventStreamServiceClient(
        GrpcClientSettings.connectToServiceAt(writeHost, writePort).withTls(false)
      )

      // 5. イベント購読 → projection → Read DB + broadcast
      // 注: Stream のステージ内では ActorContext.log が使えない（別スレッドのため）。
      // SLF4J Logger を直接掴んで使う。
      val projectionLog = LoggerFactory.getLogger("ReadProjection")

      val subscription = writeClient
        .subscribe(
          pb.EventStreamServiceSubscribeRequest(
            persistenceId = persistenceId,
            fromSeqNr = 0L
          )
        )
        .runWith(Sink.foreach { resp =>
          val envelope = resp.envelope.get
          val event = EventMapper.fromProto(envelope.event.get)
          val newState = Projection.handleEvent(stateRef.get(), event)
          stateRef.set(newState)
          ReadDb.update(dbUrl, newState)
          stateQueue.offer(EventMapper.toProtoState(newState))
          projectionLog.info(
            s"📥 seq=${envelope.seqNr} event=$event → display=${newState.displayValue}"
          )
        })

      subscription.onComplete {
        case Success(_) => projectionLog.info("Event subscription completed")
        case Failure(e) => projectionLog.error("Event subscription failed", e)
      }

      // 6. gRPC サーバ起動（BFF が叩いてくる）
      val handler: HttpRequest => Future[HttpResponse] = ServiceHandler.concatOrNotFound(
        pb.StateQueryServiceHandler.partial(new StateQueryServiceImpl(dbUrl)),
        pb.StateStreamServiceHandler.partial(new StateStreamServiceImpl(stateBroadcast, dbUrl))
      )

      Http().newServerAt("0.0.0.0", 9001).bind(handler)
      context.log.info("👀 Read gRPC Server is online at 0.0.0.0:9001")

      Behaviors.empty
    }

    ActorSystem[Nothing](behavior, "DentakuReadSystem")
