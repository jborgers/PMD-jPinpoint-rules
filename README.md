# PMD-jPinpoint-rules
Static code checking rules for responsible Java and Kotlin programming built on PMD, sponsored by [Rabobank](https://www.rabobank.com/).

These rules are on performance, sustainability, multi-threading, data mix-up, and more.

## Quickstart

The quickest way to see these rules in action is to install the IntelliJ PMD plugin:

    Settings > Plugins > Browse Repositories > Search 'PMD' > Select 'PMD' > Install > Close > OK > Restart

Next, add the Java and/or Kotlin rule sets:

    Settings > Tools > PMD > RuleSets > + > Choose RuleSet > drop down > Choose 'jpinpoint-kotlin-rules' or 'jpinpoint-java-rules' > OK > OK

<img src="docs/images/intellij-pmd-dropdown-light.png?raw=true" width="400" height="240" alt="IntelliJ PMD dropdown" title="IntelliJ PMD dropdown" />

Then right-click on a source folder or file and choose:

    Run PMD > Custom rules > jpinpoint-java-rules or jpinpoint-kotlin-rules

<img src="docs/images/intellij-pmd-run-light.png?raw=true" width="488" height="89" alt="IntelliJ PMD run" title="IntelliJ PMD run" />

The result is a list of violations of the rules, with a description and a link to documentation on the rule, the problem and the solution.

<img src="docs/images/intellij-pmd-results-light.png?raw=true" width="915" height="325" alt="IntelliJ PMD results" title="IntelliJ PMD results" />

## Purpose

The aim of this project is to create, manage and share these rules, to code better software together: 
better software which is faster, uses less resources, has a smaller ecological footprint, is more stable, more confidential, with less effort and lower cost. 
More general, to promote responsibility by considering the concerns of the user, the environment, the engineer, the community and the company.

We have distilled these code checks from what we learned in several years of analyzing performance problems and failures found in code, tests and production situations. And the ruleset is growing every month.

We didn't find these checks in other places, like the standard PMD, FindBugs/Spotbugs, Checkstyle or Sonar rules.
We are working with the PMD-team to move some of the jpinpoint rules in the standard rule set, as well as make PMD suitable for Kotlin.

## Rules documentation

* [JavaCodePerformance](docs/JavaCodePerformance.md)
* [JavaDataAccessPerformance](docs/JavaDataAccessPerformance.md)
* [JavaCodeQuality](docs/JavaCodeQuality.md)

## Presentations and articles

- How Bol uses the jPinpoint rules: [How to prevent common performance defects with the jPinpoint PMD rules](https://techlab.bol.com/en/blog/how-to-prevent-common-performance-defects-with-the-jpinpoint-pmd-rules/)
- Jeroen Borgers presented at J-Fall Virtual: [Fixing your performance and concurrency bugs before they bite you](https://youtu.be/Z_sT38KTRNk)
- Jeroen Borgers presented at Amsterdam JUG about the why, what and how of these code checking rules: [Performance problem prevention](https://www.meetup.com/nl-NL/Amsterdam-Java-User-Group/events/256497068/)
| [slides](http://jpinpoint.com/resources/Automated-and-learning-performance-problem-prevention-AMS-JUG.pdf)

## Ways to use the rules

Run the jPinpoint rules from the command-line using the PMD tool, from your favorite development
environment with a PMD-plugin, or in SonarQube with the [sonar-pmd-jpinpoint plugin](https://github.com/jborgers/sonar-pmd-jpinpoint)
next to the [Sonar pmd plugin](https://github.com/jborgers/sonar-pmd).

To use the ruleset you can install: 

- the PMD command line tool from [PMD project at github](https://pmd.github.io/) and/or
- the PMD-Plugin in your development environment. 

### PMD command line tool

After installing the PMD tool you can run `pmd` similar to the following

    pmd check \
        -R PMD-jPinpoint-rules/rulesets/java/jpinpoint-rules.xml \
        -d $your-project-src \
        -f text

### IntelliJ IDEA with PMD Plugin

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

### Eclipse with PMD Plugin

The Acanda PMD plugin seems to be the best one to use. 
- [Import it into eclipse](http://www.acanda.ch/eclipse-pmd/release/latest).
- enable PMD through the properties of the project
- add the ruleset from this project *[rulesets/java/jpinpoint-rules.xml](https://raw.githubusercontent.com/jborgers/PMD-jPinpoint-rules/refs/heads/pmd7/rulesets/java/jpinpoint-rules.xml)*

### SonarQube with Plugins

In SonarQube, you need to install [sonar-pmd plugin](https://github.com/jborgers/sonar-pmd) from the marketplace, and [sonar-pmd-jpinpoint plugin](https://github.com/jborgers/sonar-pmd-jpinpoint) for these jpinpoint rules.

## Development and contribution

Development docs (building, tests, adding rules, merging rules) are in [`CONTRIBUTING.md`](CONTRIBUTING.md)

## License

PMD-jPinpoint-rules is licensed under the [Apache License, Version 2.0](https://github.com/jborgers/pmd-jpinpoint-rules/blob/master/LICENSE.md).
