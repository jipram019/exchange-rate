package forex.services.rates

object errors {

  sealed trait Error extends Exception
  object Error {
    final case object ApiTimeoutException extends Error {
      val message: String = "Connection timeout from One Frame!"
    }
    final case class LookupFailed(error: String) extends Error
    final case class OneFrameServerException(error: String) extends Error
    final case class OneFrameClientException(error: String) extends Error
    final case class OneFrameUnknownException(error: String) extends Error
    final case class OneFrameConnectException(error: String) extends Error
  }
}
