package forex.helper

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import forex.Module
import forex.config.{ApplicationConfig, Config}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

class TestApp[F[_]: ConcurrentEffect: Timer: ContextShift](configName: String) {
  val config: ApplicationConfig = Config.loadUnsafe(configName)
  val module = new Module[F](config)
  def stream(ec: ExecutionContext): Stream[F, ExitCode] = {
    module.ratesStreamService.ratesStream.run
    BlazeServerBuilder[F](ec)
      .bindHttp(config.http.port, config.http.host)
      .withHttpApp(module.httpApp)
      .withoutBanner
      .serve
  }
}

class ModApp(configName: String) extends IOApp {
  val app = new TestApp[IO](configName)
  override def run(args: List[String]): IO[ExitCode] =
    app.stream(executionContext).compile.drain.as(ExitCode.Success)
}
