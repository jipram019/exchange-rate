package forex.services.rates

import cats.effect.{Async, ContextShift}
import forex.domain.Rate

trait Algebra[T[_]] {
  def getRate[F[_]: Async: ContextShift](pair: Rate.Pair): F[Either[errors.Error, Rate]]
}
