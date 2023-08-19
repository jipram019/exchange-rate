package forex.services.rates.interpreters

import forex.domain.{Currency, Rate}
import zio.stream.{ZSink, ZStream}
import zio.{Runtime, Schedule, Unsafe, ZIO, ZIOAppDefault}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RatesStreamService(api: OneFrameApi, cache: RatesCache, refreshRate: Long){
  private val allPairs: List[Rate.Pair] = Currency.allPairs.map(Rate.Pair.tupled)

  object ratesStream extends ZIOAppDefault {
    def run: ZIO[Any, Throwable, Unit] = {
      val apiCall: Future[Unit] = api.getAllRates(allPairs)
        .flatMap {
          rates: List[Rate] =>
            cache.putAll(rates)
        }
        .recoverWith {
          case exception =>
            Future.failed(exception)
        }

      val stream: ZIO[Any, Throwable, Unit] =
        ZStream
          .from(apiCall)
          .schedule(Schedule.spaced(zio.Duration.fromMillis(refreshRate)))
          .map { _ => apiCall}
          .run(ZSink.drain)

      Unsafe.unsafe {
        implicit unsafe => {
          Runtime.default.unsafe.fork(stream)
        }
      }

      ZIO.from(())
    }
  }
}
