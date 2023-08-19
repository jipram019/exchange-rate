package forex.http.rates

import cats.implicits.toBifunctorOps
import enumeratum.NoSuchMember
import forex.domain.Currency
import org.http4s.{ParseFailure, QueryParamDecoder}
import org.http4s.dsl.impl.QueryParamDecoderMatcher

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Currency] = QueryParamDecoder[String].emap {
    input: String =>
      Currency
        .withNameEither(input)
        .leftMap { error: NoSuchMember[Currency] =>
          ParseFailure(s"Unknown Currency ${error.notFoundName}", error.getMessage())
        }
  }

  object FromQueryParam extends QueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[Currency]("to")

}
