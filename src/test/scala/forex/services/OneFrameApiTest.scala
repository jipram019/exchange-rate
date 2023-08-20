package forex.services

import forex.config.{HttpConfig, OneFrameConfig}
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.services.rates.errors.Error.{OneFrameBadRequestException, OneFrameServerException, OneFrameUnknownException}
import forex.services.rates.interpreters.OneFrameApi
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.capabilities
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{HttpClientFutureBackend, HttpError, Request, Response}
import sttp.model.{Header, StatusCode}

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class OneFrameApiTest extends AnyWordSpec with Matchers {
  val mockSttp: SttpBackendStub[Future, capabilities.WebSockets] = HttpClientFutureBackend.stub
  val config: OneFrameConfig = OneFrameConfig(
    HttpConfig("localhost", 8080, FiniteDuration(2, "minutes")), "10dc303535874aeccc86a8251e6992f5",
    300000, 10000, 1000, 10
  )
  val pairs: Seq[Rate.Pair] = Seq(Rate.Pair(Currency.USD, Currency.EUR), Rate.Pair(Currency.AUD, Currency.SGD))

  def requestMatchers(pairs: Seq[Rate.Pair]): Request[_, _] => Boolean = { request =>
    val queryParams: String = {
      val builder: StringBuilder = new StringBuilder
      builder.append(s"?pair=${pairs.head.from}${pairs.head.to}")
      pairs.drop(1).foreach(pair => builder.append(s"&pair=${pair.from}${pair.to}"))
      builder.result()
    }
    request.uri.toString() == s"http://localhost:8080/rates$queryParams" &&
      request.headers.contains(Header("token", config.token))
  }

  "OneFrameApi" should {
    "return correct rates" in {
      val response: String =
        """
          |[
          |  {
          |    "from": "AUD",
          |    "to": "CHF",
          |    "bid": 0.45547575523254624,
          |    "ask": 0.5854499803296648,
          |    "price": 0.52046286778110552,
          |    "time_stamp": "2023-08-19T07:44:12.865Z"
          |  },
          |  {
          |    "from": "CAD",
          |    "to": "EUR",
          |    "bid": 0.10826980395578956,
          |    "ask": 0.9742417712441405,
          |    "price": 0.54125578759996503,
          |    "time_stamp": "2023-08-19T07:44:12.865Z"
          |  }
          |]
          |""".stripMargin

      val expected = Seq(
        Rate(
          Rate.Pair(Currency.AUD, Currency.CHF),
          Price(BigDecimal("0.52046286778110552")),
          Timestamp(OffsetDateTime.parse("2023-08-19T07:44:12.865Z"))
        ),
        Rate(
          Rate.Pair(Currency.CAD, Currency.EUR),
          Price(BigDecimal("0.54125578759996503")),
          Timestamp(OffsetDateTime.parse("2023-08-19T07:44:12.865Z"))
        )
      )

      val mockApi: SttpBackendStub[Future, capabilities.WebSockets] =
        mockSttp.whenRequestMatches(requestMatchers(pairs)).thenRespond(response)

      val result: Future[List[Rate]] = new OneFrameApi(config, mockApi).getAllRates(pairs)
      ScalaFutures.whenReady(result) { resp =>
        resp shouldBe expected
      }
    }

    "retry several times if one frame api fails" in {
      val response1: String =
        """
          |{
          |  "error": "Something bad happened"
          |}
          |""".stripMargin

      val response2: String =
        """
          |[
          |  {
          |    "from": "USD",
          |    "to": "EUR",
          |    "bid": 0.8702743979669029,
          |    "ask": 0.8129834411047454,
          |    "price": 0.84162891953582415,
          |    "time_stamp": "2020-07-04T17:56:12.907Z"
          |  },
          |  {
          |    "from": "AUD",
          |    "to": "SGD",
          |    "bid": 0.5891192858066693,
          |    "ask": 0.8346334453420459,
          |    "price": 0.7118763655743576,
          |    "time_stamp": "2020-07-04T17:56:12.907Z"
          |  }
          |]
          |""".stripMargin


      val mockApi: SttpBackendStub[Future, capabilities.WebSockets] =
        mockSttp.whenRequestMatches(requestMatchers(pairs)).thenRespondCyclic(response1, response1, response2)

      val expected = Seq(
        Rate(
          Rate.Pair(Currency.USD, Currency.EUR),
          Price(BigDecimal("0.84162891953582415")),
          Timestamp(OffsetDateTime.parse("2020-07-04T17:56:12.907Z"))
        ),
        Rate(
          Rate.Pair(Currency.AUD, Currency.SGD),
          Price(BigDecimal("0.7118763655743576")),
          Timestamp(OffsetDateTime.parse("2020-07-04T17:56:12.907Z"))
        )
      )

      val result1: Future[List[Rate]] = new OneFrameApi(config, mockApi).getAllRates(pairs)
      val result2: Future[List[Rate]] = new OneFrameApi(config, mockApi).getAllRates(pairs)
      val result3: Future[List[Rate]] = new OneFrameApi(config, mockApi).getAllRates(pairs)

      ScalaFutures.whenReady(result1.failed) { e =>
        e shouldBe a[OneFrameUnknownException]
      }
      ScalaFutures.whenReady(result2.failed) { e =>
        e shouldBe a[OneFrameUnknownException]
      }
      ScalaFutures.whenReady(result3) { result =>
        result shouldBe expected
      }
    }

    "return OneFrameServerException when getting HTTP 500" in {
      val statusText: String =
        """
          |{
          |  "error": "Something bad happened"
          |}
          |""".stripMargin

      val response2= Response(Left(HttpError(statusText, StatusCode.InternalServerError)), StatusCode.InternalServerError, statusText)

      val mockApi: SttpBackendStub[Future, capabilities.WebSockets] =
        mockSttp.whenRequestMatches(requestMatchers(pairs)).thenRespond(response2)

      val result: Future[List[Rate]] = new OneFrameApi(config, mockApi).getAllRates(pairs)
      ScalaFutures.whenReady(result.failed) { e =>
        e shouldBe a[OneFrameServerException]
        e.getCause.getMessage shouldBe statusText
      }
    }

    "return OneFrameBadRequestException when getting HTTP 400" in {
      val statusText: String =
        """
          |{
          |  "error": "Something bad happened"
          |}
          |""".stripMargin

      val response2= Response(Left(HttpError(statusText, StatusCode.BadRequest)), StatusCode.BadRequest, statusText)

      val mockApi: SttpBackendStub[Future, capabilities.WebSockets] =
        mockSttp.whenRequestMatches(requestMatchers(pairs)).thenRespond(response2)

      val result: Future[List[Rate]] = new OneFrameApi(config, mockApi).getAllRates(pairs)
      ScalaFutures.whenReady(result.failed) { e =>
        e shouldBe a[OneFrameBadRequestException]
        e.getCause.getMessage shouldBe statusText
      }
    }

    "return OneFrameUnknownException when getting status other than 400 and 500" in {
      val statusText: String =
        """
          |{
          |  "error": "Unknown error!"
          |}
          |""".stripMargin

      val response2= Response(Left(HttpError(statusText, StatusCode.NotModified)), StatusCode.NotModified, statusText)

      val mockApi: SttpBackendStub[Future, capabilities.WebSockets] =
        mockSttp.whenRequestMatches(requestMatchers(pairs)).thenRespond(response2)

      val result: Future[List[Rate]] = new OneFrameApi(config, mockApi).getAllRates(pairs)
      ScalaFutures.whenReady(result.failed) { e =>
        e shouldBe a[OneFrameUnknownException]
        e.getCause.getMessage shouldBe statusText
      }
    }
  }

}
