package forex.http

import cats.effect.{Async, ContextShift, IO, Sync}
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.http.rates.RatesHttpRoutes
import forex.programs.RatesProgram
import forex.programs.rates.errors.Error.RateLookupFailed
import forex.programs.rates.{errors, Protocol => RProtocol}
import io.circe.Json
import io.circe.literal._
import org.http4s.Request
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import java.time.{OffsetDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}

class RatesHttpRoutesTest extends AsyncWordSpecLike with Matchers {
  val responses: Map[RProtocol.GetRatesRequest, Either[errors.Error, Rate]] = Map(
    RProtocol.GetRatesRequest(Currency.AUD, Currency.CAD) -> Right(
      Rate(
        Rate.Pair(Currency.AUD, Currency.CAD),
        Price(BigDecimal(0.5D)),
        Timestamp(OffsetDateTime.of(2020, 7, 4, 10, 0, 0, 0, ZoneOffset.UTC))
      )
    ),
    RProtocol.GetRatesRequest(Currency.SGD, Currency.AUD) -> Left(RateLookupFailed("Rate is missing!"))
  )

  class MockedRatesProgram[F[_]](responses: Map[RProtocol.GetRatesRequest, Either[errors.Error, Rate]])
      extends RatesProgram[F] {
    override def get[T[_] : Async : ContextShift](request: RProtocol.GetRatesRequest): T[Either[errors.Error, Rate]] =
      Sync[T].delay(responses(request))
  }

  val program: MockedRatesProgram[IO] = new MockedRatesProgram[IO](responses)
  override implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO]            = IO.contextShift(executionContext)

  "RatesHttpRoutes" should {
    "return valid response" in {
      val routes: RatesHttpRoutes[IO] = new RatesHttpRoutes[IO](program)
      val request: Request[IO] = Request[IO](uri = uri"/rates?from=AUD&to=CAD")

      val futureJson: Future[Json] = routes.routes.apply(request).value
        .map {
          someResult =>
            someResult.map { result => result.as[Json] }
              .getOrElse(Sync[IO].raiseError(new Exception("Empty!")))
              .unsafeToFuture()
        }
        .unsafeToFuture()
        .flatMap { r => r }

      ScalaFutures.whenReady(futureJson) {
        response =>
          val expected: Json =
            json"""
                {
                    "from": "AUD",
                    "to": "CAD",
                    "price": 0.5,
                    "timestamp": "2020-07-04T10:00:00Z"
                }
            """
          expected shouldBe response
      }
    }

    "return error response" in {
      val routes: RatesHttpRoutes[IO] = new RatesHttpRoutes[IO](program)
      val request: Request[IO] = Request[IO](uri = uri"/rates?from=SGD&to=AUD")

      val futureJson: Future[Json] = routes.routes.apply(request).value
        .map {
          someResult =>
            someResult.map { response => response.as[Json] }
              .getOrElse(Sync[IO].raiseError(new Exception("Empty!")))
              .unsafeToFuture()
        }
        .unsafeToFuture()
        .flatMap(r => r)

      ScalaFutures.whenReady(futureJson) {
        response =>
          println(response)
          val expected: Json =
            json"""
                "Rate is missing!"
            """
          println(expected)
          expected shouldBe response
      }
    }
  }
}
