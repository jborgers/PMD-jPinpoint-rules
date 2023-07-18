
Java Data Access Performance - performance pitfalls and best practices
===================================================================

By Jeroen Borgers ([jPinpoint](www.jpinpoint.com)) and Peter Paul Bakker ([Stokpop](www.stokpop.com)), sponsored by Rabobank

# Table of contents

- [Introduction](#introduction)
- [Too many Rows in Memory](#too-many-rows-in-memory)
- [Power of the Database Not utilized](#power-of-the-database-not-utilized)
- [Power of the Framework is Not utilized](#power-of-the-framework-is-not-utilized)
- [Too many returned Rows or Roundtrips](#too-many-returned-rows-or-roundtrips)
- [Too many Columns Transferred](#too-many-columns-transferred)
- [Batch and Mass Statements not utilized](#batch-and-mass-statements-not-utilized)
- [IN-operator with many or a varying number of values](#in-operator-with-many-or-a-varying-number-of-values)
- [JDBC/database resource leaks](#jdbcdatabase-resource-leaks)
- [Incorrect Use of database Statements](#incorrect-use-of-database-statements)
- [Entity Equals and HashCode not properly defined](#entity-equals-and-hashcode-not-properly-defined)
  
Introduction
------------

We observed the following pitfalls in performance code reviews of various projects. Based on these items, we have created code checks. Each pitfall is described for itself, and for some specific data access technologies when applicable/experienced. These technologies include Hibernate, JPA, Spring, jdbc and MyBatis.

If you use JPA, see the [How to improve JPA performance by 1_825%](http://java-persistence-performance.blogspot.nl/2011/06/how-to-improve-jpa-performance-by-1825.html) blog entry.

See also our [Java Performance Pitfalls series on YouTube](https://www.youtube.com/playlist?list=PL9rzqHHCiIVQr27ZsP0_r8eOgQMTbhJfF), especially [on the performance pitfall: database access.](https://youtu.be/DfwqmkiBhFw?list=PL9rzqHHCiIVQr27ZsP0_r8eOgQMTbhJfF).

### IDA: Inefficient Data Access - Pitfall identifiers

The abbreviation IDA means: Inefficient Data Access. The second part of pitfall identifier is the abbreviation of the pitfall category plus a sequence number, for instance, IDA-TRM01 is short for: Inefficient Data Access - Too many Rows in Memory - number 1.

Too many Rows in Memory
-----------------------

### IDA-TRM01

**Observation: All query results are fetched at once.**  
**Problem:** All result rows and generated entities are stored in memory and this may take much space introducing long gc times and risk of an out of memory situation.  
**Solution:** Use an iterator to iterate through the set of result rows while processing them, and utilize setFetchSize. Set fetch size for each query such that in most cases the number of rows actually returned/processed just fits in that size. Note that for sizes larger than one hundred, memory usage may become an issue, depending on how much data is in the returned columns/rows. So, only set it higher than 100 if you know there is only little data returned per row, like 3 rather short columns.

  

#### MyBatis details

**Problem:**

Statements which return many values, called on a mapper or on the SqlSession from MyBatis API:
````java
List empMapper.findEmployees(param);
 
List sqlSession.selectList("findEmployees", param);
````
The whole list is read into memory.

**Solution:** Use a SqlSession and a ResultHandler for more control. Stream the results and process resultObject by resultObject:
````java
sqlSession.select("findEmployees", param, handler);
 
ResultHandler<T> {
   void handleResult(ResultContext context) {
	 doSomethingWithEmployee(context.getResultObject());
 }
````
See [MyBatis SqlSession documentation](http://www.mybatis.org/mybatis-3/java-api.html#SqlSession).

[This Stack overflow answer](https://stackoverflow.com/questions/6546136/handling-very-large-amount-of-data-in-mybatis#comment-18653467) (2nd answer, remark) suggests to add an annotation:
````java
@Options(fetchSize = Integer.MIN_VALUE)
````
to the statement.

#### OpenJPA details

OpenJPA has [a way to deal with large result sets](http://openjpa.apache.org/builds/1.2.3/apache-openjpa/docs/ref_guide_dbsetup_lrs.html). However, this way with a Delegating Collection proxy is confusing and can have unpleasant surprises if you e.g. try to use the collection outside of the transaction context. Therefore, we recommend to use an [EntityIterator]() approach.

### IDA-TRM02

**Observation: A batch update is executed for more than 100 rows.**  
**Problem:** All rows are stored in memory and this may take much space introducing long gc times and risk of an out of memory situation.  
**Solution:** Execute multiple batch updates with a maximum of 100 statements. If the data per update is small, e.g., 1-4 columns with simple data, then consider to use more, e.g. max. 1000 statements.

#### Hibernate details
````java
private static final COMMIT_INTERVAL = 10000; // too much
    
    connection.setAutoCommit(false);
    ps = connection.prepareStatement(UPDATE_APO);

    for (Payment payment : payments) {
        ps.setDate(1, executionDate));
        ps.setInt(2, status);
        ps.addBatch();
        if (COMMIT_INTERVAL > 0 && (counter % COMMIT_INTERVAL == 0)) {
            ps.executeBatch();
            connection.commit();
            ps.clearBatch();
        }
    }
    if (itemsInBatch) {
        ps.executeBatch();
        connection.commit();
    }
````
### IDA-TRM03

**Observation: A query fetch size of >100 is used.**  
**Problem:** >100 result rows are stored in memory and depending on how much data is in the returned columns/rows, this takes much space introducing long gc times and risk of an out of memory situation.  
**Solution:** Set fetch size to <=100. Only set it higher than 100 yet still <= 500, if you are sure there is only little data returned per row, like 3 rather short columns.

Power of the Database Not utilized
----------------------------------

### IDA-PDN01

**Observation: All rows are fetched to e.g. count them.**  
**Problem:** All row data is transferred from database to application and then the aggregate function is applied, e.g. to count them. Transferring this row data takes time, unnecessarily.  
**Solution:** Execute the aggregate function in the database and only transfer the end result, e.g. the result of the counting of rows. Note that there are several more aggregate functions like SUM, MIN, MAX, AVG, see [Oracle Aggregate Functions](https://docs.oracle.com/database/121/SQLRF/functions003.htm#SQLRF20035).

  

#### MyBatis details

**Problem**
````java
int numEmployees = (empMapper.findEmployees(param)).size();
boolean existEmployees = (empMapper.findEmployees(param)).size() > 0;
````
****Solution****

Create an explicit statement on a mapper to count:
````xml
<mapper namespace="com.example.EmployeeMapper">
   <select id="countEmployees" resultType="int">
      select count(*) from employee
   </select>
</mapper>
````
and use it:
````java
int numEmployees = empMapper.countEmployees();
boolean existEmployees = empMapper.countEmployees() > 0;
````
Power of the Framework is Not utilized
--------------------------------------

### IDA-PFN01

**Observation: Read-only mode is not utilized.**  
**Problem:** Entity objects read from the database which are not updated are managed by the persistence framework. These managed objects are checked for changes, its properties and associations, and its version may be updated and the entity persisted. This takes time and memory.  
**Solution:** Use read-only mode for query results which do not change.

#### Hibernate details
````java
 Query q = session.createQuery("yourQuery");
 q.setReadOnly(true);
 return q.list();
````
#### JPA details

The Column annotation and XML element defines insertable and updatable options. These allow for this column, or foreign key field to be omitted from the SQL INSERT or UPDATE statement. These can be used if constraints on the table prevent insert or update operations. Setting both insertable and updatable to false, effectively mark the attribute as read-only. See [Java Persistence wiki book](http://en.wikibooks.org/wiki/Java_Persistence/Basic_Attributes#Insertable.2C_Updatable_.2F_Read_Only_Fields_.2F_Returning).
````java
 @Column(name="USER_ID", nullable=false, length=30, updatable=false, insertable=false) 
 private String userId;
````
For JPA implemented by Hibernate, you can use the following way to get to the Hibernate session and set readOnlyMode on the query, see above:
````java
 Session session = entityManager.unwrap(Session.class);
````
EclipseLink provides a `"eclipselink.read-only"="true"` query hint that allows the persistence context to be bypassed.
````java
 query.setHint("eclipselink.read-only", "true");
````
  
Too many returned Rows or Roundtrips
---------------------------------------

### IDA-TRR01

**Observation: No fetch size is specified for SELECT queries.**  
**Problem:** When you read rows from a ResultSet, under the hood JDBC will fetch 10 rows at a time, in a roundtrip to the database. This default fetch size of 10 is likely not optimal for every query. If you execute a query which returns many rows and you only use the first row, will fetch 10 rows in a round trip, so the 9 last rows are fetched unnecessarily. If your query returns 1000 rows, a second round trip to the database will be made when your iteration reaches the 11th row, the third with the 21st row, and so on. So it makes 100 round trips unnecessarily.  
**Solution:** Set fetch size for each query such that in most cases the the number of rows actually returned/processed just fits in that size. Note that for sizes larger than one hundred, memory usage may become an issue, depending on how much data is in the returned columns/rows. So, only set it higher than 100 if you know there is only little data returned per row, like 3 rather short columns. See [JDBC Developers Guide](http://docs.oracle.com/cd/A87860_01/doc/java.817/a83724/resltse5.htm).

We [presented on performance pitfall database access, especially on fetch size](https://youtu.be/DfwqmkiBhFw?list=PL9rzqHHCiIVQr27ZsP0_r8eOgQMTbhJfF).  

#### JDBC details

Example:
````java
statement.setFetchSize(1);
prepStatmt.setFetchSize(500);
resultSet.setFetchSize(5); // this is for subsequent round trips
````
Note that setting fetch size on ResultSet has no influence on the first, yet only on the subsequent round trips.

#### Spring details

Spring JdbcTemplate can be parameterized as follows:
````java
 jdbcTemplate.setFetchSize(1);
````
If you want to use SimpleJdbcTemplate it is a bit more tedious to achieve, possible this way:
````java
 ((JdbcTemplate)simpleJdbcTmpl.getJdbcOperations()).setFetchSize(100);
````
Note that in this way, the fetch size is set for all queries with that template, not just for one query.

#### MyBatis details

Specify the fetchSize attribute of the select element in the mapper:
````xml
<mapper namespace="com.example.EmployeeMapper">
   <select id="findAllEmployees" resultType="Employee" fetchSize="100">
      select * from employee 
   </select>
</mapper>
````
#### Hibernate details
````java
 Query q = session.getNamedQuery("yourQuery");
 q.setFirstResult(getOffset());
 q.setMaxResults(getResultsPerPage());
 q.setFetchSize(getResultsPerPage());
 return q.list();
````
Hibernate doesn't optimize this for `Query.uniqueResult` so you need to `setFetchSize(2)` also for these queries, besides `setMaxResults(2)`.

You can for a whole persistence unit set the new default fetch size with:
```xml
<property name="hibernate.jdbc.fetch_size">100</property>
```
This can be an easy general performance improvement to change for a whole persistence unit from the default fetch size of 10. However, setting the fetch size for each query individually is the better solution.

#### JPA details

Unfortunately, JPA does not (yet) support setFetchSize directly: queries created from EntityManager do not provide the method. 
It needs to be called in an implementation-specific way. For JPA implemented by Hibernate, you can use the following way to get to the Hibernate session:
````java
 Session session = entityManager.unwrap(Session.class);
````
Alternative is to use query hints:
````java
 query.setHint("org.hibernate.fetchSize", "100");
 query.setHint("eclipselink.JDBC_FETCH_SIZE", "100"); 
 query.setHint("openjpa.FetchPlan.FetchBatchSize", "100"); // not sure this one actually works, to verify
````
\- See more at: [eclipselink doc](http://www.eclipse.org/eclipselink/documentation/2.4/jpa/extensions/q_jdbc_fetch_size.htm#CHDBEBDE)

With Spring Data JPA:
````java
 @QueryHints(value = { @QueryHint(name = "org.hibernate.fetchSize", value = "5") })
 List<User> findByLastname(String lastname);
````
With OpenJPA explicitely:
````java
OpenJPAQuery ojpaQuery = OpenJPAPersistence.cast(query);
JDBCFetchPlan plan = (JDBCFetchPlan) ojpaQuery.getFetchPlan();
plan.setFetchBatchSize(20);
````
OpenJPA doesn't optimize this for `Query.getSingleResult`, so you need to `setFetchBatchSize(2)` also for these queries, besides `setMaxResults(2)`.

You can set the value for a whole persistence unit to a new default value with Open JPA as follows:

```xml 
<property name="openjpa.FetchBatchSize" value="20"/>
```

### IDA-TRR02

**Observation: No max rows / max results is specified for SELECT Statements where only a fixed number of rows are used.**  
**Problem:** In case only the first result row or a certain number is actually used, possibly too many rows are queried on the database and transferred over the network. All rows may not fit in memory. Fetch size determines the size of the row chunk per database round trip, yet does not limit the size of a ResultSet of a SELECT query.  
**Solution:** Set max rows at the maximum number of rows ever used for this particular query. If only the first row is used, set max size at 1. If the number of rows used is limited to 25, set max rows at 25. Rows returned from the database above 25 will be silently dropped. This protects against out of memory situations.

#### JDBC details

See [Javadoc](http://docs.oracle.com/javase/7/docs/api/java/sql/Statement.html#setMaxRows(int)). Example:
````java
 prepStmt.setMaxRows(25);
````
Note that the setMaxRows only limits/affects the client's side JDBC Object. To also optimize the database query itself, use database specific SQL, ROWNUM for Oracle to create a different query, like:
````sql
SELECT * FROM employees WHERE ROWNUM < 11;
````
and if you want to sort use a subquery:

```sql
SELECT * FROM
   (SELECT * FROM employees ORDER BY employee_id)
   WHERE ROWNUM < 11;
```

Still use of setMaxRows is recommended on this optimized query as an extra safety measure against out of memory situations. 
If you want to use pagination, see next item. See [Oracle SQL documentation](https://docs.oracle.com/cd/B14117_01/server.101/b10759/pseudocolumns008.htm).

#### MyBatis details

With MyBatis you can use the JDBC approach using database specific SQL, ROWNUM for Oracle.

#### Hibernate details

Hibernate uses (for Oracle) the pseudo column ROWNUM to optimize the query.
````java
 query.setMaxResults(1);
````
Hibernate doesn't optimize this for `Query.uniqueResult`, so you need to `setFetchSize(2)` and `setMaxResults(2)`.

  

#### JPA details

JPA supports setMaxResults directly:
````java
 query.setMaxResults(2);
````
OpenJPA doesn't optimize this for `Query.getSingleResult`, so you need to `setMaxResults(2)` besides `setFetchBatchSize(2)` also for these queries.

### IDA-TRR03

**Observation: No pagination is used for data access where the user requests page by page.**  
**Problem:** All result rows of the query are searched and returned even if the user only wants to see the first page. Unnecessary work is performed.  
**Solution:** Use pagination for data access.

#### JDBC details

To optimize the database query, use database specific SQL, ROWNUM for Oracle. Like:
````sql
SELECT * 
  FROM ( SELECT /*+ FIRST_ROWS(n) */ 
  a.*, ROWNUM rnum 
      FROM ( SELECT * FROM payments ORDER BY payment_id ) a 
      WHERE ROWNUM <= 
      :MAX_ROW_TO_FETCH ) 
WHERE rnum  >= :MIN_ROW_TO_FETCH;
````
There is some trickery here, explained at [Ask Tom on ROWNUM](http://www.oracle.com/technetwork/issue-archive/2006/06-sep/o56asktom-086197.html). Order by should be of something unique.

#### MyBatis details

The RowBounds object is meant for this, however, it seems that MyBatis leaves optimization to the SQL driver. For Oracle, the ROWNUM clause is not utilized then. Therefore, use the JDBC for Oracle approach in stead.

#### Hibernate details
````java
Query q = session.getNamedQuery("yourQuery");
q.setFirstResult(getOffset());
q.setMaxResults(getResultsPerPage());
q.setFetchSize(getResultsPerPage());
return q.list();
````
**Note on changing data:** In case the data returned is changing while requesting pages, the results may be unexpected. E.g. when the first page contains last 5 lines with status is ACTIVE, which changes before page two is requested to CANCELLED, and you are searching for ACTIVE ones only, going from page 1 to page 2 will skip 5 lines which in the new situation would be on page 1. If this is considered a problem, then paging should be over one snapshot taken at the moment of requesting the initial page. Question is then where to store that snapshot. Fortunately Oracle has a nice feature for this: the [flashback query](https://docs.oracle.com/cd/B28359_01/appdev.111/b28424/adfns_flashback.htm#i1008579).

#### JDBC details

For the subquery mentioned above, the flashback query looks like:
````sql
SELECT * FROM payments AS OF TIMESTAMP :TIME_OF_INITIAL_PAGE ORDER BY payment_id
````
Note that the amount of time going back is limited, depending on Oracle configurations. If it is not available an error will occur, which should be dealt with by the application, for example:
````
ERROR at line 1:
ORA-01555: snapshot too old: rollback segment number 1 with name "_SYSSMU_1707829$" too small
````
### IDA-TRR04

**Observation: All query result rows are requested while only one element should be returned.**  
**Problem:** Unnecessarily heap is allocated in JDBC buffers. Unnecessary work is performed.  
**Solution:** An API call which is meant for fetching the one and only result row. This also validates the fact that only one can be returned.  
**Rule name:** AvoidFetchingWholeList

#### Hibernate details

Problem:
````java
Query q = getCurrentSession().createQuery(query);
List resultList = q.list();
if (resultList != null && !resultList.isEmpty()) {
 nrOfItems = ((Long) q.list().get(0)).intValue();
}
````
  

Solution:
````java
Query q = getCurrentSession().createQuery(query);
q.setMaxResults(2).setFetchSize(2);
try {
    Object nr = q.uniqueResult();
    if (nr != null) {
        nrOfItems = ((Long) nr).intValue();
    }
}
catch(NonUniqueResultException e) { /* handle it */ }
````
**Be aware!** that `setFetchSize` and `setMaxResults` are still needed because internally Hibernate calls `q.list()` without these optimizations. 
Adding these optimizes for the only valid number of results: 1 and it protects against memory overload of wrong queries while still allowing the exception to be thrown with maxResults.

#### JPA details
````java
query.setMaxResults(2);
// add query setFetchBatchSize(2) specific for JPA implementation, see above
try {
    Object nr = query.getSingleResult();
    if (nr != null) {
        nrOfApos = ((Long) nr).intValue();
    }
}
catch(NoResultException|NonUniqueResultException e) { /* handle it */ }
````
**Be aware!** that `setFetchSize` and `setMaxResults` are still needed because internally OpenJPA calls `executeReadQuery` to return a List without these optimizations. Adding these optimizes for the only valid number of results: 1 and it protects against memory overload of wrong queries while still allowing the exception to be thrown, with maxResults.

### IDA-TRR05

**Observation: A query is unnecessarily executed more than once.**  
**Problem:** Time is taken by the unnecessary roundtrip(s). Unnecessary work is performed.  
**Solution:** Execute the query only once.  
**Rule name:** AvoidMultipleRoundtripsForQuery

#### JPA details

Problem:
````java
public ReasonAdditionalInformation findReasonAdditionalInformationByCode(String code) {
   Query q = this.entityManager.createQuery("SELECT reas FROM ReasonAdditionalInformation reas WHERE reas.code = :code", ReasonAdditionalInformation.class);
   q.setParameter("code", code);
   List results = q.getResultList(); // first roundtrip
   if (results!= null && !results.isEmpty()) {
      return (ReasonAdditionalInformation)q.getSingleResult(); // second roundtrip
   }
   return null;
}
````
  

Solution:
````java
public ReasonAdditionalInformation findReasonAdditionalInformationByCode(String code) {
   Query q = this.entityManager.createQuery("SELECT reas FROM ReasonAdditionalInformation reas WHERE reas.code = :code", ReasonAdditionalInformation.class);
   q.setParameter("code", code);
   List results = q.getResultList();
   if(results!= null && !results.isEmpty()){
      if (results.size() > 1) {
         // throw some exception
      }
      return (ReasonAdditionalInformation)results.get(0);
   }
   return null;
}
````
Too many Columns Transferred
----------------------------

### IDA-TCT01

**Observation: More columns of a table are put in the SQL `UPDATE` statement than are actually changed**  
**Problem:** Column values which are not changed are unnecessarily transferred to the database.  
**Solution:** Only update (and transfer) the changed columns. This is achieved by ORM tools in different ways.

#### Hibernate details

Setting `dynamic-update="true"` for Hibernate entities means that for updates (including batch updates) only the changed properties/columns are included in the Hibernate generated SQL `UPDATE` statement. 
This can much improve performance and should always be applied in case updates happen on the entity. Example:
````xml
<hibernate-mapping>
 <class table="ACCOUNTINGREQUEST" name="com...AccountingRequest" optimistic-lock="version" dynamic-update="true" >
````
With annotations:
````java
@org.hibernate.annotations.Entity(dynamicUpdate = true)
````
And for Hibernate 4+
````java
@DynamicUpdate
````
#### JPA details

*   EclipseLink inserts all columns and updates only the changed ones, so `dynamicInsert=false` and `dynamicUpdate=true`.
*   For OpenJPA I cannot find any documentation on this topic.

### IDA-TCT02

**Observation: All columns of a table are put in the INSERT statement while some of them are actually NULL**  
**Problem:** Column values which are NULL are unnecessarily transferred to the database.  
**Solution:** Only insert (and transfer) the non-NULL columns. This is achieved by ORM tools in different ways.  
**Caution:** Be very careful with applying this for tables which have columns declared as 'non-NULL' or 'non-NULL default' values. Make sure to have the non-null and the same default value respectively on the created entity to have the same values in memory as in the database.

#### Hibernate details

Setting `dynamic-insert="true"` for Hibernate entities means that for inserts (including batch inserts) only the non-NULL properties/columns are included in the Hibernate generated SQL `INSERT` statement. This can improve performance. Example:
````xml
<hibernate-mapping>
 <class table="ACCOUNTINGREQUEST" name="com...AccountingRequest" optimistic-lock="version" dynamic-update="true" dynamic-insert="true">
````
With annotations:
````java
@org.hibernate.annotations.Entity(dynamicInsert = true)
````
And for Hibernate 4+
````java
@DynamicInsert
````
#### JPA details

*   EclipseLink inserts all columns and updates only the changed ones, so dynamicInsert=false and dynamicUpdate=true.
*   For OpenJPA I cannot find any documentation on this topic.

### IDA-TCT03

**Observation: All columns (\*) of a table are requested in the SELECT statement while only a subset is actually needed.**  
**Problem:** Column values which are not needed are unnecessarily transferred from the database.  
**Solution:** Only request (and transfer) the needed columns.

JPA and Hibernate don't have standard facilities for this. Native SQL is needed. (TODO: describe more)

Batch and Mass Statements not utilized
--------------------------------------

### IDA-BMS01

**Observation: Many update or insert statements are executed on entities with a roundtrip per (prepared)statement.**  
**Problem:** Each round trip takes considerable time. Many statements and many roundtrips take much time.  
**Solution:** Utilize JDBC batch updates: send many statements in one roundtrip.  
**Caution:** Take care of flushing a batch of inserts/updates and release memory after they have been saved.

#### Hibernate details

Simple example:
````java
Session session = sessionFactory.openSession();
Transaction tx = session.beginTransaction();
  
for (int i=0; i < 100000; i++) {
    Customer customer = new Customer(..);
    session.save(customer);
    if (i % 20 == 0) { //20, same as the JDBC batch size
        //flush a batch of inserts and release memory:
        session.flush();
        session.clear();
    }
}
tx.commit();
session.close();
````
  

The property `hibernate.jdbc.batch_size` determines the number of statements added to a batch by hibernate before it calls jdbc-executeBatch and they get sent to the database in one roundtrip. Also some other properties need to be set and a condition need to be met in order to make it work properly. From [this post](http://stackoverflow.com/questions/12011343/how-do-you-enable-batch-inserts-in-hibernate): To enable batching for both `INSERT` and `UPDATE` statements, you need to set all the following Hibernate properties:
````xml
<property name="hibernate.jdbc.batch_size">30</property>
<property name="hibernate.order_inserts">true</property>
<property name="hibernate.order_updates">true</property>
<property name="hibernate.jdbc.batch_versioned_data">true</property>
````
In addition, you should not use IDENTITY generators, since it disables batch fetching. With the ordering, statements on different entities will be grouped together, needed for hibernate to batch them. To deal with automatically versioned data the last property is needed to be set to true. I find [this](http://vladmihalcea.com/2015/03/18/how-to-batch-insert-and-update-statements-with-hibernate/)and [this article](https://abramsm.wordpress.com/2008/04/23/hibernate-batch-processing-why-you-may-not-be-using-it-even-if-you-think-you-are/) helpful to explain this.

#### JPA-EclipseLink details

To enabled batch writing in EclipseLink, add the following to persistence unit property;
````
"eclipselink.jdbc.batch-writing"="JDBC"
````
You can also configure the batch size using the "eclipselink.jdbc.batch-writing.size" persistence unit property. The default size is 100.
````
"eclipselink.jdbc.batch-writing.size"="1000"
````
See [eclipselink-batch-writing](http://java-persistence-performance.blogspot.nl/2013/05/batch-writing-and-dynamic-vs.html)

#### OpenJPA details

Statement batching should be enabled by default for any JDBC driver that supports it. When batching is on, OpenJPA should automatically orders its SQL statements to maximize the size of each batch. You can try to enable the statement batching by setting the batchLimit in the following property value.
````xml
<property name="openjpa.jdbc.DBDictionary" value="oracle(batchLimit=1000)"/>
````
Default value is 100.

The following property may also help:
````xml
<property name="openjpa.jdbc.UpdateManager" value="org.apache.openjpa.jdbc.kernel.BatchingOperationOrderUpdateManager"/>
````
This is rather unclear from the [OpenJPA documentation](http://openjpa.apache.org/builds/1.2.3/apache-openjpa/docs/ref_guide_dbsetup_stmtbatch.html).

A [WebSphere specific alternative configuration](https://www.ibm.com/support/knowledgecenter/SSAW57_8.0.0/com.ibm.websphere.nd.doc/info/ae/ae/tejb_jpabatch.html) is as follows:
````xml
<property name="openjpa.jdbc.UpdateManager" value="[com.ibm.ws](http://com.ibm.ws).persistence.jdbc.kernel.OperationOrderUpdateManager(batchLimit=1000)"
````
  

We found in a project that it is not working. Solved with a BatchHandler class based on Hibernate to deal with this in a proper way.

  

### IDA-BMS02

**Observation: The same update statement is executed for many rows of a table.**  
**Problem:** Each update statement is a roundtrip to the database. This round trip takes substantial time, many round trips take a lot of time.  
**Solution:** Rewrite the iteration of updates into:

*   preferably one **mass update** (aka bulk update) statement. This only needs one roundtrip to the database and one statement to be executed by the database. Simple arithmetic like +1 is also possible in such a mass update.
*   In case the bind variable(s) need to be different for different rows, this may not be possible. In that case, use a batch update: many statements in one round trip. (Or actually 1 prepared statement with bind variables which are different for different rows.)

**Caution!:** Be aware that a mass update bypasses the Object Relational Mapper (JPA/Hibernate). This may result in stale objects in sessions/caches, these should be properly flushed before the mass update. In addition, in case of optimistic locking, the version column is not updated. Therefore, it needs to be updated as part of the bulk update. Moreover, the bulk update does not do optimistic locking by itself, so updates of other threads during the mass update are not taken into account and may be overwritten/get lost.

#### JPA details

JPA 2.1+ has support for bulk update and delete by Criteria API. See [criteria-updatedelete](http://www.thoughts-on-java.org/criteria-updatedelete-easy-way-to/) .

### IDA-BMS03

**Observation: A batch update statement is executed for many rows of a table, with the same or a calculatable bind variable values for all rows.**  
**Problem:** Many statement bind variable values are transferred to the database and many update statements are executed by the database.  
**Solution:** Rewrite the batch update into one **mass update** statement. This also needs only one roundtrip to the database, yet only one statement to be executed by the database.  
Simple arithmetic like +1 is also possible in a mass update.  
**Caution!:** Be aware that a mass update bypasses the Object Relational Mapper (JPA/Hibernate). This may result in stale objects in sessions/caches, these should be properly flushed before the mass update. In addition, in case of optimistic locking, the version column is not updated. 
Therefore, it needs to be updated as part of the bulk update. Moreover, the bulk update does not do optimistic locking by itself, so updates of other threads during the mass update are not taken into account and may be overritten/get lost.

#### JPA details

JPA 2.1+ has support for bulk update and delete by Criteria API. See [criteria-updatedelete](http://www.thoughts-on-java.org/criteria-updatedelete-easy-way-to/) .

IN-operator with many or a varying number of values
---------------------------------------------------

### IDA-INO01

**Observation: A query IN-Operator is used with many/varying number of explicit values in a select query or mass update.**  
**Problem:**

1.  the number of values for the IN-argument list is limited, in Oracle to 1000, an error occurs when exceeding this limit and the query fails;
2.  a large IN list takes substantial time to transport to the database and be parsed;
3.  each number of IN values in a query results in a separate cache entry and [thrashes caches](https://www.quora.com/What-is-cache-thrashing), i.e. the database cache, the Prepared Statement Cache of the application server, and the JPA/Hibernate Query Plan Cache. It pollutes those caches, resulting in higher memory usage and/or lower cache hit ratio.

**Solution:** Rewrite the query by replacing the IN-argument list by a sub query using the criteria used to fetch the IN arguments. Or often even better performing, an inner join using these criteria (depending on indexes etc. - recommended to test to be sure.) 
This way, the select and update are combined into one, which will also save one round-trip. An other option would be to use custom user types.  
**Rule name**: AvoidSqlInExpression

JDBC/database resource leaks
----------------------------

### IDA-JRL01

**Observation: JDBC resources are not properly released, or not always.**  
**Problem:** These resources like database connections are limited and when not closed/returned lead to connection unavailability or out of memory situations for either the JDBC client or database server side. For instance a database error: ORA-01000: maximum open cursors exceeded.  
**Solution:** Make sure the resources are always closed, e.g. within a finally clause.

#### JDBC details

In plain JDBC this is quite cumbersome:
````java
 Connection con = dataSource.getConnection();
try {
   Statement stmt = con.createStatement();
   try {
      ResultSet rs = stmt.executeQuery("some query");
      try {
         // Do stuff with the result set.
      } finally {
         rs.close();
      }
   } 
   finally {
      stmt.close();
   }
} finally {
   con.close();
}
````
Note that in this example we don't catch and handle exceptions, which would be needed for real life code.

Java 7+ try-with-resources and AutoCloseable makes this example much easier:
````java
 try(Connection con = dataSource.getConnection(); Statement stmt = con.createStatement(); 
    ResultSet rs = stmt.executeQuery("some query")) {
   // Do stuff with the result set.
}
````
#### Spring details

Spring provides a JDBCTemplate and an HibernateTemplate to easily deal with closing these resources in a proper way.

Incorrect Use of database Statements
------------------------------------

### IDA-IUS01

**Observation: JDBC Prepared Statements or Callable Statements are built with concatenating variables.**  
**Problem:** PreparedStatements (and CallableStatements) are meant to be used with bind variables only. Using concatenation will generate multiple entries in the Statement cache of the app server, increasing cache misses and increasing memory usage of the cache. Additionally for security, enables SQL-injection.  
**Solution:** Use proper bind variables only.

#### JDBC details

Below example uses inappropriate concatenation of status and a proper bind variable for reference_nr.
````java
    PreparedStatement pStmt = null;
    try {
        String sqlQry = "UPDATE P_EXECUTION set status = '"
                + status
                + "' WHERE out_payment_id in (select payment_id from payment_item where ref_nr = ?)";
        pStmt = conn.prepareStatement(sqlQry);
        pStmt.setString(1, paymentInput.getRefNumber());
        pStmt.addBatch();
        pStmt.executeBatch();
    } finally {
        pStmt.close();
    }
````
Entity Equals and HashCode not properly defined
-----------------------------------------------

### IDA-EEH01

**Observation: Equals and hashCode are only based on the primary key**    
**Problem:** When the entity objects don't have a primary key yet, equality is not properly defined.   
**Solution:** Use a business key consisting of the fields that have a non-database-technical yet business meaning and which define the logical equality.    
**See:** [jpa-entity-equality](https://www.baeldung.com/jpa-entity-equality) [effective-java-equals-and-hashcode/](http://www.ideyatech.com/effective-java-equals-and-hashcode/) and [dont-let-hibernate-steal-your-identity](http://www.onjava.com/pub/a/onjava/2006/09/13/dont-let-hibernate-steal-your-identity.html?page=1).
