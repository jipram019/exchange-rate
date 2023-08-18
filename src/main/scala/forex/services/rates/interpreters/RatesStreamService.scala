package forex.services.rates.interpreters

import forex.domain.{Currency, Rate}
import forex.services.rates.errors.Error
import zio.stream.ZStream
import zio.{Runtime, Schedule, Unsafe, ZIO, ZIOAppDefault}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RatesStreamService(api: OneFrameApi, cache: RatesCache, refreshRate: Long){
  private val allPairs: List[Rate.Pair] = Currency.allPairs.map(Rate.Pair.tupled)

  object ratesStream extends ZIOAppDefault {
    def run: ZIO[Any, Throwable, Unit] = {
      val stream: ZIO[Any, Nothing, Unit] =
        ZStream
          .fromSchedule(Schedule.spaced(zio.Duration.fromMillis(refreshRate)))
          .map { _ =>
            api.getAllRates(allPairs)
              .flatMap {
                rates: List[Rate] =>
                  cache.putAll(rates)
              }
              .recoverWith {
                case exception: Error => Future.failed(exception)
              }
          }
          .runDrain

      Unsafe.unsafe {
        implicit unsafe => {
          Runtime.default.unsafe.fork(stream)
        }
      }

      ZIO.from(())
    }
  }
}
