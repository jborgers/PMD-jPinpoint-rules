# PMD-jPinpoint-rules
Static code checking rules for responsible Java and Kotlin programming built on PMD, sponsored by [Rabobank](https://www.rabobank.com/).

These rules are on performance, sustainability, multi-threading, data mix-up, and more.

## Purpose
The purpose of this project is to create, manage and share these rules, to code better software together: 
better software which is faster, uses less resources, has a smaller ecological footprint, is more stable, more confidential, with less effort and lower cost. 
More general, to promote responsibility by considering the concerns of the user, the environment, the engineer, the community and the company.

We have distilled these code checks from what we learned in several years of analyzing performance problems and failures found in code, tests and production situations. And the ruleset is growing every month.

We didn't find these checks in other places, like the standard PMD, FindBugs/Spotbugs, Checkstyle or Sonar rules.
We are working with the PMD-team to move some of the jpinpoint rules in the standard rule set, as well as make PMD suitable for Kotlin.

## How to use the rules

Run the jPinpoint rules from the command-line using the PMD tool, from your favorite development
environment with a PMD-plugin, or in SonarQube with the [sonar-pmd-jpinpoint plugin](https://github.com/jborgers/sonar-pmd-jpinpoint) 
next to the [Sonar pmd plugin](https://github.com/jborgers/sonar-pmd).

Documentation of the rules are here:
* [JavaCodePerformance](docs/JavaCodePerformance.md)
* [JavaDataAccessPerformance](docs/JavaDataAccessPerformance.md)
* [JavaCodeQuality](docs/JavaCodeQuality.md)

## License
PMD-jPinpoint-rules is licensed under the [Apache License, Version 2.0](https://github.com/jborgers/pmd-jpinpoint-rules/blob/master/LICENSE.md).

## See also
- How Bol uses the jPinpoint rules: [How to prevent common performance defects with the jPinpoint PMD rules](https://techlab.bol.com/en/blog/how-to-prevent-common-performance-defects-with-the-jpinpoint-pmd-rules/)
- Jeroen Borgers presented at J-Fall Virtual: [Fixing your performance and concurrency bugs before they bite you](https://youtu.be/Z_sT38KTRNk)
- Jeroen Borgers presented at Amsterdam JUG about the why, what and how of these code checking rules: [Performance problem prevention](https://www.meetup.com/nl-NL/Amsterdam-Java-User-Group/events/256497068/)
| [slides](http://jpinpoint.com/resources/Automated-and-learning-performance-problem-prevention-AMS-JUG.pdf)

# Usage

To use the ruleset you can install: 

- the PMD command line tool from [PMD project at github](https://pmd.github.io/) and/or
- the PMD-Plugin in your development environment. 

## PMD command line tool

After installing the tool you can run `pmd.sh` or `pmd.bat` similar to the following

    pmd.sh \
        -R PMD-jPinpoint-rules/rulesets/java/jpinpoint-rules.xml \
        -d $your-project-src \
        -f text

## IntelliJ IDEA with PMD Plugin

- You need version 2024-1+ of IntelliJ. The Community Edition is fine.
- Install PMD Plugin: 

      Settings > Plugins > Browse Repositories > Search 'PMD' > Select 'PMD' > Install > Close > OK > Restart

- Next, configure (add) the ruleset from this repo by URL to always be up-to-date:

      Settings > Tools > PMD > RuleSets > + > Choose RuleSet > drop down > Choose 'jpinpoint-rules' 

- Alternatively, download and add your local copy: *[rulesets/java/jpinpoint-rules.xml](https://raw.githubusercontent.com/jborgers/PMD-jPinpoint-rules/refs/heads/pmd7/rulesets/java/jpinpoint-rules.xml)*
  - remember to download regularly to get up-to-date
  
- Options tab: optionally check 'Skip Test Sources' and optionally set your Java version 

- You can now perform the code checks using [right-click] on a folder or a file and choose:
 
      Run PMD > Custom rules > jpinpoint-rules

- If you want a short description on a violation: hover over a violation title to get a popup with a description. 

- Documentation on a violation is shown on the right hand side after clicking a violation. More details of the problem and solution are shown with right-clicking 'Details'.

## Eclipse with PMD Plugin

The Acanda PMD plugin seems to be the best one to use. 
- [Import it into eclipse](http://www.acanda.ch/eclipse-pmd/release/latest).
- enable PMD through the properties of the project
- add the ruleset from this project *[rulesets/java/jpinpoint-rules.xml](https://raw.githubusercontent.com/jborgers/PMD-jPinpoint-rules/refs/heads/pmd7/rulesets/java/jpinpoint-rules.xml)*

## SonarQube with Plugins
In SonarQube, you need to install [sonar-pmd plugin](https://github.com/jborgers/sonar-pmd) from the marketplace, and [sonar-pmd-jpinpoint plugin](https://github.com/jborgers/sonar-pmd-jpinpoint) for these jpinpoint rules.

# Development

To start development on the ruleset the PDM tool designer may come in handy. 
Download it from the [PMD project at github](https://pmd.github.io/) and install it using the instructions on their site.

After installation and configuration you can start the designer from the command prompt:

    designer.bat
   
or

    ./pmd designer

## Rules and unit tests

The project can be build using **maven**. The build will perform the **unit tests** which will unit-test 
the rules. The next paragraph "Adding new rules" will describe in more detail where you can find the rule files.
To run the unit tests run the following command from the project home directory of:

    mvn clean test  
    
or simply:

    ./test

## Adding new rules

You can add new rules using the steps below. 

The steps basically tell you to create an issue, add documentation and create 3 files. 
As an example you can copy existing files and change the content according to your needs. Always work along the lines of what already exists.

For Kotlin: use the paths that contain `/kotlin/` instead of `/java/`.

- create an issue like 'Rule Request: AvoidRecreatingExpensiveThing' with simple compiling examples which can be used as tests. Use this issue reference with check-in.
- document the pitfall in the proper page and category in docs/ and [regenerate the ToC](https://luciopaiva.com/markdown-toc/)
- add the Test class in `src/test/java/com/.../perf/lang/java/ruleset/yourruleset/YourRule.java` 
elements from the package structure are used to lookup the rules xml file you add next. 
The relevant items based on the example given are: lang/**java**/ruleset/**yourruleset** 
- rules go into xml files found in `src/main/resources/category/` in this case 
src/main/resources/category/**java**/**yourruleset.xml**. Also add a rule with name `YourRule` 
since that is what the framework expects.
For new rule files (a new category) you will also need to register it in the `categories.properties` file found in the same directory 
(category/java/categories.properties) in this case add `category/java/yourruleset.xml`
- add the unit test in an xml file in 
`src/test/resources/com/.../perf/lang/java/ruleset/yourruleset/xml/YourRule.xml`. 
Pay attention to the package structure which is also dictated by the first java test class!

Depending on what you want to add you may also find it is sufficient to change one or more of the existing files.
Or to add only a Test class and unit test xml file (steps 1 and 3).

### Conventions for XML Unit test files

Following are some conventions and recommendations on how to
construct the unit test files:

- separate test code *(create separate ``<test-code>`` blocks)*
- specify test code description *(``<test-code><description>``)* Start the description with:
  - **violation:** or
  - **no violation:**
- specify number of occurrences *(``<test-code><expected-problems>``)*
- specify line-numbers *(``<test-code><expected-linenumbers>``)*
- code *(``<test-code><code>``)* conventions:
  - use class names like **``Foo``**
  - use method names like **``bad``** and **``good``**
  - add comment at the end of bad lines **``//bad``**  
  - remove useless code and **``import``** statements

### Run Kotlin Unit Tests

When running unit tests for Kotlin PMD 7 is needed. Make sure you have access to the PMD jars of the `7.0.0-SNAPSHOT`
branch (e.g. mvn install the PMD 7.0.x jars from https://github.com/pmd/pmd). Use the Maven `kotlin-pmd7` profile 
when running the Kotlin unit tests.

## Code Style Indentation

- Indentation: Use spaces a.k.a. **Disable Tabs**: *Settings>Editor>Code Style>Java>Use tab character [disable]*

## Contents of the project
- `rulesets/java/jpinpoint-rules.xml` contains the pmd custom rule definitions
- `src/main/java/pinpointrules` contains the Java code containing pitfalls for testing the rules. 
- `rulesets-merger` contains the Java code for a ruleset merger tool.  

## Merging rules

- `rulesets-merger/src` contains RulesetMerger.java for merging jpinpoint-rules.

 The merger tool can be built with:

    cd rulesets-merger
    mvn clean install

### Merging from different categories

 Run the merger tool as follows:

    cd rulesets-merger
    mvn exec:java -Dexec.args="java"
    
 or simply:
 
    ./merge

or for Kotlin instead of Java:

    ./merge kotlin

 It will merge the rules from ``src/main/resources/category/java/*.xml`` to create the jpinpoint-rules.xml file which can be used in your IDE.

### Merging with company specific rules

Company specific rules are useful for instance for checking the right use of company specific or company-bought frameworks and libraries. 
Or for rules which are candidates for inclusion into jpinpoint rules, yet need to be validated first.

- `rulesets-merger/src` contains RulesetMerger.java for merging jpinpoint-rules with company specific rules. 
Copy rulesets-merger to your company specific rules directory and adjust a few constants at the top to make it work for your company.

The merge tool runs either for the java or the kotlin rules. Use the first argument to choose: java or kotlin.
    
 After building, the merger tool can be run with:
 
    cd rulesets-merger
    mvn exec:java -Dexec.args="java"

or simply

    ./merge

or for Kotlin instead of Java:

    ./merge kotlin

 This will attempt to lookup the PMD-jPinpoint-rules project (next to your own project)
 and merge `rulesets/[java|kotlin]/jpinpoint-rules.xml` together with your rule files (from `src/main/resources/category/[java|kotlin]/*.xml`)     
 The resulting file can be used in your IDE.
 
 It assumes you have the following repositories in directories next to each other:
 
     PMD-Company-jPinpoint-rules
     PMD-jPinpoint-rules (optional)
 
  It can be built and run the same way.
 
  It will generate two files:
  
     company-rules.xml
     company-jpinpoint-rules.xml
     
  These files can be used in your IDE. The former only contains the company specific rules. 
  The latter contains all rules combined and will only be generated if the optional PMD-jPinpoint-rules repo is available.
 
  You can also do it yourself and specify the external repo to merge with explicitly:
   
      cd target
      java -jar java rulesets-merger-1.0-SNAPSHOT.jar PMD-jPinpoint-rules rulesets/java jpinpoint-rules.xml 

or for Kotlin:

      cd target
      java -jar kotlin rulesets-merger-1.0-SNAPSHOT.jar PMD-jPinpoint-rules rulesets/kotlin jpinpoint-rules.xml 

## Tools to create documentation
1. Confluence export as HTML
2. [Converting HTML to Markdown](https://domchristie.github.io/turndown/)
3. [Markdown TOC generator](https://luciopaiva.com/markdown-toc/
)
