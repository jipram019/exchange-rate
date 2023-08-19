package forex.programs.rates

import forex.services.rates.errors.{Error => RatesServiceError}

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateLookupFailed(msg: String) extends Error
    final case class TimeoutException() extends Error
    final case class RateServerException(error: String) extends Error
    final case class RateClientException(error: String) extends Error
    final case class RateUnknownException(error: String) extends Error
    final case class RateApiConnectException(error: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.LookupFailed(msg) => Error.RateLookupFailed(msg)
    case RatesServiceError.OneFrameServerException(msg) => Error.RateServerException(msg)
    case RatesServiceError.OneFrameBadRequestException(msg) => Error.RateClientException(msg)
    case RatesServiceError.OneFrameUnknownException(msg) => Error.RateUnknownException(msg)
    case RatesServiceError.OneFrameConnectException(msg) => Error.RateApiConnectException(msg)
    case RatesServiceError.ApiTimeoutException => Error.TimeoutException()
  }
}
