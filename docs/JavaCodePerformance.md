
Java Code Performance - pitfalls and best practices
=====================
By Jeroen Borgers ([jPinpoint](www.jpinpoint.com)) and Peter Paul Bakker ([Stokpop](www.stokpop.com)), sponsored by Rabobank

# Table of contents

- [Introduction](#introduction)
- [Improper back-end interaction / remote service calls](#improper-back-end-interaction--remote-service-calls)
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
**Problem:** The default maximum connections per route is by default set to 2. This throttles the number of connections to the back-end usually much more than required, such that many requests have to wait for a connection and response times get much higher than needed.The connection timeout is usually set to a low number (e.g. 300 ms), in that case connection timeout exceptions will occur.  
**Solution:** Set the default maximum connections per route to a higher number, e.g. 20. Also increase the Max Total to at least the DefaultMaxPerRoute or a multiple in case of multiple routes. Example for only one host:

```java
PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
cm.setMaxTotal(maxFileTransferConnections);
cm.setDefaultMaxPerRoute(maxFileTransferConnections);
```

or for asynchronous connections using nio:

```java
    private HttpAsyncClientBuilder createHttpClientConfigCallback(final HttpAsyncClientBuilder clientBuilder) {
        clientBuilder
            .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
            .setMaxConnTotal(MAX_CONNECTIONS_TOTAL);
```

Note that class PoolingClientConnectionManager and several othersare deprecated and [PoolingHttpClientConnectionManager](http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/conn/PoolingHttpClientConnectionManager.html) is the one to use for synchronous calls and NHttpClientConnectionManager (e.g. by HttpAsyncClientBuilder) for asynchronous calls.

#### IBI04

**Observation: A blocking asynchronous call future.get() without time-out is used.**  
**Problem:** It cannot deal with hanging threads. Threads may get stuck in database, a remote system, because of a network hiccup, in error or other exceptional situations.  
**Solution:** Use the version with timeout and handle a timeout situation.

```java
future.get(long timeout, TimeUnit unit)
```

See: [Monix Best Practice](https://monix.io/docs/2x/best-practices/blocking.html)

#### IBI05

**Observation: Apache DefaultHttpClient is used with multiple threads.**  
**Problem:** This HttpClient can only handle one thread on one connection and will throw an IllegalStateException in case a second thread tries to connect when the first is using the connection.  
**Solution:** Use a PoolingHttpConnectionManager, see Apache doc: [connection management](http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d5e639).

#### IBI06

**Observation: Deprecated or thread-unsafe HTTP connectors are used.**  
**Problem:** Several HTTP client connection managers are thread-unsafe which may cause session data mix-up or have other issues for which they were made deprecated. Highest risk of session data mixup: SimpleHttpConnectionManager.  
Other deprecated ones to remove: SimpleHttpConnectionManager, ClientConnectionManager, PoolingClientConnectionManager, ThreadSafeClientConnManager, SingleClientConnManager, DefaultHttpClient, SystemDefaultHttpClient and ClientConnectionManager.  
**Solution:** Use org.apache.http.impl.conn.PoolingHttpClientConnectionManager and org.apache.http.impl.client.HttpClientBuilder., see Apache doc: [connection management](http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d5e639).

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

@RenderMapping(params=“error=true”)
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

This section now has grown out on its own as [JavaDataAccessPerformance](#Java+Data+Access+Performance).

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

**Observation: Jackson's ObjectMapper is created in each method call.**  
**Problem:** ObjectMapper creation is expensive in time because it does much class loading.  
**Solution:** Since Jackson's ObjectMapper objects are [thread-safe after configuration](https://github.com/FasterXML/jackson-databind/wiki/JacksonFeatures) in one thread, they can be shared afterwards between requests and reused. So, reuse created and configured instances, from a static field.

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

Example code `ThreadLocal` for `XPathFactory`:

```java
private static final ThreadLocal<XPath> parser = ThreadLocal.withInitial(() -> XPathFactory.newInstance().newXPath());
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
    logStatement.append("Found page parameter with key '" + key + "' and value '" + value + "'\n");
}
LOG.debug("Note: {}", logStatement);
```
  

**Problem:** String building, concatenation and/or other operations happen before the debug, trace or info method executes, so independent of the need to actually log. Concatenation is relatively expensive.  
**Solution:** Built the String conditionally on the log level, within an if statement.  
**Rule name:**: AvoidUnconditionalBuiltLogStrings

#### IL03

**Observation: An operation is executed on a log argument, irrespective of log level.** Like:

```java
LOG.debug("ACTUAL DOWNLOAD: EndDate Download Date: {}", String.format("%1$tR", endDateDownloadDate); // bad

LOG.trace("StepExecution: \n{}", stepExecution.toString()); // bad

LOG.debug("Complete Soap response: {}", getSoapMsgAsString(context.getMessage())); // bad
```

**Problem:** toString(), String.format or some other operation or method call is executed irrespective of log level. This may include formatting, concatenation, reflection and other wasteful processing. For example, the above line with toString seems rather harmless, however, you might change your mind if you see the toString implementation of [StepExecution](https://github.com/spring-projects/spring-batch/blob/master/spring-batch-core/src/main/java/org/springframework/batch/core/StepExecution.java) (at the bottom of the page.)

  
**Solution:** Remove the toString() since this is already invoked conditionally inside SLF4J. If formatting is really needed, execute it conditionally or in its toString method.

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

**Solution:** Determine the MDC-value lifecycle and remove the MDC value in the proper place. Put the remove in a finally clause so it is also removed in case of exceptions. See also [Automating access to the MDC](http://logback.qos.ch/manual/mdc.html).

**Rule name:** MDCPutWithoutRemove.

#### IL05

**Observation: Synchonous logging is used for much data.**  
**Problem:** Logging is I/O which can take much time away from the user request/response.  
**Solution:** Use Asynchronous logging. In logback you can use: _ch.qos.logback.classic.AsyncAppender._ Log lines are put in a queue and the user does not have to wait for the actual logging, which is handled in a separate thread. You need to think about i.a. maximum queue size, data loss and what information to transfer to the logging thread (MDC).

Improper Streaming I/O
----------------------

Improper streaming may result in unwanted large object allocations. The IBM JVM has the possibility to log large object allocations to analyse this. Large object allocation logging can be enabled by using the following jvm argument with an appropriate filter value:

```
-Xdump:stack:events=allocation,filter=#6m
```

Logging of the allocation stack traces will be in native_stderr.log.

#### ISIO01

**Observation: ByteArrayOutputStream or StringWriter default constructor is used for large strings/streams.**  
**Problem:** This creates an initial buffer of 32 bytes or 16 characters respectively. If this is not enough, a new byte/char array will be allocated and contents will be copied into the new array. The old array becomes garbage to be collected and copying takes processing time. If you know what the minimum or typical size will be, this garbage and processing time are wasted.  
**Solution:** Presize the ByteArrayOutputStream or StringWriter with an initial capacity such that a resize is not needed in most cases. By using the [ByteArrayOutputStream](http://docs.oracle.com/javase/6/docs/api/java/io/ByteArrayOutputStream.html#ByteArrayOutputStream%28int%29) or [StringWriter](http://docs.oracle.com/javase/6/docs/api/java/io/StringWriter.html#StringWriter%28int%29) alternative constructor.  
If the buffer is send/received through I/O, then the size should be 8192 bytes or a multiple of it, 8 KiB being the default TCP buffer size on most systems. This value of 8 [KiB (kibibyte)](http://en.wikipedia.org/wiki/Kibibyte) is used as default buffer size in all I/O buffering classes in the Java libraries. If the buffer contents is not send or receiceved from I/O, than take 4 kiB or a multiple of it, 4 KiB being the default memory page size.

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

**Observation: A singleton, or more general: an object shared among threads, is used with non-final and/or mutable fields and the fields are not guarded**   (e.g. accessor methods are not synchronized), while typically fields are not intended to change after initialization. This includes Spring @Component, @Controller, @Service and @Repository annotated classes for application and session scope and **JavaEE bean-managed** @Singleton annotated classes.  
**Problem:** Multiple threads typically access fields of a singleton or may access fields in session scoped objects. If a field or its reference is mutable, access is thread-unsafe and may cause corruption or visibility problems. To make this thread-safe, that is, guard the field e.g. with synchronized methods, may cause contention.  
**Solution**: Make the fields final and unmodifiable. If they really need to be mutable, make access thread-safe. Thread-safety can be achieved e.g. by proper synchronization and use the [@GuardedBy](#TUTC04) annotation or use of [volatile](https://www.ibm.com/developerworks/library/j-jtp06197/).

**Notes**

1.  Instances of Date, StringBuilder, URL and File are examples of mutable objects and should be avoided (or else guarded) as fields of shared objects. In case mutable fields are final and not modified after initialization (read-only) they are thread safe, however any modification to it is thread-unsafe. Since field modification is easily coded, avoid this situation.
2.  Instances of classes like ArrayList, HashMap and HashSet are also mutable and should be properly wrapped with e.g. Collections.unmodifiableList after initialization (see [TUTC03](#TUTC03)), or accessed thread-safely with e.g. Collections.synchronizedList or thread-safe implementations like ConcurrentHashMap.
3.  Autowiring/injection is thread safe, yet make sure no other thread-unsafe assignment is made to that field.
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
    private RestTemplate restTemplateOk
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
    private RestTemplate restTemplateOk
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

**Rule names:** AvoidUnguardedMutableFieldsInSharedObjects, AvoidUnguardedAssignmentToNonFinalFieldsInSharedObjects

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
Collections.unmodifiableMap(initializeHashMap())
```

#### IUOC03

**Observation: A HashMap or HashSet is used with Enum keys.**  
**Problem:** A HashMap and HashSet are rather greedy in memory usage.  
**Solution:** Use an EnumMap or EnumSet. It is represented internally with arrays which is extremely compact and efficient.

```java
Map<YourEnumType, String> map = new EnumMap<>(YourEnumType.class);
```

#### IUOC04

**Observation: Elements are searched in a List .** The list is iterated until the right (key, value) element is found.  
**Problem:** the time to find element is O(n); n=length of list.  
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

IREU02

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
**Solution:** Make the field immutable and final. In a constructor, defensively copy the modifiable argument, so also the caller is not able to modify the object referenced by the field anymore. In case of an unmodifiable wrapped collection, make sure the inner collection is not directly reachable anymore after initialization. For example:

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
**Solution**: use destroy to cleanup:

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
**Problem:** Internal state can be modified from outside of the object, by using the reference to the mutable object by the caller of the constructor or setter.  
**Solution:**

1.  Defensively copy the referenced object such that no mutable state is shared.
2.  Use a [builder pattern](http://www.informit.com/articles/article.aspx?p=1216151&seqNum=2) in case of more than a few parameters.
