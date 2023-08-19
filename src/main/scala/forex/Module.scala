package forex

import cats.effect.{Concurrent, ContextShift, Timer}
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services.rates.interpreters.{OneFrameApi, RatesCache, RatesStreamService}
import forex.services.{RatesService, RatesServices}
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}
import sttp.client3.HttpClientFutureBackend

class Module[F[_]: Concurrent: Timer: ContextShift](config: ApplicationConfig) {
  val oneFrameClient: OneFrameApi = OneFrameApi(config.oneFrame, HttpClientFutureBackend())
  val ratesCache: RatesCache = RatesCache(config.oneFrame.expiration, config.oneFrame.cacheSize)
  val ratesStreamService: RatesStreamService = new RatesStreamService(oneFrameClient, ratesCache, config.oneFrame.refreshRate)
  val ratesService: RatesService[F] = RatesServices.live[F](ratesCache, config.oneFrame.expiration)
  val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)
  val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }
  private val http: HttpRoutes[F] = ratesHttpRoutes
  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
