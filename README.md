# PMD-jpinpoint-rules
PMD rule set for performance aware Java coding, sponsored by Rabobank. The project is meant for creating and managing 
automatic java code checks. 
These checks are based on what we have learned in several years of analyzing performance problems and other defects and failures 
found in code, tests and production situations.

We didn't find these checks in other places, like the the standard PMD, FindBugs/Spotbugs, Checkstyle or Sonar rules.
If you find duplicates of existing ones, please let us know. 
We offered these rules to the PMD-team for inclusion in the standard rules and we were warmly welcomed. We have been working with them to upgrade and merge (some of) the jpinpoint rules in the standard and looking for sponsorship to continue with that.
You don't have to wait for that, you can already use these as custom rules right now.

The jpinpoint rules can be run from the command-line using the PMD tool, from your favorite development
environment with a PMD-plugin, or in SonarQube after packaging them as Sonar plugin.

## See also
- Jeroen Borgers presented at Amsterdam JUG about the why, what and how of these code checking rules: [Performance problem prevention](https://www.meetup.com/nl-NL/Amsterdam-Java-User-Group/events/256497068/)
| [slides](http://jpinpoint.com/resources/Automated-and-learning-performance-problem-prevention-AMS-JUG.pdf)
- We are looking for sponsorship for the full documentation of the pitfalls.

# Usage

To use the ruleset you can install: 

- the PMD tool from [PMD project at github](https://pmd.github.io/)
- the PMDPlugin in you development environment. 

## PMD tool

After installing the tool you can run `pmd.sh` or `pmd.bat` similar to the following

    pmd.sh \
        -R PMD-jPinpoint-rules/rulesets/java/jpinpoint-rules.xml \
        -d $your-project-src \
        -f text

## IntelliJ IDEA

- You need a recent version of IntelliJ, 2018+. Community Edition is fine
- Install PMDPlugin: 

      Settings/Preferences > Plugins > Browse Repositories > Search 'PMDPlugin' > Select 'PMDPlugin' > Install > Close > OK > Restart

- Configure (add) the ruleset from this repo: *rulesets/java/jpinpoint-rules.xml*:

      Settings/Preferences > Other Settings > PMD > RuleSets 
- Options tab: check 'Skip Test Sources' and set your Java version 

- You can now perform the code checks using [right-click] on a folder or a file and choose:
 
      Run PMD > Custom rules > jpinpoint-rules

- If you want more information on a violation: hover over a violation title to get a details popup. 

*Known Bug: the jpinpoint-rules can be listed multiple times in the PMD Plugin: this is a bug and should be resolved by restarting IntelliJ*

## Eclipse

The Acanda PMD plugin seems to be the best one to use. 
- [Import it into eclipse](http://www.acanda.ch/eclipse-pmd/release/latest).
- enable PMD through the properties of the project
- add the ruleset from this project *rulesets/java/jpinpoint-rules.xml*

# Development

To start development on the ruleset the PDM tool designer may come in handy. 
Download it from the [PMD project at github](https://pmd.github.io/) and install it using the instructions on their site.

After installation and configuration you can start the designer from the command prompt:

    designer.bat
   
or

    ./run.sh designer

## Rules and unit tests

The project can be build using **maven**. The build will perform the **unit tests** which will unit-test 
the rules. The next paragraph "Adding new rules" will describe in more detail where you can find the rule files.
To run the unit tests run the following command from the project home directory of:

    mvn clean test  
    
or simply:

    ./test

## Adding new rules

You can add new rules using the following steps below. The steps basically tell you to create 3 files. 
As an example you can copy existing files and change the content according to your needs.

- add the Test class in `src/test/java/com/.../perf/lang/java/ruleset/yourruleset/YourRule.java` 
elements from the package structure are used to lookup the rules xml file you add next. 
The relevant items based on the example given are the following: lang/**java**/ruleset/**yourruleset** 
- rules go into xml files found in `src/main/resources/category/` in this case 
src/main/resources/category/**java**/**yourruleset.xml**. Also add a rule with name `YourRule` 
since that is what the framework expects.
For new rule files you will also need to register it in the `categories.properties` file found in the same directory 
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

## Code Style Indentation

- Indentation: Use spaces aka **Disable Tabs**: *Settings>Editor>Code Style>Java>Use tab character [disable]*

## Contents of the project
- `rulesets/java/jpinpoint-rules.xml` contains the pmd custom rule definitions
- `src/main/java/pinpointrules` contains the Java code containing pitfalls for testing the rules. 
- `rulesets-merger` contains the Java code for a ruleset merger tool.  

## Merging rules

- rulesets-merger/src contains RulesetMerger.java for merging jpinpoint-rules.

 The merger tool can be built with:

    cd rulesets-merger
    mvn clean install

### Merging from different categories

 If the merger tool is run as follows:

    rulesets-merger/mvn exec:java
    
 or simply
 
    ./merge

 It will just merge the rules from ``src/main/resources/category/java/*.xml`` to create the jpinpoint-rules.xml file which can be used in your IDE.

### Merging with company specific rules

Company specific rules are useful for instance for checking the right use of company specific or company-bought frameworks and libraries. 
Or for rules which are candidates for inclusion into jpinpoint rules, yet need to be validated first.

- rulesets-merger/src contains RulesetMerger.java for merging jpinpoint-rules with company specific rules. 
Copy rulesets-merger to your company specific rules directory and adjust a few constants at the top to make it work for your company.
    
 After building, the merger tool can be run with:
 
     rulesets-merger/mvn exec:java
or simply

    ./merge

 This will attempt to lookup the PMD-jPinpoint-rules project (next to your own project)
 and merge rulesets/java/jpinpoint-rules.xml together with your rule files (from ``src/main/resources/category/java/*.xml``)     
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
      java -jar rulesets-merger-1.0-SNAPSHOT.jar PMD-jPinpoint-rules rulesets/java jpinpoint-rules.xml 
   

