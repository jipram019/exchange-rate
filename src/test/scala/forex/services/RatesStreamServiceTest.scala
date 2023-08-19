package forex.services

import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.helper.ModApp
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{OffsetDateTime, ZoneOffset}

class RatesStreamServiceTest extends AnyWordSpec with Matchers {
  "RatesStreamService" should {
    "update cache in the background via RatesCache and ZStream" in {
      val rate: Rate = Rate(
        Rate.Pair(Currency.JPY, Currency.AUD),
        Price(BigDecimal(0.5D)),
        Timestamp(OffsetDateTime.of(2020, 7, 5, 11, 0, 0, 0, ZoneOffset.UTC))
      )

      val modApp = new ModApp("testapp-0")
      modApp.run(List())
      Thread.sleep(11000)
      ScalaFutures.whenReady(modApp.app.module.ratesCache.getRate(rate.pair)){
        result =>
          println(rate)
          rate.pair shouldBe result.get.pair
      }
    }
  }
}
