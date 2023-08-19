package forex.http
package rates

import cats.effect.{Async, ContextShift}
import cats.syntax.flatMap._
import forex.domain.Rate
import forex.http.rates.Converters.GetApiResponseOps
import forex.http.rates.QueryParams.{FromQueryParam, ToQueryParam}
import forex.programs.RatesProgram
import forex.programs.rates.errors.Error.{RateApiConnectException, RateClientException, RateLookupFailed, RateServerException, RateUnknownException}
import forex.programs.rates.{Protocol => RatesProgramProtocol}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Async: ContextShift](rates: RatesProgram[F]) extends Http4sDsl[F] {

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to)  =>
      rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap {
        case Right(rate: Rate)             => Ok(rate.asGetApiResponse)
        case Left(error: RateLookupFailed) => InternalServerError(RatesErrorResponse(error.msg))
        case Left(error: RateApiConnectException) => BadGateway(RatesErrorResponse(error.error))
        case Left(error: RateServerException) => ServiceUnavailable(RatesErrorResponse(error.error))
        case Left(error: RateClientException) => BadRequest(RatesErrorResponse(error.error))
        case Left(error: RateUnknownException) => ServiceUnavailable(RatesErrorResponse(error.error))
        case _ => RequestTimeout(RatesErrorResponse("timeout happen!"))
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}
