package forex.services.rates.interpreters

import com.github.benmanes.caffeine.cache.Caffeine
import forex.domain.Rate
import scalacache._
import scalacache.caffeine.CaffeineCache
import scalacache.modes.scalaFuture.mode

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class RatesCache(ttl: Long, cacheSize: Long) {
  private val underlyingCaffeineCache =
    Caffeine
      .newBuilder()
      .maximumSize(cacheSize)
      .build[String, Entry[Rate]]

  implicit val cache: Cache[Rate] = CaffeineCache(underlyingCaffeineCache)

  def getRate(pair: Rate.Pair): Future[Option[Rate]] = {
    val rateFuture = get(pair.toString)
    for {
      rate <- rateFuture
    } yield rate
  }

  def getAll(): Future[Cache[Rate]] = Future {
    cache
  }

  def putRate(rate: Rate): Future[Unit] =  {
    val putResult = put(rate.pair.toString)(rate, ttl = Some(Duration(ttl, TimeUnit.SECONDS)))
    for {
      _ <- putResult
    } yield()
  }

  def putAll(rates: Seq[Rate]): Future[Unit] = Future {
    rates.foreach(rate => putRate(rate))
  }
}

object RatesCache {
  def apply(ttl: Long, cacheSize: Long): RatesCache = {
    new RatesCache(ttl, cacheSize)
  }
}
