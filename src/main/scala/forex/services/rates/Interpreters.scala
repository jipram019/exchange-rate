package forex.services.rates

import forex.services.rates.interpreters.{RatesCache, RatesService}

object Interpreters {
  def live[F[_]](cache: RatesCache, expiration: Long) = RatesService[F](cache, expiration)
}
