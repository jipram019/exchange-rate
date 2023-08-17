package forex.services.rates

import forex.services.rates.interpreters.{OneFrameApi, RatesCache, RatesService}

object Interpreters {
  def live[F[_]](api: OneFrameApi, cache: RatesCache) = RatesService[F](api, cache)
}
