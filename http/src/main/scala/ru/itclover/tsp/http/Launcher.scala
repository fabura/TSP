package ru.itclover.tsp.http

import scala.util.{Failure, Properties, Success, Try}
import java.net.URLDecoder
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.io.StdIn
import cats.implicits._

object Launcher extends App with HttpService {
  private val configs = ConfigFactory.load()
  override val isDebug: Boolean = configs.getBoolean("general.is-debug")
  private val log = Logger("Launcher")

  implicit val system: ActorSystem = ActorSystem("TSP-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  override val flinkMonitoringHost = Properties
    .propOrElse("FLINK_MONITORING_HOST", configs.getString("flink.monitoring.host"))
  override val flinkMonitoringPort = Either.catchNonFatal(
    Properties.propOrElse("FLINK_MONITORING_PORT", configs.getString("flink.monitoring.port")).toInt
  ).fold(
    ex => throw new RuntimeException(s"Cannot parse FLINK_MONITORING_PORT: ${ex.getMessage}"),
    port => port
  )

  val streamEnvOrError = if (args(0) == "flink-cluster-test") {
    val (host, port) = getClusterHostPort match {
      case Right(hostAndPort) => hostAndPort
      case Left(err) => throw new RuntimeException(err)
    }
    log.info(s"Starting TEST TSP on cluster Flink: $host:$port with monitoring in $monitoringUri")
    Right(StreamExecutionEnvironment.createRemoteEnvironment(host, port, args(1)))
  } else if (args.length != 1) {
    Left("You need to provide one arg: `flink-local` or `flink-cluster` to specify Flink execution mode.")
  } else if (args(0) == "flink-local") {
    createLocalEnv
  } else if (args(0) == "flink-cluster") {
    createClusterEnv
  } else {
    Left(s"Unknown argument: `${args(0)}`.")
  }

  implicit override val streamEnvironment = streamEnvOrError match {
    case Right(env) => env
    case Left(err)  => throw new RuntimeException(err)
  }

  streamEnvironment.setMaxParallelism(configs.getInt("flink.max-parallelism"))

  private val host = configs.getString("http.host")
  private val port = configs.getInt("http.port")
  val bindingFuture = Http().bindAndHandle(route, host, port)

  log.info(s"Service online at http://$host:$port/" + (if (isDebug) " in debug mode." else ""))

  if (configs.getBoolean("general.is-follow-input")) {
    log.info("Press RETURN to stop...")
    StdIn.readLine()
    log.info("Terminating...")
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => Await.result(system.whenTerminated.map(_ => log.info("Terminated... Bye!")), 60.seconds))
  } else {
    scala.sys.addShutdownHook {
      log.info("Terminating...")
      system.terminate()
      Await.result(system.whenTerminated, 60.seconds)
      log.info("Terminated... Bye!")
    }
  }

  def getClusterHostPort: Either[String, (String, Int)] = for {
    clusterPort <- Properties
      .propOrNone("FLINK_JOBMGR_PORT")
      .map(p => Either.catchNonFatal(p.toInt).left.map { ex: Throwable =>
        s"Cannot parse FLINK_JOBMGR_PORT ($p): ${ex.getMessage}"
      })
      .getOrElse(Right(configs.getInt("flink.job-manager.port")))
    clusterHost = Properties.envOrElse("FLINK_JOBMGR_HOST", configs.getString("flink.job-manager.host"))
  } yield (clusterHost, clusterPort)

  def createClusterEnv: Either[String, StreamExecutionEnvironment] = getClusterHostPort flatMap {
    case (clusterHost, clusterPort) =>
      log.info(s"Starting TSP on cluster Flink: $clusterHost:$clusterPort with monitoring in $monitoringUri")
      val rawJarPath = this.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
      val jarPath = URLDecoder.decode(rawJarPath, "UTF-8")

      Either.cond(
        jarPath.endsWith(".jar"),
        StreamExecutionEnvironment.createRemoteEnvironment(clusterHost, clusterPort, jarPath),
        s"Jar path is invalid: `$jarPath` (no jar extension)"
      )
  }

  def createLocalEnv: Either[String, StreamExecutionEnvironment] = {
    log.info(s"Starting local Flink with monitoring in $monitoringUri")
    Right(StreamExecutionEnvironment.createLocalEnvironment())
  }
}
