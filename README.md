# PMD-jpinpoint-rules
PMD rule set for performance aware Java coding, sponsored by Rabobank. The project is meant for creating and managing 
automatic java code checks. 
These checks are based on what we have learnt in several years of analyzing performance problems and other defects and failures 
found in code, tests and production situations.

We didn't find these checks in other places, like the the standard PMD, FindBugs/Spotbugs, Checkstyle or Sonar rules.
If you find duplicates of existing ones, please let us know. 
Some of the rules are candidates for contribution to the PMD standard rules.
These jpinpoint rules can be run from the command-line using the PMD tool, from your favorite development
environment with a PMD-plugin, or in SonarQube after packaging them as Sonar plugin.

## See also
TODO

# Usage

To use the ruleset you can install 

- the PMD tool from [PMD project at github](https://pmd.github.io/)
- the PMDPlugin in you development environment. 

## PMD tool

After installing the tool you can run `pmd.sh` or `pmd.bat` similar to the following

    pmd.bat \
        -R $java-performance-code-checks/rulesets/java/jpinpoint-rules.xml \
        -d $your-project-src \
        -f text

## IntelliJ IDEA

Plugins to install are

- `PMDPlugin`
- `SonarLint`

After installing the plugins you can configure (add) the performance 
ruleset from this project *rulesets/java/jpinpoint-rules.xml*:

    Settings > Other Settings > PMD > RuleSets 

You can now perform the code checks using [right-click] on a folder or a file and choose:
 
    Run PMD > Custom rules > jpinpoint-rules

*Known Bug: the jpinpoint-rules can occur multiple times in the PMD Plugin: this is a bug and should be resolved by restarting IntelliJ*

# Development

To start development on the ruleset the PDM tool designer may come in handy. 
Download it from the [PMD project at github](https://pmd.github.io/) and install it using the instructions on their site.

After installation and configuration you can start the designer from the command prompt:

    designer.bat


## Code Style Indentation

- Indentation: Use spaces aka **Disable Tabs**: *Settings>Editor>Code Style>Java>Use tab character [disable]*