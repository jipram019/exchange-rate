package forex.services

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import forex.Module
import forex.config.{ApplicationConfig, Config}
import forex.domain.{Currency, Price, Rate, Timestamp}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import java.time.{OffsetDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext

class RatesStreamServiceTest extends AsyncWordSpecLike with Matchers {
  val config: ApplicationConfig = Config.loadUnsafe("testapp")
  class TestApp[F[_]: ConcurrentEffect: Timer: ContextShift] {
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
  object TestApp extends IOApp {
    val app = new TestApp[IO]
    override def run(args: List[String]): IO[ExitCode] =
      app.stream(executionContext).compile.drain.as(ExitCode.Success)
  }

  "RatesStreamService" should {
    "update cache in the background via RatesCache and ZStream" in {
      val rate: Rate = Rate(
        Rate.Pair(Currency.JPY, Currency.AUD),
        Price(BigDecimal(0.5D)),
        Timestamp(OffsetDateTime.of(2020, 7, 5, 11, 0, 0, 0, ZoneOffset.UTC))
      )

      TestApp.run(List())
      Thread.sleep(11000)
      ScalaFutures.whenReady(TestApp.app.module.ratesCache.getRate(rate.pair)){
        result =>
          println(rate)
          rate.pair shouldBe result.get.pair
      }
    }

  }
}
