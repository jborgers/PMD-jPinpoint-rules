
Java Code Performance - pitfalls and best practices
=====================
By Jeroen Borgers ([jPinpoint](https://www.jpinpoint.com)) and Peter Paul Bakker ([Stokpop](https://www.stokpop.com)), sponsored by Rabobank

# Table of contents

- [Introduction](#introduction)
- [Improper back-end interaction / remote service calls](#improper-back-end-interaction--remote-service-calls)
- [Improper asynchrony](#improper-asynchrony)
- [Improper caching](#improper-caching)
- [Too much session usage](#too-much-session-usage)
  - [Explicit session attributes](#explicit-session-attributes)
  - [Spring ModelMaps](#spring-modelmaps)
- [Reloading lists of values](#reloading-lists-of-values)
- [Inefficient database access](#inefficient-database-access)
- [Improper use of XML and remoting](#improper-use-of-xml-and-remoting)
- [Improper use of JSON and remoting](#improper-use-of-json-and-remoting)
- [Using XPath](#using-xpath)
- [Improper logging](#improper-logging)
- [Improper Streaming I/O](#improper-streaming-io)
- [Extensive use of classpath scanning](#extensive-use-of-classpath-scanning)
- [Unnecessary use of reflection](#unnecessary-use-of-reflection)
- [Thread unsafety and lock contention](#thread-unsafety-and-lock-contention)
- [Unnecessary execution](#unnecessary-execution)
- [Inefficient memory usage](#inefficient-memory-usage)
- [Improper use of collections](#improper-use-of-collections)
- [Inefficient String usage](#inefficient-string-usage)
- [Inefficient date time formatting](#inefficient-date-time-formatting)
- [Inefficient regular expression usage](#inefficient-regular-expression-usage)
- [Use of slow library calls](#use-of-slow-library-calls)
- [Potential memory leaks](#potential-memory-leaks)
- [Violation of Encapsulation, DRY or SRP](#violation-of-encapsulation-dry-or-srp)
  
Introduction
-------------

We categorized many performance pitfalls based on our findings in practice in the last decade. Sources we used are: performance code reviews, load and stress tests, heap analyses, 
profiling and production problems of various applications of companies. We present these pitfalls with best practice solutions as follows. 
Several of these pitfalls are automated into custom PMD/Sonar jPinpoint-rules.

Improper back-end interaction / remote service calls
----------------------------------------------------

The most important factor of application performance is the number of back-end service calls. Therefore, minimizing this number and avoiding identical calls must have top priority. Downside of the use of ‘independent’ front-end elements is the difficulty to avoid identical service requests from multiple frontend elements on the same page or on different pages. This can be both for the same application/ear as well as for separate applications/ears.

#### IBI01

**Observation: Multiple service requests and cache requests are executed for the same actual back-end service request and multiple, possibly slightly different, cache entries occupy the cache(s).**  
**Problem:** Response times are higher than needed, load on the back-end is higher than needed and there is more data in the cache(s) than needed, to result in less efficiency: more load on the cache/database or more overload to disk of the in-memory cache.  
**Solution 1:** If the service is called on most front-end elements, this could be solved on application level.  
**Solution 2:** The integration layer could be shared on source level between applications/ears, such that the same service calls and cache and cache entries are used.

#### IBI02

**Observation: Back-end calls are executed in a loop without a hardcoded stop criterion, e.g. by a 'there is more' flag returned from the back-end.**  
**Problem:** If the back-end has a failure and returns a 'there is more' flag incorrectly, or the two parties think differently about this flag, this results in back-end responses being accumulated in the Java heap during the loop. After a while this will result in an out-of-memory situation.  
**Solution:** Introduce a sensible hard-coded limit to the number of iterations. This is better than a limit by a property, since properties may be configured incorrectly, and go unnoticed if they are not used in the happy flow.

#### IBI03

**Observation: The HTTP connection manager uses the default maximum connections per route.**  
**Problem:** The default maximum connections per route is by default set to 2. This throttles the number of connections to the back-end usually much more than required, 
such that many requests have to wait for a connection and response times get much higher than needed.
The connection timeout is usually set to a low number (e.g. 300 ms), in that case connection timeout exceptions will occur.  
**Solution:** Set the default maximum connections per route to a higher number, e.g. 20. 
Also increase the Max Total to at least the DefaultMaxPerRoute or a multiple in case of multiple routes.  
**Example** for only one host. Can be done in two ways:

* via the `PoolingHttpClientConnectionManager`:
```java
PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
connMgr.setMaxTotal(maxConnections);
connMgr.setDefaultMaxPerRoute(maxConnections);

CloseableHttpClient httpClient = HttpClients.custom()
    .setConnectionManager(connMgr)
    .build();
```
* or directly on the client via e.g. `HttpClients` builder:
```java
CloseableHttpClient httpClient = HttpClients.custom()
    .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
    .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
    .build();
```
or for asynchronous connections using nio:

```java
private HttpAsyncClientBuilder createHttpClientConfigCallback(final HttpAsyncClientBuilder clientBuilder) {
    clientBuilder
        .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
        .setMaxConnTotal(MAX_CONNECTIONS_TOTAL);
```

Note that class PoolingClientConnectionManager and several others are deprecated and [PoolingHttpClientConnectionManager](http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/conn/PoolingHttpClientConnectionManager.html) is the one to use for synchronous calls and NHttpClientConnectionManager (e.g. by HttpAsyncClientBuilder) for asynchronous calls.

**Rule name:** HttpClientBuilderWithoutPoolSize.

#### IBI04
Moved to [Improper asychrony category](#ia06)

#### IBI05

**Observation: Apache DefaultHttpClient is used with multiple threads.**  
**Problem:** This HttpClient can only handle one thread on one connection and will throw an IllegalStateException in case a second thread tries to connect when the first is using the connection.  
**Solution:** Use a PoolingHttpConnectionManager, see Apache doc: [connection management](http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d5e639).

#### IBI06

**Observation: Deprecated or thread-unsafe HTTP connectors are used.**  
**Problem:** Several HTTP client connection managers are thread-unsafe which may cause session data mix-up or have other issues for which they were made deprecated. Highest risk of session data mixup: SimpleHttpConnectionManager.  
Other deprecated ones to remove: SimpleHttpConnectionManager, ClientConnectionManager, PoolingClientConnectionManager, ThreadSafeClientConnManager, SingleClientConnManager, DefaultHttpClient, SystemDefaultHttpClient, ClientConnectionManager, MultiThreadedHttpConnectionManager, HttpClient constructor.  
**Solution:** Use org.apache.http.impl.conn.PoolingHttpClientConnectionManager and org.apache.http.impl.client.HttpClientBuilder., see Apache doc: [connection management](http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d5e639).  
**Rule name:** AvoidDeprecatedHttpConnectors.

#### IBI07

**Observation: The HTTP client has enabled connection state tracking while using TLS.**  
**Problem:** HTTP connections between services are mostly stateless, meaning there is no specific user identity or security context for each session. Exceptions are `NTLM` authenticated connections and SSL/TLS connections with client certificate authentication. If HttpClients have enabled connection state tracking which is the default, established TLS connections will not be reused because it is assumed that the user identity or security context may differ. Then performance will suffer due to a full TLS handshake for each request.  
**Solution:** HttpClients should disable connection state tracking in order to reuse TLS connections, since service calls for one pool have the same user identity/security context for all sessions. Connect to one back-end only from one pool. See [Apache Http client tutorial](http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html#stateful_conn)

Example (correct):

```java
    @Bean(name = "saveObjectReferencesHttpClient")
    public HttpClient httpClient(@Qualifier("saveObjectReferencesConnectionManager") PoolingHttpClientConnectionManager connectionManager,
                                 SaveObjectReferenceConnectionProperties connectionProperties) {
        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfigUtils.builder(connectionProperties))
                .disableConnectionState() // allow re-use of mutual authenticated TLS connections
                .build();
    }
```

#### IBI08

**Observation: HttpClient is used instead of ClosableHttpClient.**  
**Problem:** if HttpClient connections are not closed properly when needed, resources are not released and connections may not (or not quick enough) become available from the pool.  
**Solution:** Use ClosableHttpClient to allow for invoking close on it to properly close the connection. Or use HttpComponentsClientHttpRequestFactory(httpClient) and let it manage closing.  
**Rule name:** UseCloseableForHttpClient  
**Example:**  
```java
  void bad() {
        HttpClient httpClient = HttpClientBuilder.create()
                .disableConnectionState().build();
    }

    void good() {
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .disableConnectionState().build();
    }
    void good2() {
        HttpClient httpClient = HttpClientBuilder.create().disableConnectionState().build();
        ClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
    }    
```
For more information, see [httpclient-connection-management](https://www.baeldung.com/httpclient-connection-management)   

#### IBI09  
**Observation: HttpClient builder is used with a ConnectionManager and setMaxConnTotal and/or setMaxConnPerRoute is called on the client.**  
**Problem:** If you use setConnectionManager, the connection pool must be configured on that Connection Manager. Pool settings on the client are ignored and lost. This is confusing and a source of errors. 
**Solution:** HttpClients should either:
1. use setConnectionManager and *only* configure the pool on the connection manager or 
2. not use a ConnectionManager and configure the connection pool on the client.   

So, if you use a Connection Manager, remove the setMaxConnTotal and setMaxConnPerRoute calls on the HttpClient.  
**Rule name:** HttpClientBuilderPoolSettingsIgnored  
**Example:**  
```java
        return HttpClientBuilder.create()
                .setConnectionManager(connMgr)
                .setMaxConnPerRoute(MAX_CONNECTIONS_TOTAL) // bad, ignored
                .build();

        return HttpClientBuilder.create() // good
                .setMaxConnPerRoute(MAX_CONNECTIONS_TOTAL)
                .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
                .build();

        return HttpClientBuilder.create() // good
                .setConnectionManager(connMgr)
                .build();
```

#### IBI10

**Observation: Apache HttpClient uses the default timeouts.**  
**Problem:** The default timeouts of Apache HttpClient are probably too high, if not infinite. 
This has impact on the stability of the app if too many threads are blocked waiting for a connection or a response.  
**Solution:** Always set the timeouts explicitly. Use best practice values: Read/socket timeout ~4000 ms (note: 
depends largely on use case and expected latency of remote calls),
Connect timeout ~250 ms, Connection Manager/Request timeout = connect timeout + slack 250+100 = ~350 ms.  
**Rule name:** HttpClientBuilderWithoutTimeouts   
**Example:**
```java
RequestConfig requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(350)
        .setConnectTimeout(250)
        .setSocketTimeout(4000)
        .build(); // good, all timeouts set

return HttpClientBuilder.create()
        .setDefaultRequestConfig(requestConfig) // good, all timeouts set, if missing > default timeouts?
        .build();

return HttpClientBuilder.create() // bad, no default HttpClient set with explicit timeouts set
        .setConnectionTimeToLive(180, TimeUnit.SECONDS)
        .build();
```

#### IBI11

**Observation: Netflix Hystrix is used for e.g. circuit breaker.**  
**Problem:** Hystrix is not actively maintained anymore.&#13;  
**Solution:** Netflix recommends to use open source alternatives like resilience4j.   
**Rule name:** AvoidDeprecatedHystrix   
**See:** [DZone article on Hystrix alternatives](https://dzone.com/articles/resilience4j-and-sentinel-two-open-source-alternat) 
and [resilience4j on GitHub](https://github.com/resilience4j/resilience4j#fault-tolerance-library-designed-for-functional-programming)

#### IBI12

**Observation: An HttpClient is created and combined with request-response.**  
**Problem:** Apache HttpClient with its connection pool and timeouts should be setup once and then used for many requests. 
It is quite expensive to create and can only provide the benefits of pooling when reused in all requests for that connection.  
**Solution:** Create/build HttpClient with proper connection pooling and timeouts once, and then use it for requests.  
**Rule name:** AvoidRecreatingHttpClient   
**Example:**
```java
    ResponseEntity<Object> connectBad(Object req) {
        HttpEntity<Object> requestEntity = new HttpEntity<>(req);
        HttpClient httpClient = HttpClientBuilder.create().setMaxConnPerRoute(10).build(); // bad
        return remoteCall(httpClient, requestEntity);
    }
```

#### IBI13
**Observation: A Retry mechanism is used in more than one location in a call chain. This can be by Resilience4j Retry or @Retry, or Spring @Retryable.**  
**Problem:** Multiple Retry locations in a call chain multiply the number of calls. For 2x retry on 3 locations (service calls) in a chain calling a system which is just recovering,
results in 3 x 3 x 3 = 27 calls instead of 1. This may cause it not being able to restart.  

<img src="https://user-images.githubusercontent.com/24591067/126504177-5ecbe2a6-0b99-4c9a-90fe-7ec6d668be4f.png" alt="retry-chain-overload" width="50%"/>.  

**Solution:** Have the retry mechanism in one location in the chain only, recommended only the one closest to the user. 
This could be achieved with passing a token/header which indicates whether retrying is already managed.   
**Rule name:** RetryCanCauseOverload   
**See:** [AWS Timeouts, retries, and backoff with jitter](https://d1.awsstatic.com/builderslibrary/pdfs/timeouts-retries-and-backoff-with-jitter.pdf)  
**Example:**
```java
import io.github.resilience4j.retry.annotation.Retry;

@Retry(name = "some-service") // inform
public class Foo {
    public Response callSomeService() {
        //...and someService does a Retry for a call to the next service
    }
}
```

#### IBI14
**Observation: When troubleshooting [Reactor](https://projectreactor.io/), [Blockhound](https://github.com/reactor/BlockHound) can be used to find blocking calls. It needs proper stack traces which can be achieved by Hooks.onOperatorDebug().**  
**Problem:** Retaining full stack traces by reactor.core.publisher.Hooks.onOperatorDebug() uses StackWalker, this can have much CPU overhead.  
**Solution:** Remove Hooks.onOperatorDebug() when not debugging Reactor for blocking calls.   
**Note:** Enabling the system property reactor.trace.operatorStacktrace (default=false) is another way to retain the stack traces with the same problem.   
**Rule name:** AvoidReactorDebugOverhead   
**See:** 1. [JDK 11 performance problem](https://bugs.openjdk.java.net/browse/JDK-8222942) and 2. [100% CPU usage of Log4j2](https://www.programmersought.com/article/63006298939/)  
**Example:**
```java
import reactor.core.publisher.Hooks;

public class Foo {
    public void bar() {
        Hooks.onOperatorDebug(); //bad
    }
}
```

#### IBI15
**Observation: For Apache HttpComponentsClientHttpRequestFactory, both the constructor which takes a HttpClient and setHttpClient method are called.**   
**Problem:** If you use both on the same factory, you discard all configuration done on the one provided in the constructor because it is replaced by the one provided to the setter.   
**Solution:** Don't use both on the same factory, provide the HttpClient only once to the factory.   
**Rule name:** AvoidDiscardingHttpClientConfig    
**Example:**
```java
class Bad {
    ClientHttpRequestFactory getFactory(HttpClientConfiguration config) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create()
                .setMaxConnTotal(config.getMaxTotalConnections())
                .setMaxConnPerRoute(config.getMaxConnPerRoute())
                .build());

        factory.setHttpClient(createHttpClient(config)); //bad
        return factory;
    }
}

class Good {
    ClientHttpRequestFactory getFactory(HttpClientConfiguration config) {
        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory(createFullyConfiguredHttpClient(config));
        return factory;
    }
}
```

#### IBI16
**Observation: HttpHost constructor with only one argument is used, meant just for hostname.**   
**Problem:** the HttpHost constructor with one argument must only be provided with a host name, the default port 80 and protocol http are implied.
The mistake of providing a URL and assuming it will be parsed into hostname, port and protocol is easily made.
When this HttpHost is then used for a route and stored socketConfig for, port 80 is added for the host and the socketConfig is stored with the wrong key and will not be used.
It is typically difficult to find out if the config is actually used. Note that [http-client-monitor](http://github.com/jborgers/http-client-monitor) helps here.   
**Solution:** Use the HttpHost constructor with 2 (including port) or preferably 3 arguments (including port and protocol).   
**Rule name:** AvoidHttpHostOneArgumentConstructor    
**Example:**
```java
import org.apache.http.HttpHost;

class Foo {
  private static final String URL = "localhost:8080";
  private static final HttpHost hostBad1 = new HttpHost("localhost:8080"); // bad

  void bar() {
    HttpHost hostBad2 = new HttpHost(URL);//bad
    HttpHost hostGood1 = new HttpHost("localhost", 8080, "http"); //good
  }
}
```

#### IBI17
**Observation: Spring RestTemplate uses a ClientHttpRequestFactorySupplier as a @Bean to configure connection pooling and timeouts.**   
**Problem:** The org.springframework.boot.web.client.ClientHttpRequestFactorySupplier may return a HttpComponentsClientHttpRequestFactory which you supply as @Bean, however,
this can silently go wrong and e.g. an unconfigured SimpleClientHttpRequestFactory can be returned.
Default pool size and timeouts will be used, possibly resulting in very slow connection use.   
**Solution:** Provide you own supplier with explicit pool sizing and timeouts by a class implementing Supplier.   
**Rule name:** AvoidClientHttpRequestFactorySupplier    
**Example:**
```java
import org.springframework.boot.web.client.ClientHttpRequestFactorySupplier;
import org.springframework.web.client.RestTemplate;

class Bad {
  void bad() {
    RestTemplate restTemplate = new RestTemplateBuilder(rt -> rt.getInterceptors()
            .add((request, body, execution) -> {
              request.getHeaders().add("SomeKey", someKey);
              return execution.execute(request, body);
            }))
            .requestFactory(new ClientHttpRequestFactorySupplier()) // bad
            .uriTemplateHandler(defaultUriBuilderFactory)
            .build();
    return restTemplate;
  }
}

class MyClientHttpRequestFactorySupplier implements Supplier<ClientHttpRequestFactory> {

  public ClientHttpRequestFactory get() {
    PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
    poolingHttpClientConnectionManager.setDefaultMaxPerRoute(MAX_CONN_PER_ROUTE);
    poolingHttpClientConnectionManager.setMaxTotal(MAX_CONN_TOTAL);

    CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(poolingHttpClientConnectionManager)
            .disableConnectionState()
            .build();
    return new HttpComponentsClientHttpRequestFactory(httpClient);
  }
}
```
  and use it to replace the bad line in Bad example:
```
        .requestFactory(new MyClientHttpRequestFactorySupplier()) // good
```

#### IBI18
**Observation: Spring BufferingClientHttpRequestFactory is used.**   
**Problem:** org.springframework.http.client.BufferingClientHttpRequestFactory buffers all incoming and outgoing streams fully in memory which may result in high memory usage.   
**Solution:** Avoid multiple reads of the response body so it is not needed.   
**Rule name:** BufferingClientHttpRequestFactoryIsMemoryGreedy    
**Example:**
```java
import org.springframework.http.client.*;
import org.springframework.web.client.RestTemplate;

public class Foo {
  public RestTemplate createMemoryGreedyRestTemplate(HttpClientConfiguration httpClientConfiguration) {
    ClientHttpRequestFactory factory = getClientHttpRequestFactory(httpClientConfiguration);
    return new RestTemplate(new BufferingClientHttpRequestFactory(factory)); // bad
  }

  public RestTemplate createStreamTroughRestTemplate(HttpClientConfiguration httpClientConfiguration) {
    ClientHttpRequestFactory factory = getClientHttpRequestFactory(httpClientConfiguration);
    return new RestTemplate(factory); // good
  }
}
```

#### IBI19
**Observation: A primitive variable identifier or @Value member ends with time, duration or similar: time unit is missing.**   
**Problem:** Time unit like hours, seconds, milliseconds is not specified and may be assumed differently by readers.
Different assumptions will lead to errors or hidden problems like ineffective caches.   
**Solution:** Specify the time unit in the identifier, like connectTimeoutMillis.   
**Rule name:** AvoidTimeUnitConfusion    
**Example:**
```java
class RetrieveCache {
    @Autowired
    public RetrieveCache(final @Value("${cache.expiryTime}") long timeToLive) { // 2x bad
    }
    @Autowired
    public RetrieveCache(final @Value("${cache.expiryTimeMillis}") long timeToLiveMillis){ // 2x good
    }
}
```

#### IBI20
**Observation: Apache HttpClient RequestConfig connectionRequestTimeout and connectTimeout have values which are typically too high.**   
**Problem:** org.apache.http.client.config.RequestConfig is used with connectionRequestTimeout and connectTimeout values > 500 milli seconds.

1. connectTimeout is for establishing a (secure) connection which should be quick, say below 200 ms.   
2. connectionRequestTimeout is for requesting a connection from the connection manager, which should be almost as quick, say below 250 ms.   

If timeouts are long, requests will wait long for an unavailable service and cause high thread usage and possibly overload.   
**Solution:** Set connectTimeout and connectionRequestTimeout to values based on network tests, for instance 200 ms and 250 ms. respectively.
**Rule name:** HttpClientImproperConnectionTimeouts    
**Example:**
```java
import org.apache.http.client.config.RequestConfig;

public class HttpClientStuff {
  private static final int CONNECTION_TIMEOUTMILLIS = 1000; // bad // timeout until a connection is established
  private static final int CONNECTIONREQUEST_TIMEOUTMILLIS = 5000; // bad // timeout when requesting a connection from the connection manager
  private static final int SOCKET_TIMEOUTMILLIS = 5000; // timeout of waiting for data

  public RequestConfig requestConfigWithTimeouts() {
    RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(CONNECTIONREQUEST_TIMEOUTMILLIS)
            .setConnectTimeout(CONNECTION_TIMEOUTMILLIS)
            .setSocketTimeout(SOCKET_TIMEOUTMILLIS)
            .build();
    return requestConfig;
  }
}
```

#### IBI21
**Observation: The default http client of Feign is used, which is java.net.HttpURLConnection.**   
**Problem:** java.net.HttpURLConnection does *not* reuse connections when using (mutual) TLS, this causes connection handshake overhead: extra CPU and latency.   
**Solution:** Use an http client with Feign that has connection reuse for (mutual) TLS connection. For example, use Apache HttpClient 4 with proper connection pool settings (see IBI03, IBI07 and IBI10).   
**Rule name:** DefaultFeignClientWithoutTLSConnectionReuse   
**Example:**
```java
class FeignConfig {

    @Bean
    public Feign.Builder feignBuilderBad() {
      Client feignClient = new Client.Default(setupSSLContextForMutualTLS().getSocketFactory(), new DefaultHostnameVerifier()); // bad
      return Feign.builder().retryer(Retryer.NEVER_RETRY).client(feignClient);
    }
    
    @Bean
    public Feign.Builder feignBuilderGood(CloseableHttpClient client) {
      Client feignClient = new ApacheHttpClient(client); // good: wrap Apache HttpClient with connection pool enabled
      return Feign.builder().retryer(Retryer.NEVER_RETRY).client(feignClient);
    }
}
```



Improper asynchrony 
-------------------
This categry could be seen as a subcategory of the previous category. However, above mostly deals with remote connections, asynchony mostly deals with threading and parallelism.
We assume asynchronous calls are typically made to remote services. 

#### IA01

**Observation: A Spring @Async thread pool has default sizes, or is not sized based on measurements or best practices**      
**Problem:** Spring @Async uses a ThreadPoolTaskExecutor. This Executor and Java's ThreadPoolExecutor have a setCorePoolSize and setMaxPoolSize method. 
Spring's Executor has default core size = 1 and max size = unlimited, which are almost never appropriate, it uses too many thread resources when the called service is slow or unavailable. 
Setting values to e.g. 100 is likely too high, causing too much thread resources usage and scheduling/context switching overhead. 
Additionally, if remote connections use a connection pool and the number of threads is higher than the number of connections in the pool, threads will have to wait for a connection.
Setting values to 1 allows for serial execution only, while one typically wants to utilize parallelism.  
**Solution:** The max number of threads should be based on request rate (tps) and response times of a healthy called service in peak load situations, such that all requests can be handled without queueing and no more threads are used than necessary.
Make core size N smaller than max size to reduce overhead and resource usage in the common case when N threads is enough. E.g. core size = 10, max size = 30.
Note that the pool will only grow bigger than core size when the queue limit is reached.  
**Example:** 
```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(10); // based on measurements
executor.setMaxPoolSize(30); // based on measurements
executor.setQueueCapacity(10); // see next item (IA02)
```

#### IA02

**Observation: A Spring @Async thread pool *queue* has default capacity, or is not sized based on measurements or best practices**      
**Problem:** Spring @Async uses a ThreadPoolTaskExecutor. This Executor and Java's ThreadPoolExecutor have a setQueueCapacity method.
Default capacity = unlimited, which is almost never appropriate, it uses too much memory when the called service is slow or unavailable.
Setting the capacity to e.g. 100 is likely too high, causing too much response time delay.
Setting the capacity to 0 allows for no full-function handling of spike load or hiccups in the called chain, beyond max pool size utilization.  
**Solution:** The queue capacity should be based on request rate (tps) and response times of a healthy called service in peak load situations, and to accommodate for some level of spike load and hiccups in the called chain.
Make queue capacity equal to pool core size to have at most 1 request waiting for each thread doing a service call.   
**Example:**
```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(10); // based on measurements
executor.setMaxPoolSize(30); // based on measurements
executor.setQueueCapacity(10); // based on measurements
```

#### IA03

**Observation: A Spring @Async thread pool is used for more than one async remote service call**      
**Problem:** Long response times or unavailability of one remote service will eat up threads of the pool so the other service will get no threads to do the remote call.  
**Solution:** If this is undesirable, use a separate pool for each async service call. (Bulkhead pattern)

#### IA04

**Observation: A Spring ThreadPoolTaskExecutor is not monitored**      
**Problem:** If you don't see the usage of threads and the queue under load, you cannot size these properly.    
**Solution:** You need to measure to know how to size. 
ThreadPoolTaskExecutor can be [exposed as mbean](https://stackoverflow.com/questions/53519810/exposing-threadpooltaskexecutor-as-mbean-in-jmx) and monitored by VisualVM or other mbean browser, 
or monitored by using micrometer ([example in afterburner](https://github.com/stokpop/afterburner/blob/4bd9f32123bd9bd0a0c4fedace06f72f6f9e4174/afterburner-java/src/main/java/nl/stokpop/afterburner/config/AfterburnerAsyncConfig.java#L52)).
Make sure to give the threads a proper name, this makes them easier to recognize in thread dumps, heap dumps and profiling.

#### IA05
**Observation: A Spring ThreadPoolTaskExecutor queue capacity is not configured**      
**Problem:** The org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor has a default queue capacity which is unlimited which can lead to an out of memory situation.      
**Solution:** Call setQueueCapacity, for instance with a value equal to CorePoolSize.
Note that the pool will only grow beyond CorePoolSize up to MaxPoolSize when the queue is full.  
**Rule name:** SetQueueCapacityForTaskExecutor   
**Example:**
```java
   private ThreadPoolTaskExecutor createExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setQueueCapacity(10); // bad if missing
        executor.setMaxPoolSize(20);
        executor.initialize();
        return executor;
   }
```
#### IA06
**Observation: A blocking asynchronous call future.get() without time-out is used.**  
**Problem:** Stalls indefinitely in case of hanging thread(s) and consumes a thread. Threads may get stuck in database, a remote system, because of a network hiccup, in error or other exceptional situations.  
**Solution:** Use the version with timeout and handle a timeout situation.
**Rule name:** AvoidFutureGetWithoutTimeout
**Example:**   
```java
future.get(long timeout, TimeUnit unit)
```

See: [Monix Best Practice](https://monix.io/docs/2x/best-practices/blocking.html)

#### IA07
**Observation: A blocking future.join() is called without a timeout.**  
**Problem:** It cannot deal with hanging threads. Threads may get stuck in database, a remote system, because of a network hiccup, in error or other exceptional situations.  
**Solution:** Provide a timeout before the join and handle the timeout. For example a future.get(timeout, unit), a orTimeout() or a completeOnTimeout(). You may want to use CompletableFuture.allOf().   
**Rule name:** AvoidFutureJoinWithoutTimeout   
**Example:**
```java
class Foo {
  private List<Order> getOrdersBad(List<CompletableFuture<Order>> getOrdersFutures) {

    List<Order> orders = getOrdersFutures.stream()
            .map(CompletableFuture::join) // bad, NO timeout provided above
            .collect(Collectors.toList());
    return orders;
  }

  private List<Order> getOrdersGood(List<CompletableFuture<Order>> getOrdersFutures) {
    // added to deal with timeout
    CompletableFuture<Void> allFuturesResult = CompletableFuture.allOf(getOrdersFutures.toArray(new CompletableFuture[getOrdersFutures.size()]));
    try {
      allFuturesResult.get(5L, TimeUnit.SECONDS); // good
    } catch (Exception e) { // should make explicit Exceptions
      //log error
    }
    List<Order> orders = getOrdersFutures.stream()
            .filter(future -> future.isDone() && !future.isCompletedExceptionally()) // keep only the ones completed -- added to deal with timeout
            .map(CompletableFuture::join) // good, timeout provided above
            .collect(Collectors.toList());
    return orders;
  }
}
```
See: [completablefuture-and-timeout](https://stackoverflow.com/questions/60419311/completablefuture-does-not-complete-on-timeout) and  [completablefuture-allof-and-errors](https://kalpads.medium.com/fantastic-completablefuture-allof-and-how-to-handle-errors-27e8a97144a0)

#### IA08
**Observation: Future.supplyAsync is used for remote calls and it uses the common pool (the default).**  
**Problem:** The common pool is meant for processing of in-memory data. The number of threads in the common pool is equal to the number of CPU's,
which is suitable to keep all CPU's busy with in-memory processing.
For I/O, however, this number is typically not suitable because relatively much time is spent waiting for the response and not in CPU.
This likely exhausts the common pool for some time thereby blocking all other use of the common pool. 
The common pool must *not* be used for blocking calls, see [Be Aware of ForkJoinPool#commonPool()](https://dzone.com/articles/be-aware-of-forkjoinpoolcommonpo)   
**Solution:** A separate, properly sized, pool of threads (an Executor) should be used for the async calls.   
**Rule name:** AvoidCommonPoolForFutureAsync   
**Example:**
```java
public class Foo {
  private final ExecutorService asyncPool = Executors.newFixedThreadPool(8);

  void bad() {
    CompletableFuture<Pair<String, Boolean>>[] futures = accounts.stream()
            .map(account -> CompletableFuture.supplyAsync(() -> isAccountBlocked(account))) // bad
            .toArray(CompletableFuture[]::new);
  }

  void good() {
    CompletableFuture<Pair<String, Boolean>>[] futures = accounts.stream()
            .map(account -> CompletableFuture.supplyAsync(() -> isAccountBlocked(account), asyncPool)) // good
            .toArray(CompletableFuture[]::new);
  }
}
```

#### IA09
**Observation: parallelStream which uses the ForkJoinPool::commonPool is used for blocking (I/O, remote) calls.**  
**Problem:** The common pool is meant for processing of in-memory data. The number of threads in the common pool is equal to the number of CPU's - 1, 
which is suitable to keep all CPU's busy with in-memory processing.
For I/O or other blocking calls, however, this number is typically not suitable because relatively much time is spent waiting for the response and not in CPU.
This likely exhausts the common pool for some time thereby blocking all other use of the common pool.
The common pool must *not* be used for blocking calls, see [Be Aware of ForkJoinPool#commonPool()](https://dzone.com/articles/be-aware-of-forkjoinpoolcommonpo)   
**Solution:** A separate, properly sized, pool of threads (an Executor or ForkJoinPool) should be used for the async calls.
**Rule name:** AvoidCommonPoolForBlockingCalls   
**Example:**
```java
public class Foo {
  final List<String> list = new ArrayList();
  final ForkJoinPool myFjPool = new ForkJoinPool(10);
  final ExecutorService myExePool = Executors.newFixedThreadPool(10);

  void bad1() {
    list.parallelStream().forEach(elem -> storeDataRemoteCall(elem));
  }

  void good1() {
    CompletableFuture[] futures = list.stream().map(elem -> CompletableFuture.supplyAsync(() -> storeDataRemoteCall(elem), myExePool))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(futures).get(10, TimeUnit.MILLISECONDS);
  }

  void good2() throws ExecutionException, InterruptedException {
    myFjPool.submit(() ->
            list.parallelStream().forEach(elem -> storeDataRemoteCall(elem))
    ).get();
  }

  String storeDataRemoteCall(String elem) {
    // do remote call, blocking. We don't use the returned value.
    RestTemplate tmpl;
    return "";
  }
}
```
See for the example good2: [custom-thread-pool-in-parallel-stream](https://stackoverflow.com/questions/21163108/custom-thread-pool-in-java-8-parallel-stream/34930831#34930831).   

#### IA10
**Observation: An Axual (Kafka) producer is created for each method call.**  
**Problem:** each Producer takes threads and memory. If you create it in each method call, and call this frequently, it will result in an explosion of threads and memory used and lead to Out Of Memory Error.   
**Solution:** Since the Axual Producer is thread-safe, it should be shared e.g. from a static field. Note that the AxualClient is not thread-safe.   
**Rule name:** AxualProducerCreatedForEachMethodCall   
**Example:**
```java
import io.axual.client.producer.Producer;

public class AxualProducerBad {
  public void publishToEventStream() {
    Producer<String, String> producer = axualClient.buildProducer(producerConfig); // bad
    producer.produce(msg);
  }
}

class AxualProducerGood1{
  private static final Producer<String, String> producer = AxualClient.buildProducer(producerConfig);
}

@Configuration
class AxualProducerGood2{
  public Producer<String, String> axualProducer() {
    Producer<String, String> producer = axualClient.buildProducer(producerConfig);
    return producer;
  }
}
```

#### IA11
**Observation: parallelStream which uses the ForkJoinPool::commonPool is used.**  
**Problem:** Collection.parallelStream() and .stream().parallel() use the common pool, with #threads = #CPUs - 1. 
It is designed to distribute much CPU work over the cores. 
It is *not* meant for remote calls nor other blocking calls.
In addition, parallelizing has overhead and risks, should only be used for much pure CPU processing.
The common pool must *not* be used for blocking calls, see [Be Aware of ForkJoinPool#commonPool()](https://dzone.com/articles/be-aware-of-forkjoinpoolcommonpo)   
**Solution:** For remote/blocking calls: Use a dedicated thread pool with enough threads to get proper parallelism independent of the number of cores.
For pure CPU processing: use ordinary sequential streaming unless the work takes more than about 0,1 ms in sequential form and proves to be faster with parallelization. 
So only for large collections and much processing without having to wait.   (jpinpoint-rules)</description>   
**Rule name:** AvoidParallelStreamWithCommonPool   
**Example:**
```java
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class Foo {
  final Map<String, String> map = new HashMap();
  final List<String> list = new ArrayList();
  final List<String> hugeList = new ArrayList(); //1000+ elements
  final ForkJoinPool myFjPool = new ForkJoinPool(10);
  final ExecutorService myExePool = Executors.newFixedThreadPool(10);

  void bad1() {
    list.parallelStream().forEach(elem -> someCall(elem)); //bad
  }
  void bad2() {
    map.entrySet().parallel().stream().forEach(entry -> someCall(entry.getValue())); //bad
  }
  void exceptionalProperUse() {
    hugeList.parallelStream().forEach(elem -> heavyCalculations(elem)); //flagged but may be good, should suppress when proven to be faster than sequential form
  }

  void good1() {
    CompletableFuture[] futures = list.stream().map(elem -> CompletableFuture.supplyAsync(() -> someCall(elem), myExePool))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(futures).get(3, TimeUnit.SECONDS);
  }
  void good2() throws ExecutionException, InterruptedException {
    myFjPool.submit(() ->
            list.parallelStream().forEach(elem -> someCall(elem))
    ).get();
  }

  String someCall(String elem) {
    // do some call, don't know if remote or blocking. We don't use the returned value.
    return "";
  }

  String heavyCalculations(String elem) {
    // calculate a lot
    return "";
  }
}
```
See for the example good2: [custom-thread-pool-in-parallel-stream](https://stackoverflow.com/questions/21163108/custom-thread-pool-in-java-8-parallel-stream/34930831#34930831).

Improper caching  
-------------------

See our [presentation on Java Performance Pitfall: improper caching.](https://youtu.be/IOWz67wSAIQ?list=PL9rzqHHCiIVQr27ZsP0_r8eOgQMTbhJfF)

#### IC01

**Observation: Default cache key generation with toString is used.** DefaultServiceCacheConfiguration / DefaultCacheKeyGenerator defines the key used for caching as the class name with concatenated the toString() called on all method arguments.  
**Problems:**

*   This is a problem especially for non-String arguments
*   toString method is meant to return a human readable representation of the object, not a part of a cache key.
*   toString may not be defined on the argument and return the class name and a hash for its location on the heap, thereby returning the object identity in stead of equality. This typically results in no cache hits, only misses.
*   For a cache in the database: The resulting key may be too large to fit in the database column.
*   It is unclear from the source code what the key is and why it is defined like it is, the motivation. Proper javadoc or other cache key description is typically missing.
*   This default cache key definition is unclear, fragile and error prone.

**Solutions:**

*   Make all cache keys explicit and specific. Do _not_ use the default keys.
*   Do not use toString for a cache key, instead create an explicit, separate method to generate (part of) the cache key, named for example getKey:

```java
    public String getKey() {
       return userId;
    }
```    

*   Explain design decisions and conditions around the key definition in a document and/or in javadoc.
*   The DefaultCacheKeyGenerator must _not_ be used anymore and should be deprecated. To be changed in ServiceCache.
*   An efficient implementation would be a CompositeKey composed of all arguments to use as key in Ehcache Element:

```java
    public class CompositeKey {
       private String userKey;
       private String siteKey;
       public CompositeKey(User user, Site site) {
          userKey = user.getKey();
          siteKey = site.getKey();
       }
       public boolean equals(Object other) {
          return (other != null && other instanceof CompositeKey 
                 && ((CompositeKey)other).userKey.equals(this.userKey)
                 && ((CompositeKey)other).siteKey.equals(this.siteKey));
       }
       public int hashCode() {
          // similar to equals, using hashCode
       }
    }
```

*   This is efficient because it prevents String concatenation of the arguments when accessing the cache.
*   Actually Spring's SimpleKey is a more generic solution, see IC13.

**Rule name:** partly implemented: AvoidDefaultCacheKeyGenerator.

#### IC02

**Observation: Caching takes place in the database.**  
**Problem:** ServiceCache uses by default caching in the database which is inferior to caching in memory. A database round trip for a SELECT may take 10-50 ms. A value not yet in the cache needs 3 update/inserts and may take 100+ ms, and even more with high load.  
**Solution:** Cache in memory with [ehcache](http://ehcache.org/). The total amount of cache data per JVM should be limited to a couple of 100 MBs, more needed can be overflowed to disk. It could be a good idea to create a solution on application level, such that central [monitoring and management](http://ehcache.org/documentation/operations/monitor) can take place, e.g. for monitoring size and hit ratio and clearing caches. 

#### IC03

**Observation: Some retrieve services are not cached.**  
**Problem:** Active “F5 users” or [DoS attacks](http://en.wikipedia.org/wiki/Denial-of-service_attack) could overload the back-end systems.  
**Solution:** All get services should be cached at least for a short time, to prevent overloading of back-end systems.

#### IC04

**Observation: Freemarker template caching is set at the default of 5 seconds.**  
**Problem:** Every 5 seconds, for every template file, the application hits the file system to find out if there is a new version of the template file. Since these don’t change in production, this is unnecessary load on the system.  
**Solution:** Increase the time to live for the freemarker template cache to a reasonable, much higher value, possibly indefinite. Project has configured the following to be in file initiate-direct-debit-portlet.xml:

```xml
 <property name="freemarkerSettings">
  <props>
    <prop key="template_update_delay">2500000</prop>
  </props>
</property>
```

Note that this delay is specified in **seconds.**

Or via configuration (e.g. in Spring):

```java
freemarker.template.Configuration.setTemplateUpdateDelayMilliseconds(2_500_000_000L);
```
  
#### IC05

**Observation: Cache configuration is not motivated.**  
**Problem:** Circumstances may change and make the cache configuration invalid, to be updated.  
**Solution:** Add a motivation for each configuration value. For example:

```xml
<cache maxEntriesLocalHeap="1" /> <!-- only one entry needed for all countries in one ArrayList-->
<cache timeToLiveSeconds="7200" /> <!-- business: change to country table needs to be live within 2 hours-->
```

#### IC06

**Observation: Cache elements are mutable.**  
**Problem:** A cache can be used by multiple threads, if elements are mutated in a thread-unsafe manner, concurrency issue will occur. Some caches store elements by-reference and others by-value, if cache implementation is changed, application behavior may change because of this.  
**Solution:** Make cache elements immutable/unmodifiable

#### IC07

**Observation: Cache elements are large domain objects.**  
**Problem:** Caches are meant to temporarily contain data / values and are separate from your application logic / behavior.  
**Solution:** Cache Serializable value objects used by your domain objects.

#### IC08

**Observation: Cache properties are not monitorable.**  
**Problem:** If we cannot see if the cache has hits, if the hit ratio is like we expect, we don't know if the cache is useful at all.  
**Solution:** Make all caches monitorable by exposing them as MBeans. Name the CacheManager. Ehcache provides those mbeans with statistics. Like:

```java
cacheManager.setName("Mobile App CacheManager");
...
final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
ManagementService.registerMBeans(cacheManager, mbeanServer, true, true, true, true);
LOG.info("MBean registration is done.");
```

In Spring config:

```xml
 <beans>
   <bean id="exporter" class="org.springframework.jmx.export.MBeanExporter" lazy-init="false">
       <property name="beans">
           <map>
               <entry key="bean:name=testBean1" value-ref="testBean"/>
           </map>
       </property>
   </bean>
   <bean id="testBean" class="org.springframework.jmx.JmxTestBean">
       <property name="name" value="TEST"/>
       <property name="age" value="100"/>
   </bean>
</beans>
```

#### IC09

**Observation: Cache keys are unnecessarily long.** For instance each key starts with com.company.department.subdepartment.tribe.topic.  
**Problem:** All keys together take up a substantial part of the heap. Often the keys take up more space than the values.  
**Solution:** Make all keys just long enough to be unique, do not have a part identical for all keys. Do not include the fully qualified class names.

#### IC10

**Observation: Cache keys are sums of hash codes of method arguments.**  
**Problem:** Hash codes are int-s which have a non-negligible chance to be equal for unequal objects. If two keys are unintentionally equal, session data will be mixed up.  
**Solution:** Never use keys based on hashCode-s.

#### IC11

**Observation: ehcache default cache configuration is used.**  
**Problem:** Default configuration is confusing: it only applies to programmatically created caches by addCache(). These caches can be created and used without explicit configuration, yet implicitly with the default configuration, which can be wrong. Caches need to be tuned individually.  
**Solution:** Remove the default cache configuration. This way, an error is generated if caches are created by name (programmatically) with no defaultCache loaded. This is clear and fail-fast. Configure each ehcache declaratively in ehcache.xml.

#### IC12

**Observation: a remote (cloud) cache like Redis is used without a local cache for elements typically accessed more than once, e.g. for static-ish data like countries, currencies**  
**Problem:** every access to the static data will take a remote call to the remote cache, serialization of data and possibly processing to an intermediate object like a Map to lookup e.g. a country. This means extra time is added the response time for the user.  
**Solution:** Add a local, in process, by reference cache for high-performance; in addition to a remote cache. Ehcache is the most used local, in-process cache. Add a local cache in case of high load and you expect a significant hit ratio (say >20%) on the local cache. The redis cache enables scaling up of the calling service, it reduces load on the back-end and the local cache reduces load on the redis cache.

#### IC13

**Observation: A non-overridden Object.toString may be called on a spring KeyGenerator.generate method parameter.**  
**Problem:** The non-overridden Object.toString returns a String representing the identity of the object. Because this is different for two objects with the same value, cache keys will be different and the cache will only have misses and no hits.  
**Solution:** return a SimpleKey composed of method and the params. Note: equals and hashCode must be properly implemented for each param.  
**Rule name:** AvoidIdentityCacheKeys   
**Example:**   
```java
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.util.StringUtils;
import org.springframework.cache.interceptor.SimpleKey;

class Bad implements KeyGenerator {
    public Object generate(Object target, Method method, Object... params) {
        List<Object> objArray = Arrays.asList(params);
        return target.getClass().getName() + "_" + method.getName() + "_"
                + StringUtils.arrayToDelimitedString(params, "_");  // bad, do not concatenate without casting
    }
}

class NotBad implements KeyGenerator {
    public Object generate(Object target, Method method, Object... params) {
        if (params.length != 1) {
            throw new IllegalArgumentException("KeyGenerator for GetProfileCache assumes 1 parameter 'profileId', found: " + params);
        }
        String profileId = (String)params[0];
	StringJoiner joiner = new StringJoiner("_");
	joiner.add(target.getClass().getSimpleName()).add(method.getName()).add(profileId);
        return joiner.toString();
    }
}

class Good implements KeyGenerator {
    @NotNull @Override
    public Object generate(Object target, Method method, Object... params) {
        return new SimpleKey(method, params);
    }
}
```
In the above, method is included to ensure to get different keys if this KeyGenerator is used on different classes/methods. 
The Method object implements equals and hashCode properly.

### IC14

**Observation: Default key generation is used with @Cacheable, no KeyGenerator is used.**  
**Problem:** With default key generation, an object of Spring's SimpleKey class is used and its value is composed of just the method parameter(s). It does not include the method (nor class), which is unclear and risky, it may cause cache data mix-up.   
**Solution:** Create a KeyGenerator and make it generate a unique key for the cache per cached value, by use of SimpleKey composed of method and the appropriate method parameters.   
**Rule name:** UseExplicitKeyGeneratorForCacheable    
**See:** [Spring 4.0 Caching](https://docs.spring.io/spring-framework/docs/4.0.x/spring-framework-reference/html/cache.html)   
**Example:**   
```java
import org.springframework.cache.annotation.Cacheable;
class Foo {

    @Cacheable(cacheNames = {"DATA"}, sync = true, keyGenerator = "cacheKeyGenerator")
    public Object getDataGood(String id) {
        return fetchFromBackend(id);
    }

    @Cacheable(value="DATA", sync = true) // bad, keyGenerator missing
    public Object getDataBad(String id) {
        return fetchFromBackend(id);
    }
}
```

### IC15

**Observation: Spring's SimpleKey creation for a cache lacks either the method or the method parameters.**  
**Problem:** Both method and the method parameters are needed to make a unique key for a cache entry. 
If one is missing, a key may be the same for different methods or different parameter values, which may cause cache data mix-up.   
**Solution:** Create a SimpleKey composed of both the method object and the params Object[].   
**Rule name:** AvoidSimpleKeyCollisions    
**See:** [Spring 4.0 Caching](https://docs.spring.io/spring-framework/docs/4.0.x/spring-framework-reference/html/cache.html)   
**Example:**
```java
import org.springframework.cache.interceptor.*;
import java.lang.reflect.Method;

class BadCacheKeyGenerator implements KeyGenerator {
  @Override
  public Object generate(Object target, Method method, Object... params) {
    return new SimpleKey(params); // bad
  }
}

class GoodCacheKeyGenerator implements KeyGenerator {
  @Override
  public Object generate(Object target, Method method, Object... params) {
    return new SimpleKey(method, params); // good
  }
}
class AlsoGoodCacheKeyGenerator implements KeyGenerator {
  @Override
  public Object generate(Object target, Method method, Object... params) {
    return SimpleKeyGenerator.generateKey(method, params); // good
  }
}
```
### IC16

**Observation: For a @Cachable (with keyGenerator) annotated method, the parameters that make up the cache key do not properly implement the required methods.**  
**Problem:** When 
1. concatenating or joining parameters in a KeyGenerator: they need to properly implement toString().
2. using SimpleKey (recommended): the parameters need to properly implement equals() and hashCode(). Failing to do so may lead to caching data mix-up.   

**Solution:** Create a SimpleKey composed of both the method object and the params Object[] and make sure the params properly implement equals and hashCode.   
**Rule name:** EnsureProperCacheableParams
**Note:** This rule is just informational, because it cannot actually check if it is implemented correctly or not.
**See:** [Spring 4.0 Caching](https://docs.spring.io/spring-framework/docs/4.0.x/spring-framework-reference/html/cache.html)   
**Example:**
```java
import org.springframework.cache.annotation.Cacheable;
import java.time.*;

class Foo {
  @Cacheable(value = "myCache", keyGenerator = "myGenerator")
  public String getDataGood(String str, LocalDate date) {
    return service.getData(input);
  }
  @Cacheable(value = "myCache", keyGenerator = "myGenerator") 
  public String getDataInform(MyObject input, String str, LocalDate date) { // inform
    return service.getData(input);
  }
}
class MyObject {
  String field;
  // equals, hashCode, toString missing
}
```

### IC17

**Observation: The class implementing Spring's KeyGenerator uses a generic name: CacheKeyGenerator.**   
**Problem:** It is unclear where this KeyGenerator should be used, for which cache and/or for which methods.
If used on the wrong caches or methods, it may lead to cache key mix-up and user data mix-up.   
**Solution:** Make the name specific so that it is clear where to apply this KeyGenerator in @Cacheable.   
**Rule name:** UseClearKeyGeneratorName   
**Example:**
```java
import org.springframework.cache.interceptor.KeyGenerator;

public class CacheKeyGenerator // bad, unclear name
        implements KeyGenerator { 
  public Object generate(Object target, Method method, Object... params) {
    // build key and return it
  }
}
```

Too much session usage
----------------------

The application often puts much data in the user session to keep state at the server side. The HTTP and Portlet sessions have a timeout of default 30 minutes, so if the user does not sign out explicitly, data keeps occupying heap space for a long time. Heap space is limited and shared by all sessions of all portlets on the JVM, so this data limits scalability. Additionally, garbage collection pause times increase with increasing heap usage. Since this application has to deal with many concurrent users, session data should be minimized. Portlets can set portlet session attributes explicitly, or Spring can put data in the session implicitly.

### Explicit session attributes

#### TMSU01

**Observation: Attributes are not removed from the session.** They are set in the session like:

```java
actionRequest.getPortletSession().setAttribute(DOWNLOAD_INFORMATION_FORM, downloadInformationForm);
```

and they are never removed or not removed in all flows, being happy or unhappy.

**Problem:** Attributes unnecessarily occupy heap space.  
**Solution:** Remove attributes from the session if not really needed, and as soon as possible in all controllable flows, including for instance technical exception cases, like as follows.

```java
actionRequest.getPortletSession().removeAttribute(DOWNLOAD_INFORMATION_FORM);
```

Spring has a PortletUtils to facilitate session attribute usage. It however does not provide a remove method. Removing can be achieved as in:

```java
PortletUtils.setSessionAttribute(request, SESSION_ATTRIBUTE_NAME, null);
```

Spring will than invoke removeAttribute on the portlet session.

It might also be an option to use render parameters instead, for example, in the action method:

```java
response.setRenderParameter("page", "initiatePayment");
```

**Rule name:** Available. Assumes removeAttribute occurs in same class as setAttribute. Also deals with Spring PortletUtils.

#### TMSU02

**Observation: Attributes contain too much data.** This is usually observed in a heap usage analysis. MemorySession objects of 5-20 kB are often observed.  
**Problem:** If they occupy more than 1 kB, we consider this as being too much. For motivation of the norm of 1 kB: at peak load there can be like 20.000 sessions times 25 portlets = 500.000 portlet sessions, times 1 kB = 500 MB which is really a maximum.  
**Solution:**

*   Remove attributes from the session if not really needed.
*   Remove attributes earlier in flows.
*   Reduce the amount of data in attributes: only store what is really needed.
*   Store the data more efficiently, see \[TMSU05\]

#### TMSU03

**Observation: Session attributes are accessed from various places.**  
**Problem:** Often, attribute names are inconsistent, access handling is inconsistent and/or a method clearSession only clear a few in stead of the all session attributes. Confusing, error prone, violates the [DRY principle](http://en.wikipedia.org/wiki/Don%27t_repeat_yourself).  
**Solution:** Centralize and encapsulate session access in one class, where all attribute names are defined.

#### TMSU04

**Observation: Attributes gotten from the session are not checked for null.**  
**Problem:** Out of sequence requests, e.g. refresh or misuse, should not result in a NullPointerException.  
**Solution:** Centralize session access in one class and check for null. Throw an IllegalStateException to fail fast on misuse and handle with a proper message to the user.

#### TMSU05

**Observation: Attributes are needed in the session, but they contain too much data.**  
**Problem:** Sessions occupy too much heap.  
**Solution:**

*   Remove collections dynamic growth overhead.
*   For instance, an ArrayList has the default initial capacity of 10 elements. This memory overhead can be avoided with use of the [constructor with capacity argument](http://docs.oracle.com/javase/6/docs/api/java/util/ArrayList.html#ArrayList%28int%29) or removed with [trimToSize()](http://docs.oracle.com/javase/6/docs/api/java/util/ArrayList.html#trimToSize%28%29).
*   A HashMap can be pre-sized with initial capacity and load factor, however it has more space overhead than ArrayList. For not too many elements (<50) consider to use an ArrayList instead. Operations like contains() looping over 50 elements is usually fast enough.
*   Use an array instead of an ArrayList, it has less space overhead.
*   Use primitives instead of wrappers, e.g. a long instead of a Long.
*   String.substring() always shares the backing char\[\] with the original String. This holds for both the IBM and the Hotspot JRE. If you create small String(s) out of a big String with substring (a rare case) and you only want to retain a small part of the whole char\[\] by small one(s), prevent retaining the original big char\[\] by:

```java
    leanString = new String(substring.toCharArray()); // only to be considered if substring is created by String.substring
```    

Note that for StringBuilder.substring this is not needed, since it does make a copy of the sub part of the char array. Also note we use a JVM option (\-Djava.lang.string.create.unique=true see [IBM APAR](http://www-01.ibm.com/support/docview.wss?uid=swg1IZ92080)) to achieve this, to remove waste by stringBuilder.toString(). This removes most String waste seen before on the IBM JVM. Defensively making Strings lean before putting them in a session or memory cache is therefore no longer needed. It actually introduces extra time overhead, therefore it is discouraged now.

*   For empty lists or other collections, use references to predefined (singleton) empty collections, like [Collections.emptyList()](http://docs.oracle.com/javase/6/docs/api/java/util/Collections.html#emptyList()).
*   Use [String.intern()](http://docs.oracle.com/javase/6/docs/api/java/lang/String.html#intern%28%29) to put frequently occurring strings in the single String pool in the JVM, like country, countryCode.
*   Use normalization: e.g. NaturalPerson references BranchImpl and only about 300 BranchImpls exist, these can be put in a Map in application scope.
*   Calendar is a large object containing about 540 bytes, this can usually be replaced by a Date, [org.joda.time.\[Local\]DateTime](http://joda-time.sourceforge.net/apidocs/org/joda/time/LocalDateTime.html), [java.time.\[Local/Zoned\]DateTime](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/LocalDateTime.html) or a even most compact: a long.
*   For a HashMap with Enum keys, use EnumMap. EnumMaps are represented internally as arrays. This representation is extremely compact and efficient.

### Spring ModelMaps

#### TMSU11

**Observation: ModelMaps are implicitly added to the session.** Spring BindingAwareModelMaps are implicitly and automagically added to the portlet session by Spring MVC under certain conditions. A Controller with the following code is problematic:

```java
@ActionMapping
public void action(ModelMap model) {
    ...
}
@RenderMapping
public String render(ModelMap model) {
    ...
}  
```

Spring will inject the proper data types. The problem is that with portlets, a browser redirect always happens in between the action and the render. A HTTP POST-Redirect-GET happens in the browser and where can Spring get the model from to inject in the render method? Right, from the session!

**Problem:** ModelMaps are rather large objects containing explicitly added data and administrative data from Spring. They are added to the Portlet session implicitly. They stay in the session for some time: during session activity and 30 minutes (HTTP timeout) after it, in case the user does not exit explicitly. They occupy heap space during that time, for every user.  
**Solution:** Make sure that there is no need for Spring to put ModelMaps or @ModelAttributes in the session. Remove the ModelMap from the render method argument list and create a new local ModelMap to use in the render request scope. If you really need to pass attributes from the action to the render, put them explicitly in the session in the action and make sure to remove them from the session as soon as possible. Do not remove the attributes from the session needed for the rendering process in the render method itself, as that would make page refreshes (or other re-renderings) impossible. 
Render parameters might be an option, see [TMSU01](#TMSU01).

#### TMSU12

**Observation: Form validation in an action request with ModelMaps or ModelAttributes is convenient, but they stay in the session after validation.**  
**Problem:** ModelMaps are rather large objects and still take heap space while they are not needed anymore.  
**Solution:** use the ModelAttribute in the validation error flow and clear the ModelMap right after validation in the normal/happy flow, which then also happens when the user has resolved the input validation errors and continues in the happy flow. This is the approach the OI project has taken. See the following example code:

```java
@ActionMapping
public void action(@ModelAttribute SomeForm form, BindingResult errors, ActionResponse response, ModelMap map) {
  // Validate the form, on error fill the BindingResult errors object and return the name of the render
  // (In this case use the implicit model by Spring to render the original screen including the error)
  validator.validate(form, errors);
  if (errors.hasErrors()) {
    ...
    response.setRenderParameter("error","true");
    return; 
  }
  ...
  // By clearing the map it won't be persistent in session by Spring
  map.clear();
}

@RenderMapping(params="error=true")
public String renderErrorPage(…) {
  // Make use of the implicit model of Spring. This model contains the original model as well as the error binding (for each field)
  return "page.ftl";
}
```
  

Reloading lists of values
-------------------------

#### RLOV01

**Observation: A list of static values is reloaded for each user session.** Lists Of Values (LOV's) which are more of less static, like countries, currencies, etc., are fetched from a back-end service which is cached in the database. They are stored in each request scope or worse, in each portlet session.  
**Problem:** Since these values are identical for each user and constant for a relatively long period of time, there is no need to store them separately for each user on the heap and call the service/database cache for each request. This introduces extra heap space usage and longer response times.  
**Solution:** Store LOV's only once in memory (best), or only once per application (good enough); in a memory cache or in application/portlet scope.

Inefficient database access
---------------------------

This section now has grown out on its own as [JavaDataAccessPerformance](https://github.com/jborgers/PMD-jPinpoint-rules/blob/master/docs/JavaDataAccessPerformance.md).

Improper use of XML and remoting
--------------------------------

See our [presentation on Java Performance Pitfall: improper use of XML/JSON and remoting.](https://youtu.be/hcRWWAOq5Jo?list=PL9rzqHHCiIVQr27ZsP0_r8eOgQMTbhJfF)

XML/SOAP/MQ is used for remoting as well as XML/SOAP/HTTP.

#### IUOXAR01

**Observation: MQ is used for synchronous calls.**  
**Problem:** MQ is relatively slow because of its asynchronous nature and configurations like persisted messaging.  
**Solution:** Use HTTP for synchronous calls.

#### IUOXAR02

**Observation: XML-Object Binding is too slow.**  
**Problem:** XML and SOAP are verbose formats and expensive to process, especially parse.  
**Solution:** If XML or SOAP handling proves to be a serious bottleneck in performance tests, consider to migrate to an alternative like the better performing RMI or a binary web service protocol, like [Hessian](http://en.wikipedia.org/wiki/Hessian_%28web_service_protocol%29) or [Google protocol buffers](https://developers.google.com/protocol-buffers/). Both are supported by the Spring framework. If XML is mandatory, consider [VTD-XML](http://vtd-xml.sourceforge.net/), [VTD-XML on Wikipedia](http://en.wikipedia.org/wiki/VTD-XML).

#### IUOXAR03

**Observation: XMLBeans is used.**  
**Problem:** XMLBeans introduces high memory usage and inferior performance for large documents.  
**Solution:** Migrate XMLBeans to JAXB

#### IUOXAR04

**Observation: JAXBContext is created for each user transaction.**  
**Problem:** JAXBContext creation is expensive because it does classloading and other expensive things.  
**Solution:** Since JAXBContext objects are thread safe, they can be shared between requests and reused. So, reuse created instances, e.g. as one singleton per application.

```java
private static final JAXBContext JAXB_CONTEXT;
static {
    try {
        JAXB_CONTEXT = JAXBContext.newInstance(X.class.getPackage().getName());
    } catch (JAXBException e) {
        throw new YException(e);
    }
}
```

**Rule name:** implemented, will give false positives: any construction in a method will be flagged. Construction in a static block, the safe way, will not be flagged.

#### IUOXAR05

**Observation: `XMLGregorianCalendar` and `GregorianCalendar` are used with JAXB for dates.**  
**Problem:** `XMLGregorianCalendar` and `GregorianCalendar` are large objects on the heap, involving substantial processing. To create a new `XMLGregorianCalendar`, the [poorly performing DatatypeFactory](http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6466177) is used. A `DatatypeFactory.newInstance()` is executed, which goes through the complete service look up mechanism, involving class loading.  
**Solution:** Add a converter for alternative date handling with joda-time `LocalDateTime` or [java.time.LocalDateTime](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/LocalDateTime.html) instead of default `XMLGregorianCalendar`.  
Example:

```java
 import org.joda.time.LocalDateTime;
 import org.joda.time.format.DateTimeFormatter;
 import org.joda.time.format.ISODateTimeFormat;

 public class DateConverter {
    private static final DateTimeFormatter DATE_FORMATTER = ISODateTimeFormat.date();

    public static LocalDateTime parseDate(final String date) {
        return DATE_FORMATTER.parseLocalDateTime(date);
    }

    public static String printDate(final LocalDateTime date) {
        return DATE_FORMATTER.print(date);
    }
 }
```

corresponding JAXB binding configuration:

```xml
<jxb:globalBindings>
   <jxb:javaType name="org.joda.time.LocalDateTime" xmlType="xs:date"
         parseMethod="com.company.package.DateConverter.parseDate"
         printMethod="com.company.package.DateConverter.printDate" />
</jxb:globalBindings>
```

Note that `LocalDateTime` is faster than `DateTime`, however, be aware of its time zone (none) and daylight saving (DST) properties.

#### IUOXAR06

**Observation: many `JAXBContextImpl` instances are used.**  
**Problem:** `JAXBContextImpl` objects are large objects on the heap, wasting memory  
**Solution:** Handle as many XSDs per `JAXBContextImpl` as possible. Ideally just one per application. See: [stackoverflow](http://stackoverflow.com/questions/13399567/multiple-jaxbcontext-instances)

#### IUOXAR07

**Observation: JAXB `Marshaller`, `Unmarshaller` or `Validator` is shared by threads.**  
**Problem:** JAXB `Marshaller`, `Unmarshaller` and `Validator` are not thread-safe.  
**Solution:** Create a new instance every time you need to marshall, unmarshall or validate a document. If it turns out to be a bottleneck in profiling, consider to pool the instances. More details [here.](https://jaxb.java.net/guide/Performance_and_thread_safety.html)

#### IUOXAR08

**Observation: XML messages between internal systems are validated in production.**  
**Problem:** XML validation is expensive, comparable to parsing. (`javax.xml.validation.Validator.validate` or `org.springframework.xml.validation.XmlValidator`)  
**Solution:** Validate messages produced during testing, and once the message format and semantics are valid, switch off validation. Note that load tests should be run production like, so with validation switched off like in production.

#### IUOXAR09

**Observation: XML related `XXXFactory.newInstance()` is called repeatedly.**  
**Problem:** Upon instance creation of `javax.xml.transform.TransformerFactory`, `javax.xml.parsers.DocumentBuilderFactory`, `javax.xml.soap.MessageFactory` or `javax.xml.validation.SchemaFactory`, i.a. the file system is searched for an implementing class in a jar file. This is expensive. The factories are not thread-safe, so they cannot simply be made static and/or shared among threads.  
**Solution:**

1.  Preferably create the factory once. Use a lock to guard the factory, preferably with a synchronizing wrapper. Be aware of possible contention. Or use a `ThreadLocal`.
2.  If many threads access often, consider using `ThreadLocal`s, or a pooling factory solution with for instance a `BlockingQueue` of a few factories.
3.  Simple alternative: use jvm system properties to specify your default implementation class. This at least prevents the biggest bottleneck: class path scanning. Examples:

```
-Djavax.xml.parsers.DocumentBuilderFactory=org.apache.xerces.jaxp.DocumentBuilderFactoryImpl 
-Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl //old
-Djavax.xml.transform.TransformerFactory=net.sf.saxon.TransformerFactoryImpl //newer
-Djavax.xml.soap.MessageFactory=org.apache.axis.soap.MessageFactoryImpl
-Djavax.xml.validation.SchemaFactory=com.saxonica.ee.jaxp.SchemaFactoryImpl
```

**Note:** More on `TransformerFactory` and caching compiled templates, see IBM's [XSLT transformations cause high CPU and slow performance](http://www-01.ibm.com/support/docview.wss?uid=swg21641274).

Example code `ThreadLocal` for `TransformerFactory`:

```java
private static final ThreadLocal<TransformerFactory> TRANSFORMER = ThreadLocal.withInitial(TransformerFactory::newInstance);
```

#### IUOXAR10

**Observation: `javax.xml.bind.JAXB` utility class is used for marshalling or unmarshalling**  
**Problem:** the `JAXB` class is not optimised for performance and multi-threaded usage. For instance, if multiple types are marshalled, with multiple `JAXBContext`'s, the `JAXBContext`'s are not reused. When URLs are supplied, the context is fetched outside of http connection pools with proper timeout properties and connection sharing. No validation is performed (if you need that, better skip validation when you can for better performance).  
**Solution:** use JAXB API directly for marshalling and unmarshalling to gain all the performance benefits as described in IUOXAR04 and IUOXAR06.

Instead of using this:
```java
JAXB.unmarshal(response, X.class)
```

create a reusable `JAXB_CONTEXT` via `JAXBContext.newInstance(new Class[]{X.class})` (as described in IUOXAR04)
and use as follows:

```java
Unmarshaller u = JAXB_CONTEXT.createUnmarshaller();
u.unmarshal(...);
```

Improper use of JSON and remoting
---------------------------------

#### IUOJAR01

**Observation: Jackson's ObjectMapper or JsonMapper is created in each method call.**  
**Problem:** ObjectMapper/JsonMapper creation is expensive in time because it does much class loading.  
**Solution:** Since Jackson's ObjectMapper/JsonMapper objects are [thread-safe after configuration](https://github.com/FasterXML/jackson-databind/wiki/JacksonFeatures) in one thread, 
they can be shared afterwards between requests and reused. So, an option is to reuse created and configured instances, from a static field. 
Better is using and sharing ObjectReaders and ObjectWriters created from ObjectMapper since they are immutable and therefore guaranteed to be thread-safe.
**Rule name:** ObjectMapperCreatedForEachMethodCall

#### IUOJAR02

**Observation: An ObjectMapper is used as field; an existing ObjectMapper is modified.**  
**Problem:** Configuring/modifying an ObjectMapper is thread-unsafe.  
**Helpful:** Only configure objectMappers when initializing: right after construction, in one thread.   
**Solution:** Create configured ObjectReaders and ObjectWriters from ObjectMapper and share those as field, since they are immutable and therefore guaranteed to be thread-safe.  
**Exceptions:** A convertValue method is not provided by Reader/Writer, therefore use of an ObjectMapper as field cannot easily be avoided in this case. The AvoidObjectMapperAsField rule is not applied.
Also when used like jaxMsgConverter.setObjectMapper(objectMapper) it is not considered a violation.    
**Rule names:** AvoidObjectMapperAsField, AvoidModifyingObjectMapper   
**Example:**   
```java
class ToAvoidStyle {
    private static final ObjectMapper staticObjectMapper = new ObjectMapper(); // bad by AvoidObjectMapperAsField
    private final ObjectMapper mapperField = new ObjectMapper(); // bad by AvoidObjectMapperAsField

    static {
        staticObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); 
    }

    public ToAvoidStyle() {
        mapperField.setSerializationInclusion(JsonInclude.Include.NON_NULL); 
    }

    ObjectMapper bad(ObjectMapper mapper) {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // bad by AvoidModifyingObjectMapper
        return mapper;
    }
}

class ExceptionCase {
  private static final ObjectMapper staticObjectMapper = new ObjectMapper(); //accepted by AvoidObjectMapperAsField
  private final ObjectMapper mapperField = new ObjectMapper(); //bad by AvoidObjectMapperAsField

  public UserProfileDto getUserProfileDto(UserProfile userProfile) {
        return staticObjectMapper.convertValue(userProfile, UserProfileDto.class);
  }
}

class GoodStyle {
    private static final ObjectWriter staticObjectWriter =
        new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL).writer(); // good
}
```

#### IUOJAR03

**Observation: A Gson object is created in each method call.**  
**Problem:** Gson creation is expensive. 
A [JMH benchmark](https://github.com/stokpop/performance-playground/blob/main/src/main/java/nl/stokpop/jmh/LetsParseGson.java) shows a 24x improvement reusing one Gson instance versus a new instance each call.  
**Solution:** Since Gson objects are supposed to be [thread-safe](https://stackoverflow.com/questions/10380835/is-it-ok-to-use-gson-instance-as-a-static-field-in-a-model-bean-reuse#answer-35189723),
they can be shared. So reuse created and configured instances from a static field. Take care to use (custom) thread-safe adapters/serializers.
Note that the default Gson `com.google.gson.DefaultDateTypeAdapter` adapter uses thread-unsafe `SimpleDateFormat` and the adapter synchronizes on the List of DateFormats to avoid concurrency issues.
This might be an [issue](https://github.com/google/gson/issues/162) in high load/performance environments. Consider using a non-synchronized thread-safe adapter for dates.  
**Rule name:** GsonCreatedForEachMethodCall  
**Example:**
```java
public class GsonReuse {
  private static final Gson GSON = new Gson();
  
  public String toJsonBad(List<String> list) {
    return new Gson().toJson(list); // bad
  }

  public String toJsonGood(List<String> list) {
    return GSON.toJson(list); // good
  }
}
```
Using XPath
-----------

#### UX01

**Observation: XPath is used to navigate through elements and attributes in an XML document in a wide scope.**  
**Problem:** XPath performance is bad when retrieving within a wide scope of the document, e.g. "//".  
**Solution:** Make sure the XPath expressions use a scope as narrow as possible.

#### UX02

**Observation: The XPathExpression is created and compiled every time.**

```java
XPathFactory.newInstance().newXPath().compile(xPathExpression).evaluate(..) 
```

is used. The new factory, new XPath, and the compiled expression are hard to cache, because those objects are not thread-safe.

**Problem:** Execution of the same code on the same expression is performed on every node retrieval, several times per user request. This takes CPU cycles, unnecessarily.  
**Solution:** There is no easy solution, other than not using XPath. Caching in a ThreadLocal might be an option, however, this introduces some complexity.   
**See:** [XPath performance tweaks](http://leakfromjavaheap.blogspot.com/2014/12/xpath-evaluation-performance-tweaks.html).   
**Example:**   
Using `ThreadLocal` for `XPathExpression`:

```java
class Bad {
    public static NodeList bad(Document doc) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expr = xpath.compile("//book[author='Isaac Asimov']/title/text()"); // bad
        return expr.evaluate(doc, XPathConstants.NODESET);
    }
}

class Good {
    private static final ThreadLocal<XPathFactory> tlFac = ThreadLocal.withInitial(XPathFactory::newInstance);
    private static final ThreadLocal<XPathExpression> tlExpr;
    static {
        XPath xpath = tlFac.get().newXPath();
        XPathExpression expr = xpath.compile("//book[author='Isaac Asimov']/title/text()"); // good
        tlExpr = ThreadLocal.withInitial(expr); 
    }
    public static NodeList good(Document doc) {
        return tlExpr.get().evaluate(doc, XPathConstants.NODESET); // good
    }
}
```

#### UX03

**Observation: XPath is used, implemented by Xalan. XPath implementation has bad performance.**  
**Problem:** XPath is reported to have bad performance, see [stack overflow](http://stackoverflow.com/questions/6340802/java-xpath-apache-jaxp-implementation-performance) and [XalanJ Jira issue](https://issues.apache.org/jira/browse/XALANJ-2540).  
**Solution:**  
1\. Avoid the use of XPath.  
2\. If avoiding XPath turns out to be very difficult, check if the reference applies to the application situation concerning implementation / versions and if so, consider to adopt the presented solution and benchmark before and after the fix. That solution being: use JVM option:

```
-Dcom.sun.org.apache.xml.internal.dtm.DTMManager=com.sun.org.apache.xml.internal.dtm.ref.DTMManagerDefault
```
or
```
-Dorg.apache.xml.dtm.DTMManager=org.apache.xml.dtm.ref.DTMManagerDefault 
```
Depending on e.g. which one shows up in your Java stack traces / javacore file.  
3\. Use CachedXPathAPI, see [Ways to increase the performance of XML processing in Java](https://xml.apache.org/xalan-j/apidocs/org/apache/xpath/CachedXPathAPI.html) - Case 2. Be aware of higher memory usage. Note that CachedXPathAPI object is thread-unsafe.  
**Alternative approach:** If XPath evaluation still turns out to be a bottleneck by profiling, consider to switch to [VTD-XML](http://vtd-xml.sourceforge.net/)

#### UX04

**Observation: Spring WS-Addressing is used with Action-annotated resolution**  
**Problem:** When using Spring WS-Addressing and the @Action annotation, Spring-WS uses AddressingVersions which use Spring's AbstractAddressingVersion. This uses XPath with Xalan implementation to extract headers from the addressing header and has bad performance. See [Spring Jira issue the RASS team submitted](https://jira.spring.io/i#browse/SWS-869). For parsing incoming messages this is a problem, for outgoing messages XPath usage is not a problem.  
**Solution:** Avoid the use of Spring WS-Addressing. In non-avoidable: add JVM option for partial solution, see [above](#UX03).  
**Alternative approach:** Add custom version objects to the AnnotationActionEndpointMapping during startup. These custom versions should resolve the headers without a performance hit.  
**Alternative approach:** If XPath evaluation still turns out to be a bottleneck by profiling, consider to switch to [VTD-XML](http://vtd-xml.sourceforge.net/)

Improper logging
----------------

See our [presentation on Java Performance Pitfall: improper logging.](https://youtu.be/uek0BbPNAxk?list=PL9rzqHHCiIVQr27ZsP0_r8eOgQMTbhJfF)

#### IL01

**Observation: Concatenation is used in unconditionally executed logging statements:**

```java
LOG.debug("Length: " + length + ", currency: " + currency);
```

**Problem:** Concatenation happens before the debug, trace or info method executes, so independent of the need to actually log. Concatenation is relatively expensive.  
**Solution:** Use SLF4J with the {} pattern and pass the variables without concatenating them

```java
LOG.debug("Length: {}, currency: {}", length, currency);
```

**Rule name:** UnconditionalConcatInLogArgument.

#### IL02

**Observation: A logging String is built unconditionally:**

  
```java
while(..) {
    ...
    logStatement.append("Found page parameter with key '" + key + "' and value '" + value + "'\n"); // bad
}
LOG.debug("Note: {}", logStatement);
```
  

**Problem:** String building, concatenation and/or other operations happen before the debug, trace or info method executes, so independent of the need to actually log. Concatenation is relatively expensive.  
**Solution:** Built the String conditionally on the log level, within an if statement. A nicer solution is possible with SLF4J2, by utilizing a lambda expression:
```
LOG.debug("Found page key-value pairs: {}", () -> buildPageKeyValueLogString()); // good
```
The `buildPageKeyValueLogString` method is deferred and only executed if needed, by SLF4J.

**Rule name:**: AvoidUnconditionalBuiltLogStrings

#### IL03

**Observation: An operation is executed on a log argument, irrespective of log level.** Like:

```java
LOG.debug("ACTUAL DOWNLOAD: EndDate Download Date: {}", String.format("%1$tR", endDateDownloadDate); // bad

LOG.trace("StepExecution: \n{}", stepExecution.toString()); // bad

LOG.debug("Complete Soap response: {}", getSoapMsgAsString(context.getMessage())); // bad
```

**Problem:** toString(), String.format or some other operation or method call is executed irrespective of log level. This may include formatting, concatenation, reflection and other wasteful processing. For example, the above line with toString seems rather harmless, however, you might change your mind if you see the toString implementation of [StepExecution](https://github.com/spring-projects/spring-batch/blob/master/spring-batch-core/src/main/java/org/springframework/batch/core/StepExecution.java) (at the bottom of the page.)

  
**Solution:** Remove the toString() since this is already invoked conditionally inside SLF4J. If formatting is really needed, execute it conditionally or in its toString method. Or even nicer, use a lambda expression with SLF4J2, see IL02 above.

```java
LOG.debug("ACTUAL DOWNLOAD: EndDate Download Date: {}", endDateDownloadDate); // good

LOG.trace("StepExecution: \n{}", stepExecution); // good

if (LOG.isDebugEnabled()) { // good
    LOG.debug("Complete Soap response: {}", getSoapMsgAsString(context.getMessage()));
}
```

**Rule name:** UnconditionalOperationOnLogArgument.

#### IL04

**Observation: MDC values are added for logging, but not removed, or not in all cases.**  
MDC = Mapped Diagnostic Context, used to provide context information (e.g. userId) for all subsequent log calls in a user transaction.  
**Problem:** MDC values can leak to other user transactions (requests) and log incorrect information. This can happen because MDC is implemented with ThreadLocals and a thread holding the ThreadLocal is reused for another user transaction after it has finished its current transaction. Additionally, memory is wasted by leaked MDC values.  
E.g.:

```java
MDC.put("UserId", userId);
```

with no corresponding

```java
MDC.remove("UserId");
```

**Solution:** Determine the MDC-value lifecycle and remove the MDC value in the proper place. Put the remove in a finally clause so it is also removed in case of exceptions.  
**See also:** [Automating access to the MDC](http://logback.qos.ch/manual/mdc.html).   
**Rule name:** MDCPutWithoutRemove.

#### IL05

**Observation: Synchonous logging is used for much data.**   
**Problem:** Logging is I/O which can take much time away from the user request/response.  
**Solution:** Use Asynchronous logging. In logback you can use: _ch.qos.logback.classic.AsyncAppender._ Log lines are put in a queue and the user does not have to wait for the actual logging, which is handled in a separate thread. Think about i.a. maximum queue size, data loss and what information to transfer to the logging thread (MDC).

#### IL06

**Observation: A log argument is created unconditionally, irrespective of log level.**  
**Problem:** Creation of a log argument with a toString or other operation(s) may be expensive, while depending on the log level, the result may not be used.     
**Solution:** Create the log argument conditionally on the log level, within an if statement. For just 'obj.toString()', just pass 'obj' to the log method and leave it to SLF4J to call toString() only if needed. See IL02 for a nice solution using a lambda.   
**Rule name:** UnconditionalCreatedLogArguments   

Improper Streaming I/O
----------------------

Improper streaming may result in unwanted large object allocations. The IBM JVM has the possibility to log large object allocations to analyse this. Large object allocation logging can be enabled by using the following jvm argument with an appropriate filter value:

```
-Xdump:stack:events=allocation,filter=#6m
```

Logging of the allocation stack traces will be in native_stderr.log.

#### ISIO01

**Observation: ByteArrayOutputStream or StringWriter default constructor is used for large strings/streams, or a capacity smaller or equal to the default.**  
**Problem:** By default an initial buffer of 32 bytes or 16 characters is created, respectively. If this is not enough, a new byte/char array will be allocated and contents will be copied into the new array. The old array becomes garbage to be collected and copying takes processing time. 
If you know what the minimum or typical size will be, this garbage and processing time are wasted.  
**Solution:** Presize the ByteArrayOutputStream or StringWriter with an initial capacity such that a resize is not needed in most cases. By using the [ByteArrayOutputStream](http://docs.oracle.com/javase/6/docs/api/java/io/ByteArrayOutputStream.html#ByteArrayOutputStream%28int%29) or 
[StringWriter](http://docs.oracle.com/javase/6/docs/api/java/io/StringWriter.html#StringWriter%28int%29) alternative constructor.  
If the buffer is send/received through I/O, then the size should be 8192 bytes or a multiple of it, 8 KiB being the default TCP buffer size on most systems. This value of 8 [KiB (kibibyte)](http://en.wikipedia.org/wiki/Kibibyte) is used as default buffer size in all I/O buffering classes in the Java libraries. 
If the buffer contents is not send or receiceved from I/O, than take 4 kiB or a multiple of it, 4 KiB being the default memory page size.   
**Rule name**: AvoidInMemoryStreamingDefaultConstructor   
**Example**: 
```java
class Bad {
    public static void bad()  {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); //bad
        StringWriter sw = new StringWriter(); //bad
        baos = new ByteArrayOutputStream(32); //bad - not larger than default
    }
}
class Good {
    public static void good()  {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(8192); // 8 kiB
        StringWriter sw = new StringWriter(2048);
    }
}
```
#### ISIO02

**Observation: An unbuffered input or output is used.** Applicable to InputStream, OutputStream, Reader and Writer  
**Problem:** Each write() or read() call is a system call which is relatively expensive. Doing a system call for every byte or char is very inefficient.  
**Solution:** By buffering, a bunch of data is given to the system call at once to process, making the work per byte much less and the total much more efficient. Examples:

```java
BufferedReader in = new BufferedReader(new FileReader("IOStreamDemo.java"));

BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream("yourFile.txt"));
```

This especially applies to direct file streaming. Access through ClassLoader.getResourceAsStream / URL.openStream where URL is of FILE or HTTP protocol, is buffered by the implementing class, e.g. [sun.net](http://sun.net).www.protocol.file.FileURLConnection.

#### ISIO03

**Observation: Streaming is not (fully) used for uploading or downloading documents: somewhere the whole file is loaded into memory in a byte array e.g. to determine the mime type or create a digest.**  
**Problem:** Large objects are allocated on the heap, up to e.g. 50 MB. We also observed 300 MB and even 1 GB in back-end systems. This likely triggers long gc’s for compaction or may trigger out of memory crashes.

In the next example, there are two large byte arrays in memory: one in baos and the other is the returned byte array since a copy is made in toByteArray().

```java
   private byte[] zipData() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        // stream 1 GB data from input to output stream (zos) here
        return baos.toByteArray();
   }
```

**Solution:** Use streaming all the way, don't use byte arrays. A mime type is determined from the first few bytes of the file, don't read in all 50 MB - 1 GB for that. Often, functionality can be achieved in a streaming way, i.e. [a digest can be computed in a streaming way](http://www.mkyong.com/java/java-sha-hashing-example/).

The previous example improved:

```java
   private void zipData() {
        FileOutputStream dest = new FileOutputStream(file);
		ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(dest));
        // stream 1 GB data from input to output stream (zos) here, which will be written to file
   }
```

#### ISIO04

**Observation: Streaming SOAP with MTOM is used for uploading or downloading documents, with embedded base64 encoded PayloadData.**  
**Problem:** The heap shows much heap usage by XMLStreamReaders, the IBM StAX implementation caches these. For large documents and high load, heap usage can become very high, such that it will cause an out-of-memory error crash.  
**Solution:** IBM's description [PM42465: Excessive memory usage by XMLStreamReader pool may lead to OutOfMemoryError](http://www-01.ibm.com/support/docview.wss?uid=swg1PM42465) offers a work around. In project experience (5-10 MB documents) this did not help enough, we still got an occasional OOM. Real solution was

1.  switch to binary streaming upload with apache HTTP client. This is also used for back-end service calls with large data transfers, not just uploads. 
2.  use binary attachments. In this solution, the PayloadData SOAP element only contains a reference to a separate MIME block in the POST body, containing a reference to a file with the binary data. See [Attachments over webservices](http://www.middlewareguru.com/mw/?p=752). This works well in project.

Validate with large object allocation logging that no large objects are allocated and buffered streaming is working correctly, by using a jvm argument like:

```
-Xdump:stack:events=allocation,filter=#6m
```

Logging of allocation stack traces will be in native\_stderr.log.

Extensive use of classpath scanning
-----------------------------------

#### EUOCS01

**Observation: A Spring ApplicationContext is re-created** (to complete with example)  
**Problem:** When a XXXApplicationContext is created, all Spring beans are initialized, wired and component scanning may take place. Component scanning involves extensive class path scanning which is expensive.  
**Solution:** Create the ApplicationContext only once in the application deployed/live time.

#### EUOCS02

**Observation: A Spring component scan is used explicitly** (to complete with example)  
**Problem:** A component scan uses extensive class path scanning which is expensive.  
**Solution:** Specify explicitly**.** And/or use Java 9+ modules which have an index.

Unnecessary use of reflection
-----------------------------

#### UUOR01

**Observation: Reflection is used unnecessarily**, for instance like:

```java
public boolean equals(final Object arg0) {
    return EqualsBuilder.reflectionEquals(this, arg0);
}
```

**Problem:** Reflection is relatively expensive.  
**Solution:** Avoid to use reflection. Use the non-reflective, explicit way, see equals and hashCode section below.  
**Rule name:** prototype ready, hits on EqualsBuilder.reflectionEquals and HashCodeBuilder.reflectionHashCode of Apache commons lang.

Thread unsafety and lock contention
-----------------------------------

When multiple threads access the same object, access it in a thread safe way. Getting thread safety right and not hindering performance is difficult. Locking with the synchronized keyword may introduce lock contention under load, which is bad for performance. To make threading aspects easier to understand in source code, we recommend the use of [Java Concurrency In Practice annotations.](http://jcip.net.s3-website-us-east-1.amazonaws.com/annotations/doc/index.html)

#### TUTC01

**Observation: The synchronized method contains much code and/or takes time.** For instance by doing I/O.  
**Problem:** Other threads that want to access the synchronized method on the same object have to wait until the 'owner' exits the method.  
**Solution:** Minimize the synchronized code block, don't execute an _alien_ call in a synchronized block, don't do I/O there. See [Concurrency In Practice](http://www.google.nl/search?q=Java+concurrency+in+practice).

#### TUTC02

**Observation: The broken [double checked locking idiom](http://en.wikipedia.org/wiki/Double-checked_locking) is used.** Example:

```java
// "Double-Checked Locking" idiom - Broken / Wrong!
class Foo {
    private Helper helper = null;
    public Helper getHelper() {
        if (helper == null) {
            synchronized(this) {
                if (helper == null) {
                    helper = new Helper();
                }
            }
        }
        return helper;
    }
}
```

**Problem:** Other threads may not see the initialized helper set by one thread. They could also see a partly initialized helper, leading to corruption. Sometimes. See **[double checked locking idiom](http://en.wikipedia.org/wiki/Double-checked_locking).**  
**Solution:** Use a volatile reference to make sure the assignment to it is published to all threads in a thread-safe way. For example for a lazy initialized, immutable map:

```java
class Foo {
    private volatile Map map = null;
    public Map getMap() {
        if (map == null) { // 1
            synchronized(this) { // 2
                if (map == null) { // 3
                    map = Collections.unmodifiableMap(buildMap()); // 4, 5
                }
            }
        }
        return map;
    }
    // 1. volatile, so every thread sees current value of reference
    // 2. make sure check-and-modify of map is 'atomic'
    // 3. make sure map is not initialized more than one time
    // 4. only read-access of map, so thread-safe
    // 5. make sure keys and values are thread-safe, too
}
```

This solution is usually good enough. A more optimized version using a local variable, yet more complicated can be found [on wikipedia](http://en.wikipedia.org/wiki/Double-checked_locking). See also: [double-checked-locking--clever--but-broken-by-Brian-Goetz](https://www.javaworld.com/article/2074979/double-checked-locking--clever--but-broken.html).

#### TUTC03

**Observation: A data structure like a Collection or Map or object like applicationContext is constructed once by one thread and consecutively read-only by one or more other threads. No thread-safety is used.**  
**Problem:** It is not guaranteed that the other threads will ever see the constructed data structure, and they may see a partially constructed version, leading to corruption of data. Sometimes.  
**Solution:** There are three options:

1.  use a static final variable and assign the fully constructed immutable datastructure to it during class loading;
2.  use a volatile reference and assign the fully constructed immutable datastructure to it once during initialization;
3.  use the double checked volatile locking idiom, see solution of the previous item.

For all three solutions holds that the data structure itself does not need to be thread-safe, only that is must be fully constructed and not changed after assignment to its reference. So, it better be immutable to guarantee this. [See Java Concurrency In Practice, Chapter 3.5](https://books.google.nl/books?id=EK43StEVfJIC&pg=PA344&lpg=PA344&dq=See+Java+Concurrency+In+Practice,+Chapter+3.5&source=bl&ots=uo0Cw2rRmy&sig=3bythgbuQ6ijOY3vT2oVCGVhd5E&hl=en&sa=X&ved=0ahUKEwi9-8DqyPbTAhVPZlAKHWVBAMMQ6AEITTAH#v=onepage&q=See%20Java%20Concurrency%20In%20Practice%2C%20Chapter%203.5&f=false).
 

#### TUTC04

**Observation: Java Concurrency In Practice annotations are not used.** ([net.jcip version with full documentation](http://jcip.net/annotations/doc/net/jcip/annotations/package-summary.html)) ([new package location: javax.annotation.concurrent](https://static.javadoc.io/com.google.code.findbugs/jsr305/3.0.1/javax/annotation/concurrent/package-summary.html))  
**Problem:** Often, the intended thread-safety policies of a class are unclear, leading to hard-to-find concurrency bugs. These annotations describe this intent.  
**Solution:** Utilize the annotations: @GuardedBy, @Immutable, @ThreadSafe and possibly @NotThreadSafe, as well. These will communicate the intended thread-safety promises to both users and maintainers. In addition, IDE's and FindBugs/Sonar use these annotations to check thread-safety. For example:

  
```java
@Immutable
public class String { 
...

@NotThreadSafe
public class StringBuilder { 
...

 @ThreadSafe
 class Example {
    private final Object lock = new Object();
    
    @GuardedBy("lock")
    private Stuff internal = ...;
    
    public void work() {
        synchronized(lock) {
            workWith(internal.fiddle());
        }
    }        
 }
```
  

#### TUTC05

**Observation: A class has setters with the only purpose to initialize it.**  
**Problem:** Setters make the class mutable while it may otherwise be immutable. Immutability eases multithreading.  
**Solution:** Use the [builder pattern](http://www.informit.com/articles/article.aspx?p=1216151&seqNum=2) instead of setter methods and make sure it is immutable.

#### TUTC06

**Observation: Mutable objects are used in HttpSession or PortletSession.**   
**Problem:** Objects in the HttpSession are accessed by multiple threads, for example consecutive threads from the thread pool, or concurrent portlet threads. HttpSession.getAttribute, setAttribute and removeAttribute are synchronized. A call to setAttribute is needed after modifications: it will make the reference to the object visible to other threads, as well as all modifications to the object itself which happened before that. However, there is no atomicity of the modification operations on the object itself, it may be interrupted by other threads and become in inconsistent state.  
**Solution:** Objects used in HttpSessions should be immutable. If modification is needed, a new version of the object should be put in session with setAttribute. If this is not possible (why?), be sure to access the object in session in a thread-safe way, using a lock around access and modification. Warning: This is difficult to get right and error prone. This problem and solution is described in [full detail by Brian Goetz](https://www.ibm.com/developerworks/library/j-jtp09238/).

#### TUTC07

**Observation: A singleton, or more general: an object shared among threads, is used with non-final and/or mutable fields and the fields are not guarded**   (e.g. accessor methods are not synchronized), while typically fields are not intended to change after initialization. This includes Spring @Component, @Controller, @RestController, @Service and @Repository annotated classes for application and session scope and **JavaEE bean-managed** @Singleton annotated classes.  
**Problem:** Multiple threads typically access fields of a singleton or may access fields in session scoped objects. If a field or its reference is mutable, access is thread-unsafe and may cause corruption or visibility problems. To make this thread-safe, that is, guard the field e.g. with synchronized methods, may cause contention.  
**Solution**: Make the fields final and unmodifiable. If they really need to be mutable, make access thread-safe. Thread-safety can be achieved e.g. by proper synchronization and use the [@GuardedBy](#TUTC04) annotation or use of [volatile](https://www.ibm.com/developerworks/library/j-jtp06197/).

**Notes**

1.  Instances of Date, StringBuilder, URL and File are examples of mutable objects and should be avoided (or else guarded) as fields of shared objects. In case mutable fields are final and not modified after initialization (read-only) they are thread safe, however any modification to it is thread-unsafe. Since field modification is easily coded, avoid this situation.
2.  Instances of classes like ArrayList, HashMap and HashSet are also mutable and should be properly wrapped with e.g. Collections.unmodifiableList after initialization (see [TUTC03](#TUTC03)), made immmutable by e.g. List.copyOf() or accessed thread-safely with e.g. Collections.synchronizedList or thread-safe implementations like ConcurrentHashMap.
3.  For autowiring/injection, the assignment of the reference is thread safe, so final is not required. The unmodifiable/thread-safe requirement for that field still holds. Also make sure no other thread-unsafe assignment is made to that field.
4.  In case you are sure the Component is used in single threaded context only (e.g. a Tasklet), annotate the class with @NotThreadSafe to make this explicit.
5.  Use package private and @VisibleForTesting for methods used for JUnit only.
6.  [Documentation on a.i. using final for threads-safety](https://www.securecoding.cert.org/confluence/display/java/TSM03-J.+Do+not+publish+partially+initialized+objects) and from the [JLS](http://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.5): An object is considered to be _completely initialized_ when its constructor finishes. A thread that can only see a reference to an object after that object has been completely initialized is guaranteed to see the correctly initialized values for that object's `final` fields.
7.  Also [See Java Concurrency In Practice, Chapter 3.5](https://books.google.nl/books?id=EK43StEVfJIC&pg=PA344&lpg=PA344&dq=See+Java+Concurrency+In+Practice,+Chapter+3.5&source=bl&ots=uo0Cw2rRmy&sig=3bythgbuQ6ijOY3vT2oVCGVhd5E&hl=en&sa=X&ved=0ahUKEwi9-8DqyPbTAhVPZlAKHWVBAMMQ6AEITTAH#v=onepage&q=See%20Java%20Concurrency%20In%20Practice%2C%20Chapter%203.5&f=false).

**Example with issues**

```java
@Component
@Scope(WebApplicationContext.SCOPE_SESSION) // similar for e.g. default SCOPE_APPLICATION
public class ReportController extends AbstractController {
    private Report data;
    private boolean contacted;
    private RestTemplate restTemplateOk;
	private String name; 

	public ReportController() { name = "LaundryReport"; } // unsafe
	public void createData() { data = ...; } // unsafe	
	public Report getData() { return data; }  // unsafe 
	public boolean getContacted() { return contacted; } // unsafe
	public void setContacted(boolean contacted) { this.contacted = contacted; } // unsafe
        @Autowired
	public void setRestTemplate(final RestTemplate restTemplate) { // autowiring is safe
    	this.restTemplateOk = restTemplate;
	}
```

**Example with issues solved**

```java
@Component
@Scope(WebApplicationContext.SCOPE_SESSION) // similar for e.g. default SCOPE_APPLICATION
public class ReportController extends AbstractController {
	@GuardedBy("this") // needed to remove pmd/Sonar violation, enables extra checks
    private Report data;
    private volatile boolean contacted; // assignment-safe
    private RestTemplate restTemplateOk;
	private final String name; // assignment-safe

	public ReportController() { name = "LaundryReport"; } // safe because of final
	public void synchronized createData() { data = ...; } // safe because synchonized (@GuardedBy)	
	public Report synchronized getData() { return data; }  // safe like previous if data is immutable, or if a copy is retured
	public boolean getContacted() { return contacted; } // safe because volatile
	public void setContacted(boolean contacted) { this.contacted = contacted; } // safe because volatile
        @Autowired
	public void setRestTemplate(final RestTemplate restTemplate) { // autowiring is safe
    	this.restTemplateOk = restTemplate;
	}
```

**Rule names:** AvoidUnguardedMutableFieldsInSharedObjects, AvoidUnguardedAssignmentToNonFinalFieldsInSharedObjects, AvoidUnguardedMutableInheritedFieldsInSharedObjects, AvoidUnguardedAssignmentToNonFinalFieldsInObjectsUsingSynchronized, AvoidUnguardedMutableFieldsInObjectsUsingSynchronized

#### TUTC08

**Observation: A static field is mutable or non-final.**  
**Problem:** Multiple threads typically access static fields. Unguarded assignment to a mutable or non-final static field is thread-unsafe and may cause corruption or visibility problems. To make this thread-safe, that is, guard the field e.g. with synchronized methods, may cause contention.

The next examples show non-final fields:

```java
private static String fileName = "<none>"; // likely a concurrency bug

private static Logger log = new Logger(); // potential concurrency bug
```

Here the first likely is a concurrency bug (agree?) and the second probably not. The reference is mutable. Usually 'final' can just be added to prevent concurrency problems.

The following example is less obvious, an array is by many considered immutable, which is a wrong assumption: elements can be replaced. Therefore, it has the same risk as the above examples.

```java
private static final String[] QUALIFIERS_Violate = {"alpha", "beta", "milestone"}; // mutable
```

**Solution:** Make the fields final and unmodifiable. How to make a static final List or Set unmodifiable, see example of [PML01](#PML01). The immutable version of the literal String array:

```java
private static final List QUALIFIERS_Ok = Collections.unmodifiableList(Arrays.asList("alpha", "beta", "milestone"));
```

With Java 9 this can be much more compact:

```java
private static final List QUALIFIERS_Ok = List.of("alpha", "beta", "milestone");
```

or by using Guava immutable collections like [ImmutableList](https://google.github.io/guava/releases/21.0/api/docs/com/google/common/collect/ImmutableList.html):

```java
private static final List QUALIFIERS_Ok = ImmutableList.of("alpha", "beta", "milestone");
```

Note that for primitives Guava has: [ImmutableIntArray](http://google.github.io/guava/releases/22.0/api/docs/com/google/common/primitives/ImmutableIntArray.html), [ImmutableLongArray](http://google.github.io/guava/releases/22.0/api/docs/com/google/common/primitives/ImmutableLongArray.html) and [ImmutableDoubleArray](http://google.github.io/guava/releases/22.0/api/docs/com/google/common/primitives/ImmutableDoubleArray.html).

If they really need to be mutable, make access thread-safe. Thread-safety can be achieved e.g. by proper synchronization and use the [@GuardedBy](#TUTC04) annotation or use of volatile. Consider lock contention.

**Rule name:** AvoidMutableStaticFields

#### TUTC09
**Observation: A compound statement like ``i++`` or ``i-=1`` is used for a volatile field**  
**Problem:** A compound statement like ``i++``, ``i--``, ``i += 1`` or  ``i -= 1`` may seem one statement and thread-safe for a volatile field. 
However, the operation actually comprises two separate statements executed non-atomically and therefore *not* thread-safe.  
**Solution:** In stead of volatile, guard the field properly with synchronized, atomics like AtomicInteger or java.util.concurrent locks.  
**Rule name:** AvoidIncrementOrDecrementForVolatileField

#### TUTC10
**Observation: A field is defined as volatile while the class has prototype scope.**  
**Problem:** Volatile has some overhead, especially for writes. When getting the bean from the Spring applicationContext, 
prototype scope means that each invocation creates a new object so the field is not shared.  
**Solution:** Since only one thread can access the field, there is no need for volatile and it can be removed.  
**Rule name:** AvoidVolatileInPrototypeScope  

#### TUTC11

**Observation: A singleton, or more general: an object shared among threads, has a field that is not private.**  
**Problem:** A shared object needs to be thread safe. Thread safety of fields cannot be guaranteed if not private: 
the fields can possibly be modified from other classes.
**Solution:** Make the fields private and make sure that they are used in a threadsafe way.  
**Rule name:** AvoidNonPrivateFieldsInSharedObjects

#### TUTC12

**Observation: Only local variables are accessed in a synchronized block.**  
**Problem:** Synchronization has overhead and may introduce lock contention.  
**Solution:** Remove the synchronized statement because local variables are only accessible by the owning thread and are not shared.  
**Rule name:** SynchronizingForLocalVars
**Example:** 
```java
public class Foo {
  private Map<String, String> mapField;

  protected Map<String, String> bad() {
    Map<String, String> addHeaders = MDC.getCopyOfContextMap();

    synchronized (this) { // bad, no use
      if (addHeaders == null) {
        addHeaders = new HashMap<>();
      }
    }
    return addHeaders;
  }
}
```

Unnecessary execution
---------------------

#### UE01
**Observation: A Calendar is unnecessarily created for a Date or time**  
**Problem:** A Calendar is a heavyweight object and expensive to create. For example, to copy a Date:

```java
Calendar dateCalendar = Calendar.getInstance();
dateCalendar.setTime(date);
return dateCalendar.getTime();
```

or

```java
long time = Calendar.getInstance().getTimeInMillis();
```

**Solution:** Avoid use of Calendar, in this example by:

```java
new Date(otherDate.getTime());
```

or

```java
long time = System.currentTimeMillis();
```

Or better yet instead of Date, use a [org.joda.time.LocalDateTime](http://joda-time.sourceforge.net/apidocs/org/joda/time/LocalDateTime.html) or [java.time.LocalDateTime](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/LocalDateTime.html), which also has the advantage over java.util.Date that it is immutable.  
**Rule name**: prototype ready, hit on Calendar.getInstance().getTime() usage and on two steps in same block.

#### UE02

**Observation: Unnecessary or hidden auto boxing and unboxing**, e.g. primitive types from/to corresponding Object types.  
**Problem:** There is a performance penalty, especially when using large arrays and/or compute intensive loops. Can introduce subtle bugs, see example 2.  
**Solution:** Avoid auto boxing and unboxing.  
Example 1: subcategoryId is of type Integer:

```java
response.setRenderParameter(RENDER_PARAMETER_SUB_CATEGORY_ID, Integer.toString(subCategoryId));
```

subCategoryId is unboxed to an int to work in the Integer.toString(int) method that expects only a primitive int. Use subcategoryId.toString() to avoid unboxing.

Example 2: categoryId is of type int, valuesAsList is of type List<Integer>:

```java
if (valuesAsList.contains(categoryId)) {
     valuesAsList.remove((Integer) categoryId);
} else {
     valuesAsList.add(categoryId);
}
```
  
categoryId of type int will be boxed to Integer for the contains() and add() and remove() calls on valuesAsList.

  
Note the use of explicit (Integer) cast to call the correct overloaded remove(Integer) method instead of the remove(int) method. The first will remove the Integer object, the second will remove the Object on index categoryId. Without the explicit cast the latter will be called, resulting in wrong behaviour.  
Suggested fix: create a category id variable of type Integer before the if statement so no further implicit boxing is done.  
**Tip:** enable the compiler check on auto boxing/unboxing in eclipse.

Inefficient memory usage
------------------------

#### IMU01

**Observation: A Calendar field is used in an object with many instances.**  
**Problem:** A Calendar field is typically only used for representing a date and time. A java.util.Calender object wastes heap space: it occupies about 540 bytes of heaps space.  
**Solution:** Use a [org.joda.time.LocalDateTime](http://joda-time.sourceforge.net/apidocs/org/joda/time/DateTime.html) or [java.time.LocalDateTime](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/LocalDateTime.html), which also has the advantage over java.util.Date that it is immutable. Also even a primitive long type for "milliseconds since epoch" could be used instead, for the most space-efficient solution.

Improper use of collections
---------------------------

#### IUOC01

**Observation: A LinkedList is used.** e.g. to maintain insertion order in a list  
**Problem:** A LinkedList is faster than an ArrayList for insertion and removal of elements of long lists, say with > 100 elements. However, it takes more memory because it maintains a forward and backward pointer for every element. Additionally, for short lists it is never faster.  
**Solution:** Use an ordinary ArrayList in stead. It will maintain insertion order just like the Linked version. Note: a Set and Map don't maintain insertion order, those need the Linked version for that.

#### IUOC02

**Observation: A ConcurrentHashMap is used unnecessarily**, that is, for created-only-once and consecutively unmodified data or when used by only one thread.  
**Problem:** A ConcurrentHashMap is particularly greedy in heap usage because of its segments and locks.  
**Solution:** Only use ConcurrentHashMap with multiple threads and when modified. Otherwise, for read-write access by a single thread just use HashMap en with multiple threads and read-only use:

```java
Map readOnlyMap = Collections.unmodifiableMap(initializeHashMap());
```

#### IUOC03

**Observation: A HashMap or HashSet is used with Enum keys.**  
**Problem:** A HashMap and HashSet are rather greedy in memory usage.  
**Solution:** Use an EnumMap or EnumSet. It is represented internally with arrays which is extremely compact and efficient.

```java
Map<YourEnumType, String> map = new EnumMap<>(YourEnumType.class);
Set<YourEnumType> set = EnumSet.allOf(YourEnumType.class);
```

#### IUOC04

**Observation: Elements are searched in a List .** The list is iterated or streamed until the right (key, value) element is found.  
**Problem:** the time to find element is O(n); n = the length of list.  
**Solution:** a Map is more time efficient, value is found by key. Access time is O(1), provided the [hashCode is well defined](http://www.ibm.com/developerworks/library/j-jtp05273/).

#### IUOC05

**Observation: A Collection is instantiated without an initial capacity.**  
**Problem:** If no initial capacity is specified, a default capacity is used, which will rarely be optimal. Failing to specify initial capacities for collections may result in performance issues, if space needs to be reallocated and memory copied when capacity is exceeded.  
**Solution:** Specify an initial capacity, suited for your need.

#### IUOC06

**Observation: A Collection is wrapped with a synchronized wrapper and multiple threads put significant load on it.**  
**Problem:** There is only one lock used for accessing the collection by all threads, which may result in lock contention.  
**Solution:** Use a collection which is optimized for access by multiple threads:

*   CopyOnWriteXxx collections of [java.util.concurrent](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/package-summary.html) for mostly read access
*   ConcurrentXxx collections of [java.util.concurrent](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/package-summary.html) for non-mostly read. These utilizes non-blocking/wait-free algorithms or lock striping (see [thinking in parallel](http://www.thinkingparallel.com/2007/07/31/10-ways-to-reduce-lock-contention-in-threaded-programs/)), for instance ConcurrentHashMap. Or use NonBlockingHashMap of [high-scale-lib](https://sourceforge.net/projects/high-scale-lib/).
*   Add lock striping to your collection with [guava's Striped](https://google.github.io/guava/releases/19.0/api/docs/com/google/common/util/concurrent/Striped.html)<ReadWriteLock>.

#### IUOC07

**Observation: To get an enum value by its defined field, the values are streamed on every call.**   
**Problem:** the time to find element is O(n); n = the number of enum values. This identical processing is executed for every call. Considered problematic when n > 3.     
**Solution:** use a static field-to-enum-value Map. Access time is O(1), provided the [hashCode is well-defined](http://www.ibm.com/developerworks/library/j-jtp05273/).
For one String field, usually toString returns that field. Consider to implement a fromString method to provide the reverse conversion by using the map, see the following examples:   
**Examples:**
```java
// BAD
public enum Fruit {
    APPLE("apple"),
    ORANGE("orange"),
    BANANA("banana"),
    KIWI("kiwi");

    private final String name;

    Fruit(String name) { this.name = name; }

    @Override
    public String toString() { return name; }

    public static Optional<Fruit> fromString(String name) {
        return Stream.of(values()).filter(v -> v.toString().equals(name)).findAny(); // bad: iterates for every call, O(n) access time
    }
}
```
Usage: `Fruit f = Fruit.fromString("banana");`
```java
// GOOD
public enum Fruit {
    APPLE("apple"),
    ORANGE("orange"),
    BANANA("banana"),
    KIWI("kiwi");

    private static final Map<String, Fruit> nameToValue =
            Stream.of(values()).collect(toMap(Object::toString, v -> v));
    private final String name;

    Fruit(String name) { this.name = name; }

    @Override
    public String toString() { return name; }

    public static Optional<Fruit> fromString(String name) {
        return Optional.ofNullable(nameToValue.get(name)); // good, get from Map, O(1) access time 
    }
}
```
As a side note, if you are happy with the Fruit name as capitals, you don't need any of the fields and methods and the default implementation of toString and valueOf will do,
so Fruit simply becomes:
```java
public enum Fruit { // great, very simple
    APPLE,
    ORANGE,
    BANANA,
    KIWI;
}
```
With usage: `Fruit f = Fruit.valueOf("BANANA");`

**See:**
* This is a special case of [#IUOC04](#IUOC04).
* Effective Java 3rd Ed, p164 (Use enums).

Inefficient String usage
------------------------

#### ISU01

**Observation: A StringBuffer is used.**  
**Problem:** StringBuffer is thread-safe and has locking overhead.  
**Solution:** Thread-safety not needed if only accessed by one thread. Use StringBuilder instead.  
**Rule name:** AvoidStringBuffer.

#### ISU02

**Observation: Multiple statements are executed to concatenate to the same String with the +-operator. Example:**

```java
for (String val : values) {
    logStatement += val;
}
```
  

**Problem:** Each statement with one or more +-operators creates a hidden temporary StringBuilder, a char\[\] and a new String object, which all have to be garbage collected.  
**Solution:** If you have multiple statements which concatenate to the same String with the +-operator in a method, it is more efficient to explicitly use a StringBuilder, append to it and use one toString at the exit of the method. This is especially true for concatenation within a loop.  
**Rule name:** AvoidConcatInLoop, AvoidMultipleConcatStatements

#### ISU03

**Observation: A StringBuilder used to build long strings is constructed with default capacity.**  
**Problem:** The default initial capacity of StringBuilder is 16 characters. If more is appended, a new char\[\] will be allocated with: new-capacity = 2 \* capacity + 2. The contents will be copied into the new char\[\] and the old char\[\] can be garbage collected. This allocation, copying and garbage collection takes time.  
**Solution:** Construct the StringBuilder with an initial capacity explicitly:

```java
StringBuilder builder = new StringBuilder(2048);
```

Choose the value such that it will fit without resizing in most cases. And of course, you might want to define a constant field for the size.

#### ISU04

**Observation: Concatenation of Strings is used inside an StringBuilder.append argument.**  
Example:

```java
builder.append("ExceptionType: " + ex.getClass().getName() + ", ");
```

**Problem:** Concatenating one or more Strings creates a hidden temporary StringBuilder, a char\[\] and a new String object, which all have to be garbage collected. This generated StringBuilder is created in addition to the explicitly used StringBuilder.  
**Solution:** Use one or more extra append on the explicit StringBuilder. Example:

```java
builder.append("ExceptionType: ").append(ex.getClass().getName()).append(", ");
```

**Rule name:** AvoidConcatInAppend

#### ISU05

**Observation: A StringBuilder is created and append is used for one statement resulting in a String.**  
Example:

```java
    public String bad() {
        return new StringBuilder()
                .append(name) // bad 
                .append(" = ")
                .append(value)
                .toString();
    }
```

**Problem:** Creating a StringBuilder and using append is more verbose, less readable and less maintainable than simply using String concatenation (+).
                         For one statement resulting in a String, creating a StringBuilder and using append is not faster than simply using concatenation.  
**Solution:** Simply concatenate Strings in one statement, it is more concise, better readable and more maintainable. Example:

```java
    public String good() {
        return name + " = " + value;
    }
```

**Rule name:** AvoidUnnecessaryStringBuilderCreation


Inefficient date time formatting
--------------------------------

#### IDTF01

**Observation: A SimpleDateFormat is used.**  
**Problem:** java.util.SimpleDateFormat is thread-unsafe. The usual solution is to create a new one when needed in a method. Creating SimpleDateFormat is relatively expensive.  
**Solution:** Use a [Joda-Time](http://joda-time.sourceforge.net/index.html) [DateTimeFormat](http://joda-time.sourceforge.net/api-release/org/joda/time/format/DateTimeFormat.html) to create a specific [DateTimeFormatter](http://joda-time.sourceforge.net/api-release/org/joda/time/format/DateTimeFormatter.html) or use [java.time.DateTimeFormatter](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/format/DateTimeFormatter.html). These classes are immutable, thus thread-safe and can be made static final. Example:

```java
private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");
[...]
LocalDateTime lastDate = DATE_FORMATTER.parseLocalDateTime(lastDateString);
```

Note: sometimes the use of SimpleDateFormat is forced by an API of a library. For instance, Jackson's ObjectMapper uses SimpleDateFormat. Because Jackson prevents threading issues,
the rule allows the usage here. Beware there might be some contention when you use objectMapper.setDateFormat(SimpleDateFormat), if so, better create your own thread safe date serializers
or use jackson-datatype-jsr310.
 
Like SimpleDateFormat, java.util.Date and -Calendar are mutable and flawed in other ways. Joda-Time is the better alternative and when Java 8 is available, [java.time](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/package-summary.html) is the way to go .  
**Rule name:** AvoidSimpleDateFormat.

#### IDTF02

**Observation: A DateTimeFormatter is created from a pattern on every parse or print.**  
**Problem:** Recreating a DateTimeFormatter is relatively expensive.  
**Solution:** org.joda.time.format.DateTimeFormatter or [java.time.DateTimeFormatter](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/format/DateTimeFormatter.html) is thread-safe and can be shared among threads. Create the formatter from a pattern only once, to initialize a static field. See previous example.  
**Rule name:** AvoidRecreatingDateTimeFormatter

#### IDTF03

**Observation: Joda-time parseDateTime or printDateTime is used.**  
**Problem:** These methods consider timezone information which is expensive and usually unnecessary. There is a [performance issue](http://java-performance.info/joda-time-performance/) with time zones in joda time library 2.1-2.3  
**Solution:** Use DateTimeFormatter.parseLocalDateTime(String) and DateTimeFormatter.print(LocalDateTime). In my benchmarks, the parse local is 2-3 times faster, and the print local is ~10% faster. Make sure the functionality is still correct. Note that LocalDateTime besides times zones [does not support daylight saving time (DST)](http://blog.smartbear.com/programming/date-and-time-manipulation-in-java-using-jodatime/).  
Upgrade to version >= 2.4 or when Java 8 is available, move to [java.time](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/package-summary.html).

Inefficient regular expression usage
------------------------------------

#### IREU01

**Observation: One of the regular expression operations of String is used: matches, replaceAll, replaceFirst, split; or the static Pattern.matches().**  
**Problem:** A regular expression is implicitly compiled on every invocation, which can be expensive, depending on the length of the regular expression.  
**Solution:** Use a matcher with the constant compiled regular expression pattern. Example:  
old:

```java
String getFileNameWithCount() {
    return filename.replaceAll("(.\*)\\\\.(\[^\\\\.\]\*)", "$1\\\\(" + count + "\\\\).$2");
}
```

new:

```java
private static final Pattern AROUND_LAST_DOT_PATTERN = Pattern.compile("(.\*)\\\\.(\[^\\\\.\]\*)");
..
String getFileNameWithCount() {
    return AROUND_LAST_DOT_PATTERN.matcher(filename).replaceAll("$1\\\\(" + count + "\\\\).$2");
}
```

**Rule name:** AvoidImplicitlyRecompilingPatterns, improved: AvoidImplicitlyRecompilingRegex

#### IREU02

**Observation: A regular expression is compiled in a method.**  
**Problem:** A regular expression is compiled on every invocation, which can be expensive, depending on the length of the regular expression.  
**Solution:** Compile the pattern only once and assign it to a private static field. java.util.Pattern objects are thread-safe so they can be shared among threads.  
**Rule name:** AvoidRecompilingPatterns

Use of slow library calls
-------------------------

#### UOSLC01

**Observation: [java.net](http://java.net).URLEncoder.encode is called on an IBM JRE.**  
**Problem:** [java.net](http://java.net).URLEncoder.encode is slow in multi-threaded environment for [java.net](http://java.net).URLEncoder source before Java 6. This turned out to be a bottleneck in a large webshop application, I reported this in 2006 and it was fixed in Sun Java 6, see [Sun Bug](http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6437829). However, IBM still uses a version of 17/11/2005 in JDK7 and even in JDK8, hence it suffers from this issue.  
**Solution:** Use [org.apache.commons.codec.net.URLCodec](http://commons.apache.org/codec/apidocs/org/apache/commons/codec/net/URLCodec.html).encode instead.

#### UOSLC02

**Observation: Base64 encoding is achieved with sun.misc.BASE64Encoder and decoding with sun.misc.BASE64Decoder.**  
**Problem:** These implementations are not efficient  
**Solution:** There is actually a hidden fast alternative since JAXB 1.0 / JavaEE 5+: [javax.xml.bind.DatatypeConverter](http://docs.oracle.com/javaee/6/api/javax/xml/bind/DatatypeConverter.html) parseBase64Binary and printBase64Binary methods, see [here](http://java-performance.info/base64-encoding-and-decoding-performance/). If you have Java 8+, use java.util.Base64, it is even a little faster.

Potential memory leaks
----------------------

#### PML01

**Observation: An object field is mutable while it should not change after initialization**, for a field like an ArrayList, HashMap, StringBuilder or a custom object.  
**Problem:** The field can unintentionally be added to, so grow and become a memory leak.  
**Solution:** Make the field immutable and final. In a constructor, defensively copy the modifiable argument, so also the caller is not able to modify the object referenced by the field anymore. In case of an unmodifiable wrapped collection, make sure the inner collection is not directly reachable anymore after initialization.  
**Rule name:** AvoidMutableLists   
**Example:**
```java
class Conf {
  private final List configurationItems;
  
  public Conf(List listOfImmutableElems) { 
    configurationItems = Collections.unmodifiableList(new ArrayList(listOfImmutableElems));
  }
}
 
class PaymentUtil {
    private static final Set<String> BRANCH_NAMES;
    static {
		final Set<String> branches = new HashSet<>();
        branches.add("Company Antwerp Branch");
        branches.add("Company Frankfurt Branch");
        branches.add("Company London Branch");
		BRANCH_NAMES = Collections.unmodifiableSet(branches);
    }    
}
```

#### PML02

**Observation: Timer objects must be shut-down properly on application shutdown/redeploy,**  
**Problem:** If not correctly shut down whole classloader remains in memory.  
**Solution:** Implement an ... Aware bean and implement the OnDestroy ..

#### PML03

**Observation: A Spring @ComponentScan or @EntityScan is set too wide eg. com.company.shop**  
**Problem:** Many unwanted and unexpected objects on the heap, wasting memory  
Unexpected leaks could occur when initialized from a static context, which are unaware of lifecycle events.  
**Solution:** Make your component scans as narrow as possible. You can also provide classes from the packages to scan, see example:

```java
@EntityScan(basePackageClasses = {Job.class, ReportJob.class, ScheduleJob.class, Jsr310JpaConverters.class})
```

#### PML05

**Observation: Context and Dependency Injection is used with explicit calls of CDI.current().select(MyClass.class).get()**  
**Problem**: We found a memory leak with this use of CDI. Two objects of type [com.ibm.ws](http://com.ibm.ws).cdi.impl.CDIImpl appear in the heap. One is small and the other is very large. See <TODO> and see more info: [dont-get-trapped-into-a-memory-leak-using-cdi-instance-injection](https://blog.akquinet.de/2017/01/04/dont-get-trapped-into-a-memory-leak-using-cdi-instance-injection/)  
**Solution**: use destroy to cleanup.      
**Example**:   
```java
MyClass myObject= CDI.current().select(MyClass.class).get();
try {
	....//do stuff
} finally {
	CDI.current().destroy(myObject);
}
```

**Rule name:** AvoidCDIReferenceLeak [PMD-jPinpoint-rules/issues/28](https://github.com/jborgers/PMD-jPinpoint-rules/issues/28)

Violation of Encapsulation, DRY or SRP
--------------------------------------

[Encapsulation](http://en.wikipedia.org/wiki/Encapsulation_%28object-oriented_programming%29) (or [information hiding](http://en.wikipedia.org/wiki/Information_hiding)), [Don’t Repeat Yourself (DRY)](http://en.wikipedia.org/wiki/Don%27t_repeat_yourself) and the [Single Responsibility Principle (SRP)](http://en.wikipedia.org/wiki/Single_responsibility_principle) are important for performance because it makes it easier to improve performance by optimizing functionality, that is, the to-optimize code is limited to the implementation of a class.

#### VOEDOS01

**Observation: Object exposes internal mutable state**, a field like an ArrayList or a java.util.Date.  
**Problem:** The state can be modified outside of the object, e.g. in case of a list it can be modified, added to, removed from or cleared, outside of object. No encapsulation.  
**Solution:** If not really needed, remove the exposing method. Use proper encapsulation. Apply the object oriented: [Tell, don’t ask](http://pragprog.com/articles/tell-dont-ask) principle: tell the object to do something with the state it owns, instead of asking for the internal state and do something with it outside of the object. If you think you have to expose the object, think again. If you then still have to expose, expose the object only unmodifyable using e.g. java.util.Collections.unmodifyableList() or create a copy in case of a mutable object like java.util.Date (or replace by an immutable type [org.joda.time.LocalDateTime](http://joda-time.sourceforge.net/apidocs/org/joda/time/LocalDateTime.html) or [java.time.LocalDateTime](https://docs.oracle.com/javase/8/docs/api/index.html?java/time/LocalDateTime.html))

#### VOEDOS02

**Observation: Object exposes internal unmodifyable data structure** like an unmodifyable list.  
**Problem:** The list is iterated over, some condition is tested and some operation is performed conditionally. This is actually a query, a selection of elements followed by an operation. Quite similar of these queries+operations typically exist in various places.   
**Solution:** See previous item, apply: Tell, don't ask. See also [Violation of encapsulation](http://www.artima.com/weblogs/viewpost.jsp?thread=132358). These queries+operations are much easier to optimize if they are located in one place.

#### VOEDOS03

**Observation: A mutable static field is used.** This can be a static non-final reference or a static final mutable object.  
**Problem:** Violates encapsulation, the state can be modified outside of the object/class. If modified and used by multiple threads, it is thread-unsafe, may cause corruption of data or not viewing changes done by other threads.  
**Solution:** Make it a final reference, possibly a blank final; and/or an immutable object or unmodifiable via a wrapper like [Collections.unmodifiableList](http://docs.oracle.com/javase/6/docs/api/java/util/Collections.html#unmodifiableList%28java.util.List%29).

#### VOEDOS04

**Observation: An interface is used to define constants.**  
**Problem:** Constants are often implementation details. Putting constants in an interface makes them part of the public API of all implementing classes. Doing this for constants which are implementation details is bad OO practice. It is a [documented anti-pattern](http://en.wikipedia.org/wiki/Constant_interface).  
**Solution:** Make it a Class which cannot be instantiated, or an Enum. Use static imports.  
**Rule name:** AvoidConstantsInInterface.

#### VOEDOS05

**Observation: A constructor or setter shares internal state.** A reference to a mutable object is passed in a constructor or setter and assigned to a field, it is used directly as state of the object.  
**Problem:** Internal state can be modified from outside of the object, by using the reference to the mutable object by the caller of the constructor or setter. Risk of thread-unsafety.  
**Solution:**

1.  Defensively copy the referenced object such that no mutable state is shared.
2.  Use a [builder pattern](http://www.informit.com/articles/article.aspx?p=1216151&seqNum=2) in case of more than a few parameters.

#### VOEDOS06

**Observation: A Java record shares internal mutable state.** Records are a concise way to code your plain "data carriers". They are transparent holders for _shallowly_ immutable data, that is, the fields (references) are final. 
Still, fields may reference mutable objects.   
**Problem:** Internal state can be modified from outside of the record, through the implicit accessor method or by the caller of the constructor. Risk of thread-unsafety.  
**Solution:** Use [record compact constructor](https://docs.oracle.com/en/java/javase/16/language/records.html) to defensively copy the (possibly) mutable object such as a List, Set or Map to make the record _deeply_ immutable. 
Note that for this the collection elements also need to be deeply immutable.   
**Rule name:** AvoidExposingMutableRecordState   
**Example:**
```java
record BadRecord(String name, List<String> list) { // bad, possibly mutable List exposed
}

record GoodRecord(String name, List<String> list) {
  public GoodRecord {
    list = List.copyOf(list); // good, immutable list
  }
}
```
**Note:** copyOf() doesn't actually do any copying if the input is already an immutable list created via List.of() (or related methods).    
**See:** [enforce-immutable-collections-in-a-java-record](https://stackoverflow.com/questions/67604105/enforce-immutable-collections-in-a-java-record) and [jep-395](https://openjdk.org/jeps/395).   
