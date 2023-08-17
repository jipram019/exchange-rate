package forex.programs.rates

import cats.effect.{Async, ContextShift}
import forex.domain.Rate

trait Algebra[F[_]] {
  def get[T[_]: Async: ContextShift](request: Protocol.GetRatesRequest): T[Either[errors.Error,Rate]]
}
