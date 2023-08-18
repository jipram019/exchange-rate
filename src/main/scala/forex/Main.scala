package forex

import cats.effect._
import forex.config._
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  val config: ApplicationConfig = Config.loadUnsafe("app")
  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO](config).stream(executionContext).compile.drain.as(ExitCode.Success)
}

class Application[F[_]: ConcurrentEffect: Timer: ContextShift](config: ApplicationConfig) {
  def stream(ec: ExecutionContext): Stream[F, ExitCode] = {
    val module = new Module[F](config)
    module.ratesStreamService.ratesStream.run
    BlazeServerBuilder[F](ec)
      .bindHttp(config.http.port, config.http.host)
      .withHttpApp(module.httpApp)
      .withoutBanner
      .serve
  }
}
