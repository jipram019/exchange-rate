package forex.domain

case class Rate(
    pair: Rate.Pair,
    price: Price,
    timestamp: Timestamp
)

case class Rates(
    rates: Seq[Rate]
                )

object Rate {
  final case class Pair(
      from: Currency,
      to: Currency
  )
}
