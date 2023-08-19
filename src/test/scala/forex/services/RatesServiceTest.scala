package forex.services

import cats.effect.IO
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.helper.ModApp
import forex.services.rates.errors.Error.LookupFailed
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{OffsetDateTime, ZoneOffset}
import scala.concurrent.Future

class RatesServiceTest extends AnyWordSpec with Matchers {
  "RatesService" should {
    "return correct result" in {
      val data = Rate(
        Rate.Pair(Currency.AUD, Currency.CAD),
        Price(BigDecimal("0.480309880869541285")),
        Timestamp(OffsetDateTime.of(2020, 7, 4, 15, 8, 0, 0, ZoneOffset.UTC))
      )

      val modApp = new ModApp("testapp-0")
      modApp.run(List())
      Thread.sleep(11000)
      val service: RatesService[IO] = modApp.app.module.ratesService
      val rateFuture: Future[Rate] = service.getRateFuture(Rate.Pair(Currency.AUD, Currency.CAD))
      ScalaFutures.whenReady(rateFuture) {
        rate => rate.pair shouldBe
          data.pair
      }
    }

    "return missing rate error if rate is not found" in {
      val modApp = new ModApp("testapp-0")
      modApp.run(List())
      Thread.sleep(11000)
      val service: RatesService[IO] = modApp.app.module.ratesService
      val rateFuture: Future[Rate] = service.getRateFuture(Rate.Pair(Currency.JPY, Currency.JPY))
      ScalaFutures.whenReady(rateFuture.failed) {
        e =>
          e shouldBe a[LookupFailed]
          e.getCause.getMessage shouldBe "Rate is missing!"
      }
    }

    "return expired rate error if rate is expired" in {
      val modApp = new ModApp("testapp-1")
      modApp.run(List())
      Thread.sleep(11000)
      val service: RatesService[IO] = modApp.app.module.ratesService
      val rateFuture: Future[Rate] = service.getRateFuture(Rate.Pair(Currency.AUD, Currency.CAD))
      ScalaFutures.whenReady(rateFuture.failed) {
        e =>
          e shouldBe a[LookupFailed]
          e.getCause.getMessage shouldBe "Rate is expired!"
      }
    }
  }
}
