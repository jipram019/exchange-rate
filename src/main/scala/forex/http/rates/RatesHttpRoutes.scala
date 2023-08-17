package forex.http
package rates

import cats.effect.{Async, ContextShift, Sync}
import cats.syntax.flatMap._
import forex.http.rates.Converters.GetApiResponseOps
import forex.http.rates.QueryParams.{FromQueryParam, ToQueryParam}
import forex.programs.RatesProgram
import forex.programs.rates.{Protocol => RatesProgramProtocol}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Async: ContextShift](rates: RatesProgram[F]) extends Http4sDsl[F] {

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to)  =>
      rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap(Sync[F].fromEither).flatMap { rate =>
        Ok(rate.asGetApiResponse)
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}
