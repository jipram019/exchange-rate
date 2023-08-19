package forex.http.rates

/**
 * This model used to represent client-facing error, that will be returned  as JSON in case of error response
 *
 * @param message message to return to client
 */
final case class RatesErrorResponse(message: String)


