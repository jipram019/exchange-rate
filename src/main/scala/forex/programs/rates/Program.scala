package forex.programs.rates

import cats.data.EitherT
import cats.effect.{Async, ContextShift}
import forex.domain._
import forex.programs.rates.errors.toProgramError
import forex.services.RatesService

class Program[F[_]](
    ratesService: RatesService[F]
) extends Algebra[F] {
  override def get[T[_]: Async: ContextShift](request: Protocol.GetRatesRequest): T[Either[errors.Error, Rate]] = {
    EitherT(ratesService.getRate(Rate.Pair(request.from, request.to))).leftMap(toProgramError).value
  }
}

object Program {
  def apply[F[_]](
      ratesService: RatesService[F]
  ): Algebra[F] = new Program(ratesService)

}
