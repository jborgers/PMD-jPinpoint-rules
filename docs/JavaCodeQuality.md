
Java code quality - pitfalls and best practices
=============================================
By Jeroen Borgers ([jPinpoint](www.jpinpoint.com)) and Peter Paul Bakker ([Stokpop](www.stokpop.com)), sponsored by Rabobank

# Table of contents

- [Introduction](#introduction)
- [Improper use of BigDecimal](#improper-use-of-bigdecimal)
- [Improper amount representation](#improper-amount-representation)
- [Incorrect equals and hashCode](#incorrect-equals-and-hashcode)
- [Potential Session Data Mix-up](#potential-session-data-mix-up)
- [Suspicious code constructs](#suspicious-code-constructs)

Introduction 
------------
We categorized many performance and quality issues based on performance code reviews, load tests, heap analyses, profiling and production problems of various applications. Several of these items are automated into PMD-jPinpoint-rules code checks.
The next items do have performance implications. However, they are actually more concerning general software quality or security.

Improper use of BigDecimal
--------------------------

#### IUOB01

**Observation: the constructor BigDecimal(double) is used.** [This constructor (javadoc)](http://docs.oracle.com/javase/6/docs/api/java/math/BigDecimal.html#BigDecimal%28double%29) 

```
translates a double into a BigDecimal which is the exact decimal representation of the double's binary floating-point value.  

Notes:

1.  The results of this constructor can be somewhat unpredictable. One might assume that writing new BigDecimal(0.1) in Java creates a BigDecimal which is exactly equal to 0.1 (an unscaled value of 1, with a scale of 1), but it is actually equal to 0.1000000000000000055511151231257827021181583404541015625. This is because 0.1 cannot be represented exactly as a double (or, for that matter, as a binary fraction of any finite length). Thus, the value that is being passed in to the constructor is not exactly equal to 0.1, appearances notwithstanding.
2.  The String constructor, on the other hand, is perfectly predictable: writing new BigDecimal("0.1") creates a BigDecimal which is exactly equal to 0.1, as one would expect. Therefore, it is generally recommended that the String constructor be used in preference to this one.
3.  When a double must be used as a source for a BigDecimal, note that this constructor provides an exact conversion; it does not give the same result as converting the double to a String using the Double.toString(double) method and then using the BigDecimal(String) constructor. To get that result, use the static valueOf(double) method.
```
**Problem:** BigDecimal is intended to be used for amounts of e.g. euro’s with cents. It should e.g. represent 0,11 exactly. However, with the used constructor, one easily gets unexpected rounding, e.g.:
```java
System.out.println("result = " + new BigDecimal(0.105).setScale(2, BigDecimal.ROUND\_HALF\_UP));
```
Results unexpectedly in:
````java
result = 0.10
````
**Solution:** Use the factory method BigDecimal.valueOf(double) instead. E.g.:
````java
System.out.println("result = " + BigDecimal.valueOf(0.105).setScale(2, BigDecimal.ROUND\_HALF\_UP));
````
Results as expected in:
````java
result = 0.11
````
Or use long for cents in stead of BigDecimal.

#### IUOB02

**Observation: BigDecimal is instantiated with 0,1 or 10.**  
**Problem:** These instances are already available as BigDecimal.ZERO, BigDecimal.ONE and BigDecimal.TEN  
**Solution:** Use the static instances.

Improper amount representation
------------------------------

Incorrect equals and hashCode
-----------------------------

#### IEAH01

**Observation: Object does not implement equals nor hashCode method.** Often the equals and/or the hashCode methods are missing or incompatible. If equals and hashCode are not well-defined, this means that comparing (with equals) references to these objects is equivalent to finding out whether they refer to the same object, instead of finding out whether the objects are logically equivalent.  
**Problem:** Does not meet programmer expectations and when instances of the class are used as map keys, set elements or certain List operations, this results in unpredictable, undesirable behavior.  
Example where Amount does not define equals nor hashCode method:
````java
Set set = new HashSet();  
set.add(new Amount("0.10"));  
set.add(new Amount("0.10"));  
System.out.println("result = " + set);  
  
Map map = new HashMap();  
map.put(new Amount("0.10"), new Amount("0.10"));  
System.out.println("result = " + map.containsKey(new Amount("0.10")));
````
Results unexpectedly in:
````java
result = [0.10, 0.10]
result = false 
````
**Solution:** Add proper equals and hashCode methods that meet the general contract to all objects which might anyhow be put in a Map, Set or other collection by someone, e.g. the domain objects. See [Effective Java, Chapter 3](http://www.slideshare.net/ibrahimkurce/effective-java-chapter-3-methods-common-to-all-objects). Options to use are:

Three ways to generate the boilerplate code:
1. Use [Project Lombok](https://projectlombok.org/features/index.html) annotation @EqualsAndHashCode, @Value or @Data to generate the boilerplate code
2. Use [Google AutoValue framework](https://github.com/google/auto/blob/master/value/userguide/why.md) (Promoted by Effective Java)
3. Use Immutables framework [Article that compares Lombok, AutoValue and Immutables](https://codeburst.io/lombok-autovalue-and-immutables-or-how-to-write-less-and-better-code-returns-2b2e9273f877)

````
import lombok.*;
class Getters { // bad - equals and hashCode missing
    private String someState1 = "some1";
    private String someState2 = "some2";

    public String getSomeState1() {
        return someState1;
    }
    public String getSomeState2() {
        return someState2;
    }
}

@Getter
class LombokGetterBad { // bad - equals and hashCode missing
    private String someState1 = "some1";
    private String someState2 = "some2";
}

@Getter
@EqualsAndHashCode
class LombokGetterGood { // good  
    private String someState1 = "some1";
    private String someState2 = "some2";
}
````
More traditional ways:
1.  [EqualsBuilder](http://commons.apache.org/lang/api-2.4/org/apache/commons/lang/builder/EqualsBuilder.html) and [HashCodeBuilder](http://commons.apache.org/lang/api-2.4/org/apache/commons/lang/builder/HashCodeBuilder.html), without reflection, see [UUOR01](http://wiki.rabobank.nl/wiki/JavaCodePerformance#UUOR01) above.
2.  The more concise [Google Guava Objects](http://guava-libraries.googlecode.com/svn/trunk/javadoc/com/google/common/base/Objects.html) as discussed at [StackOverflow](http://stackoverflow.com/questions/5038204/apache-commons-equals-hashcode-builder).
3.  Java 7 version using the Objects class, see example below.
4.  Your IDE to generate those methods, possibly using one of the above.

Since we use Java 7+, we recommend the Java 7+ version, example:
````java
public boolean equals(Object obj) {  
    if (obj == this) {  
        return true;  
    }  
    if (obj == null || getClass() != obj.getClass()) {  
        return false;  
    }  
    final AddressO7 other = (AddressO7) obj;  
    return Objects.equals(this.houseNumber, other.houseNumber)  
        && Objects.equals(this.street, other.street)  
        && Objects.equals(this.city, other.city)  
        && Objects.equals(this.country, other.country);  
    }  
}  
  
public int hashCode() {  
    return Objects.hash(houseNumber, street, city, country);  
}
````
**Note:** _Always remember to maintain the equals and hashCode on field changes!_

If you are still not convinced, please read: [Effective Java, Chapter 3](http://www.slideshare.net/ibrahimkurce/effective-java-chapter-3-methods-common-to-all-objects) and recent Dzone article: [why-should-you-care-about-equals-and-hashcode](https://dzone.com/articles/why-should-you-care-about-equals-and-hashcode), or more details in [JavaRanch article](http://www.javaranch.com/journal/2002/10/equalhash.html).

##### Equals and hashCode not designed

In the exceptional case that instances of the class will or should never be checked for equality nor be used in any collection type, or to ease the transition period to properly implement equals and hashCode, we recommend to implement equals and hashCode like:
````java
@SuppressFBWarnings("EQ_UNUSUAL")  
public final boolean equals(Object other) {  
    throw new UnsupportedOperationException("equals not designed.");  
}  
  
public final int hashCode() {  
    throw new UnsupportedOperationException("hashCode not designed.");  
}
````
Added advantage is that you don't need to maintain these methods on field changes. The used annotation suppresses FindBugs violations which otherwise show up in Sonar. You need a dependency in your pom, see next mentioned tool.

**Note**: we created a tool that generates this failing equals and hashcode in your classes and takes care of possible complications.

##### Motivation

[Effective Java, Chapter 3](http://www.slideshare.net/ibrahimkurce/effective-java-chapter-3-methods-common-to-all-objects) explains that equals and hashCode of Object usually don't make sense, they are designed to be overridden. It is very easy to introduce bugs by not overriding them. With the UnsupportedException version you get fail-fast in stead of unexpected, undesired behaviour, yet you don't need to create code which needs maintenance and which is not meant to be used, in fact is just waste.

Tip 1: with the [MeanBean](http://meanbean.sourceforge.net/) test library and [Equals Verifier](http://jqno.nl/equalsverifier/) of Jan Ouwens it is easy to test the equals and hashCode contract. These tests can easily be integrated in the existing UnitTests.

Tip 2: [Project Lombok](https://projectlombok.org/features/index.html) provides annotations for boilerplate code like equals and hashCode. Often no need to maintain the methods on field changes.

**Rule-name:** [ImplementEqualsHashCodeOnValueObjects](https://jira.rabobank.nl/browse/JPCC-21).

#### IEAH02

**Observation: Comparable classes don't override equals()**  
**Problem:** If equals() is not overridden, the equals() implementation is not consistent with the compareTo() implementation. If an object of such a class is added to a collection such as java.util.SortedSet, this collection will violate the contract of java.util.Set, which is defined in terms of equals().  
**Solution:** Implement equals consistent with compareTo.

#### IEAH03

**Observation: Equals and hashCode are inconsistent: they are not based on the same fields or use inconsistent conversion.**  
**Problem:** 
Actually, two cases: 
1. Equal objects can have different hashCodes and end-up in different buckets of a Map/Set. Strange things can happen like adding an object to a Set and not being able to find it back.
2. Two unequal objects can have the same hashCode and end up in the same bucket of a Map. This may result in bad performance, O(n) lookup instead of O(1).   

**Solution:** Use the same fields in equals and hashCode and if conversions are needed, use identical conversions in both. So don't use equalsIgnoreCase.  
**Rule-names:** InconsistentEqualsAndHashCode, MissingFieldInHashCode   
**Examples:**  
````java
class Good {
    String field1, field2;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Good that = (Good) o;
        return Objects.equals(field1, that.field1) &&
                Objects.equals(field2, that.field2);
    }
    public int hashCode() {
        return Objects.hash(field1, field2);
    }
}

class Bad1 {
    String field1;
    String field2; //bad

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bad1 that = (Bad1) o;
        return Objects.equals(field1, that.field1); // field2 missing in equals
    }
    public int hashCode() {
        return Objects.hash(field1, field2); 
    }
}

class Bad2 {
    String field1;
    String field2; //bad

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bad2 that = (Bad2) o;
        if (field1 != null ? !field1.equals(that.field1) : that.field1 != null) return false;
        return field2 != null ? field2.equalsIgnoreCase(that.field2) : that.field2 == null; // bad: ignore case - inconsistent conversion
    }
    public int hashCode() {
        int result = field1 != null ? field1.hashCode() : 0;
        result = 31 * result + (field2 != null ? field2.hashCode() : 0);
        return result;
    }
}
````


#### IEAH04

**Observation: A field simply assigned to is missing in the equals method**  
**Problem:**  If a field which can be assigned separately (independent of other fields) is missing in the equals method, then changing that field in one object has no effect on the equality with another object.
However, if a field of one of two equal objects is changed, the expectation is that they are no longer equal. In other words, objects are typically only considered equal when all their fields have equal values. 
**Solution:** include the missing field in the equals and hashCode method.  
**Examples:**
````java
class Bad1 {
    String field1;
    String final field2; // bad, missing in equals

    public Bad1(String arg2) {
        field2 = arg2;
    }
    public void setField1(String arg1) {
        field1 = arg1;
    }
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bad1 that = (Bad1) o;
        return Objects.equals(field1, that.field1);
    }
    public int hashCode() {
        return Objects.hash(field1);
    }
}
````
**Rule-name:** MissingFieldInEquals

Potential Session Data Mix-up
-----------------------------

Session data mixup is one of the worst problems that can occur. Customers seeing data of other customers is bad, for the users and for the reputation of the company. Therefore, we want to have defence mechanisms to protect against these problems. There can be several causes of session data mixup, like:

*   a response over a connection being bound to the wrong request, so request-response mixup.
*   shared variables like singleton fields, e.g. a Spring controller
*   cache key mixup.

#### PSDM01

**Observation: a response from a service call is not validated on business level with the request**   
**Problem:** Software infrastructure (pool, queue) with problems like misconfiguration or bugs, can cause a response being returned for a different request than the original. So a request-response mixup.   
**Solution:** Check if a business value like customerId or accountNumber is equal for the request and the response. If not, log an error and do *not* show the data to the user.   

Suspicious code constructs
--------------------------

### SSC01

**Observation: Multiple switch cases contain the same assignment**  
**Problem:** Identical assignments to the same variable are very likely a bug. It lead to a production incident in a project.  
**Solution:** Each case block of a switch should contain unique assignments. Common assignments should be taken out of the switch construct. Exceptional case: a duplicate assignment to a boolean is considered safe since it can only hold 2 values.    
**Perf-code-check:** [AvoidDuplicateAssignmentsInCases](https://jira.rabobank.nl/browse/JPCC-89)

