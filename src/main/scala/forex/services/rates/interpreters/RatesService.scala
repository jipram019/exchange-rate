package forex.services.rates.interpreters

import cats.effect.{Async, ContextShift}
import forex.domain.Rate
import forex.services.rates.errors.Error
import forex.services.rates.errors.Error.LookupFailed
import forex.services.rates.{Algebra, errors}

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneOffset, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, MILLISECONDS}

class RatesService[F[_]](cache: RatesCache, expiration: Long) extends Algebra[F]{
  def isExpired(dateTime: OffsetDateTime): Boolean =
    ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
      .isAfter(dateTime.atZoneSameInstant(ZoneOffset.UTC).plus(Duration.apply(expiration, MILLISECONDS).toMillis, ChronoUnit.MILLIS))

  override def getRate[T[_]: Async: ContextShift](request: Rate.Pair): T[Either[errors.Error, Rate]] = {
    Async.fromFuture(
      Async[T].delay(
        cache.getRate(request)
          .recoverWith {
            case exception: Error => Future.failed(exception)
          }
          .flatMap {
            case Some(rate) =>
              if(isExpired(rate.timestamp.value)) Future.failed(LookupFailed("Rates are expired!"))
              else Future.successful(Right(rate))
            case None => Future.failed(LookupFailed("Rates cannot be fetched"))
        }
      )
    )
  }

  override def getRateFuture(request: Rate.Pair): Future[Rate] = {
    cache.getRate(request)
      .recoverWith {
        case exception: Error => Future.failed(exception)
      }
      .flatMap {
        case Some(rate) =>
          if(isExpired(rate.timestamp.value)) {
            Future.failed(LookupFailed("Rate is expired!"))
          }
          else {
            Future.successful(rate)
          }
        case None => Future.failed(LookupFailed("Rate is missing!"))
      }
  }
}

object RatesService {
  def apply[F[_]](cache: RatesCache, expiration: Long): RatesService[F] =
    new RatesService[F](cache, expiration)
}
