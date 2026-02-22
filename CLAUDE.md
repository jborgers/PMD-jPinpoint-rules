# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PMD-jPinpoint-rules is a collection of custom PMD static analysis rules for Java and Kotlin, focused on performance, sustainability, multi-threading, data mix-up, and code quality. Rules are implemented as XPath expressions inside XML rule definition files (no custom Java rule classes).

## Commands

### Build and test
```sh
mvn clean test       # run all unit tests
./test               # shorthand script (runs ./mvnw test)
```

### Run a single test class
```sh
mvn test -Dtest=AvoidSpringMVCMemoryLeaksTest
```

### Merge category XML files into the combined ruleset
```sh
./merge              # merges Java categories → rulesets/java/jpinpoint-rules.xml
./merge kotlin       # merges Kotlin categories → rulesets/kotlin/jpinpoint-kotlin-rules.xml
```
The merge step is required after editing any `src/main/resources/category/**/*.xml` file so that the combined `rulesets/` files stay up to date.

## Architecture

### Rule definitions (source of truth)
Rules live in per-category XML files under `src/main/resources/category/`:

| Language | Category files |
|----------|---------------|
| Java | `java/common.xml`, `java/common_std.xml`, `java/concurrent.xml`, `java/enterprise.xml`, `java/remoting.xml`, `java/spring.xml`, `java/sql.xml` |
| Kotlin | `kotlin/common.xml`, `kotlin/remoting.xml` |

Each category is registered in `src/main/resources/category/{java,kotlin}/categories.properties`.

Almost all rules use `class="net.sourceforge.pmd.lang.rule.xpath.XPathRule"` — rules are XPath 2.0 queries over the PMD AST.

### Combined rulesets (generated)
`rulesets/java/jpinpoint-rules.xml` and `rulesets/kotlin/jpinpoint-kotlin-rules.xml` are **generated** by the merger tool from the category files. Edit the category files, not these.

### Tests
Each rule has exactly two files:

1. **Test class** — `src/test/java/com/jpinpoint/perf/lang/{java,kotlin}/ruleset/{category}/{RuleName}Test.java`
   Always empty except for extending `PmdRuleTst`. The package path encodes the language and category, which PMD uses to locate the rule XML and test data.

2. **Test data** — `src/test/resources/com/jpinpoint/perf/lang/{java,kotlin}/ruleset/{category}/xml/{RuleName}.xml`
   Contains `<test-code>` blocks with:
   - `<description>` starting with `violation:` or `no violation:`
   - `<expected-problems>` (count)
   - `<expected-linenumbers>`
   - `<code>` (CDATA) — use class names `Foo`, method names `bad`/`good`, and `//bad` comments on flagged lines

### Merger tool
`rulesets-merger/src/main/java/com/jpinpoint/perf/tools/RulesetMerger.java` — standalone Maven module that merges category XMLs into the combined ruleset files.

## Adding a New Rule

1. Add the rule definition to the appropriate `src/main/resources/category/{java,kotlin}/{category}.xml`
2. Create the empty test class in `src/test/java/com/jpinpoint/perf/lang/{java,kotlin}/ruleset/{category}/{RuleName}Test.java`
3. Create the test data XML in `src/test/resources/com/jpinpoint/perf/lang/{java,kotlin}/ruleset/{category}/xml/{RuleName}.xml`
4. Document the rule in the appropriate `docs/` markdown file
5. Run `./merge` to regenerate the combined ruleset
6. Run `./test` to verify

For a new category, also add an entry to `categories.properties`.

## Code Style

- Indentation: **spaces only** (no tabs)
