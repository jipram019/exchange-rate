# A Local Proxy for Forex Rates  

Build a local proxy for getting Currency Exchange Rates  

## **Requirements**     
Forex is a simple application that acts as a local proxy for getting exchange rates. It's a service that can be consumed by other internal services to get the exchange rate between a set of currencies, so they don't have to care about the specifics of third-party providers.

The use case:

- The service returns an exchange rate when provided with 2 supported currencies
- The rate should not be older than 5 minutes
- The service should support at least 10,000 successful requests per day with 1 API token

The drawback:
> The One-Frame service supports a maximum of 1000 requests per day for any given authentication token.





# Services Design  

## **Rates Streaming Service**    
This exchange rate streaming service will pull the latest rates from OneFrame API every 10 seconds (configurable via refresh-rate param in application.conf).  
  
The basic working of the streaming service is implemented with ZIO Stream, of which, once the program starts, it will first call OneFrame API with all enumeration of currency pairs as QueryParams.  

(Ref: https://zio.dev/reference/stream/)

Then it will schedule to call the API every `refresh-rate`.  

The latest rates for all currency pairs will be stored in Cache.  

This design enabled fast initial loading of the cache without having to wait first 10 seconds to fetch the rates and store it.




## **Rates Cache**
The RatesCache implements a Cache using Scala Caffeine Cache. Caffeine provides an in-memory cache using a Google Guava inspired API. The improvements draw on the experience designing Guava's cache and ConcurrentLinkedHashMap.  

https://github.com/ben-manes/caffeine  

The cache will store { Rate.Pair, Rate } as KV pair with using Rate.Pair.toString as its key.  

The ttl is set from expiration key in the application.conf file. In the current configuration, the cache will expire after 5 minutes, and currently the cache capacity is set to 1000 entries which should be more than enough as the cache is refreshed every 10 seconds.  

To better optimize it, the cache expiration could be set the same as refresh rate and or rates expiration itself (which is 5 minutes as per requirement). But as currently we cannot guarantee the reliability of OneFrame API, it is possible that the API call fails during fetching the latest rates. It is also possible that this error is not recovered soon enough (for example due to OneFrame API server itself down for long time). In either case, refresh rate might need to be set shorter enough than the refresh rate, so that when the api fails it will give enough time to recover within 5 minutes expiration.  




## **OneFrame API Client**
The OneFrame API client itself is implemented using SttpBackendClient with HttpClientFutureBackend() as an instance client.  

There is no particular reason why it is been choosen to use Future backend client, merely because it is less complex to implement from the author's point of view. Also, the Future data type from Scala can interoperate well with Cats Effect IO.  

There is this interface to wrap F[_] into Future:
````
Async.fromFuture {
   Async[F].delay {
      ......
   }
}
````

Any non 200 HTTP response from OneFrame API currently is mapped based to the following exception:  

````
4xx --> OneFrameBadRequestException  
5xx --> OneFrameServerException  
others --> OneFrameUnknownException
````  



The timeout will also be handled separately.  


Currently it might not really useful to map OneFrame error responses following this kind of classification, but in the future there might be several use cases in which we want to retry the API call only if it is getting some specific errors from OneFrame API.  

Or we might want to put several type of error response into Dead Letter Queue (DLQ) where we want to retry the call later (if necessary).  

Currently, the service implements Direct Retry 3 times if it is getting 4xx or 5xx response, meaning it will soon retry the API call right after getting these errors.


## **Rates Service**  
Rates Service basically works as layer that expose its endpoint to external client.  

It will query the caching layer where it needs to fetch the latest rate for particulat rate.Pair. 

Some possible exception that might happen at this layer:  

- Rate is missing from the cache due to initial cache loading from RatesStreamService, or due to some internal error from caffeine cache itself
- Rate is staled, following the simple calculation logic :  
  `Rate timestamp + expiration < now` then the rate is expired


# **Running and Testing**
To run the Main application, simply run:  

`sbt run`

In local machine, it will expose the API at http://localhost:8082  

And test using the following curl as an example:  

`curl -H "token: 10dc303535874aeccc86a8251e6992f5" 'localhost:8082/rates?from=USD&to=JPY'`  


To run the unit tests, which are including below unit tests:  

````
RatesHttpRouteTest  
OneFrameApiTest  
RatesStreamServiceTest  
RatesServiceTest
````


simply run:  

`sbt test`  

But don't forget to run one frame docker container first


