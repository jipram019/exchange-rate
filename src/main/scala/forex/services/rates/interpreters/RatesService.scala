package forex.services.rates.interpreters

import cats.effect.{Async, ContextShift}
import forex.domain.{Currency, Rate}
import forex.services.rates.errors.Error
import forex.services.rates.errors.Error.LookupFailed
import forex.services.rates.{Algebra, errors}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RatesService[F[_]](api: OneFrameApi, cache: RatesCache) extends Algebra[F]{
  private val allPairs: List[Rate.Pair] = Currency.allPairs.map(Rate.Pair.tupled)

  override def getRate[T[_]: Async: ContextShift](request: Rate.Pair): T[Either[errors.Error, Rate]] = {
    Async.fromFuture(
      Async[T].delay(
        getRateFuture(request)
      )
    )
  }

  def getRateFuture(request: Rate.Pair): Future[Either[errors.Error, Rate]] = {
    cache.getRate(request).flatMap {
      case Some(rate) => Future.successful(Right(rate))
      case None =>
        api.getAllRates(allPairs)
          .flatMap {
            rates: List[Rate] =>
              cache.putAll(rates).flatMap {_ =>
                cache.getRate(request).flatMap {
                  case Some(rate) => Future.successful(Right(rate))
                  case None => Future.failed(LookupFailed("Rates cannot be fetched"))
                }
              }
          }
          .recoverWith {
            case exception: Error => Future.failed(exception)
          }
    }
  }
}

object RatesService {
  def apply[F[_]](api: OneFrameApi, cache: RatesCache): RatesService[F] =
    new RatesService[F](api, cache)
}
