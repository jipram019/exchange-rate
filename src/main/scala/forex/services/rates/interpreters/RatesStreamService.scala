/*package forex.services.rates.interpreters

import cats.effect.{Async, ContextShift, Timer}
import cats.implicits.toFlatMapOps
import forex.domain.{Currency, Rate}
import fs2.Stream
import scalacache.Cache

import scala.concurrent.duration.FiniteDuration

class RatesStreamService[F[_]: Async: ContextShift: Timer](oneFrame: OneFrameApi, cache: RatesCache, refreshRate: FiniteDuration){
  private val allPairs: List[Rate.Pair] = Currency.allPairs.map(Rate.Pair.tupled)

  def getRates: F[Cache[Rate]] = cache.getAll()

  def cacheUpdater(): Stream[F, Unit] = {
    def updateCache(): F[Unit] = {
      oneFrame
        .getAllPairs(allPairs)
        .flatMap[List[Rate]] {
          case Right(value: List[Rate]) => Async[F].pure(value)
          case Left(exception: Exception) => Async[F].raiseError(exception)
        }
        .flatMap(rates => cache.putAll(rates))
    }
    Stream.eval(updateCache()) >> Stream.awakeEvery[F](refreshRate) >> Stream.eval(updateCache())
  }
}*/
