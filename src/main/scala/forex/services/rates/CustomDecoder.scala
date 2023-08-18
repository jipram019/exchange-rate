package forex.services.rates

import forex.domain.{Currency, Price, Rate, Timestamp}
import io.circe._
import io.circe.generic.semiauto.deriveDecoder

import java.time.OffsetDateTime
import scala.util.Try

object CustomDecoder {
  implicit val timestampDecoder: Decoder[OffsetDateTime] = Decoder.decodeString.emapTry((value: String) => Try(OffsetDateTime.parse(value)))
  implicit val currencyDecoder: Decoder[Currency] = deriveDecoder[Currency]
  implicit val rateDecoder: Decoder[Rate] = {
    (cursor: HCursor) =>
      for {
        from <- cursor.downField("from").as[String]
        to <- cursor.downField("to").as[String]
        price <- cursor.downField("price").as[BigDecimal]
        timestamp <- cursor.downField("time_stamp").as[OffsetDateTime]
      } yield {
        Rate(Rate.Pair(Currency.fromString(from), Currency.fromString(to)), Price(price), Timestamp(timestamp))
      }
    }

  implicit val rateListDecoder: Decoder[List[Rate]] = Decoder.decodeList(rateDecoder)
}
