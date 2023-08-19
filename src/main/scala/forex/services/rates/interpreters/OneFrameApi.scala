package forex.services.rates.interpreters

import forex.config.OneFrameConfig
import forex.domain.Rate
import forex.services.rates.CustomDecoder.rateListDecoder
import forex.services.rates.errors.Error.{OneFrameBadRequestException, OneFrameConnectException, OneFrameServerException, OneFrameUnknownException}
import sttp.capabilities
import sttp.client3.circe.asJson
import sttp.client3.{Identity, RequestT, Response, ResponseException, SttpBackend, UriContext, basicRequest}
import sttp.model.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OneFrameApi(
    config: OneFrameConfig,
    sttpBackendClient: SttpBackend[Future, capabilities.WebSockets]
) {

  def getAllRates(pairs: Seq[Rate.Pair]): Future[List[Rate]] = {
    val params: Seq[(String, String)] = pairs.map(
      (pair: Rate.Pair) => "pair" -> s"${pair.from}${pair.to}"
    )
    val url: Uri = uri"http://${config.http.host}:${config.http.port}/rates?$params"

    val httpRequest: RequestT[Identity, Either[ResponseException[String, Exception], List[Rate]], Any] =
      basicRequest
        .readTimeout(config.http.timeout)
        .header("token", config.token)
        .get(url)
        .response(asJson[List[Rate]])

    sttpBackendClient
      .send(httpRequest)
      .recoverWith {
        exception =>
          Future.failed(OneFrameConnectException(exception.getMessage))
      }
      .flatMap[List[Rate]] {
        httpResponse: Response[Either[ResponseException[String, Exception], List[Rate]]] =>
          httpResponse.body match {
            case Right(value) =>
              Future.successful(value)
            case Left(_: ResponseException[String, Exception]) =>
              httpResponse.code match {
                case status if status.isServerError =>
                  Future.failed(OneFrameServerException(httpResponse.statusText))
                case status if status.isClientError =>
                  Future.failed(OneFrameBadRequestException(httpResponse.statusText))
                case _ =>
                  Future.failed(OneFrameUnknownException(httpResponse.statusText))
              }
          }
      }
  }
}

object OneFrameApi {
  def apply(
      oneFrameConfig: OneFrameConfig,
      sttpBackendClient: SttpBackend[Future, capabilities.WebSockets]
  ): OneFrameApi =
    new OneFrameApi(oneFrameConfig, sttpBackendClient)
}


