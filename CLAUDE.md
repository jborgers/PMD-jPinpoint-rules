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

Most rules use `class="net.sourceforge.pmd.lang.rule.xpath.XPathRule"` — rules are XPath 2.0 queries over the PMD AST. Complex rules that are hard to express in XPath can be implemented as Java classes (see below).

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

### Java-based Kotlin rules

For complex logic that is hard to express in XPath, rules can be implemented as Java classes:

- **Location**: `src/main/java/com/jpinpoint/perf/lang/kotlin/rule/{category}/{RuleName}Rule.java`
- **Pattern**: extend `AbstractKotlinRule`, override `buildTargetSelector()` and `buildVisitor()`
- **Visitor**: inner class extending `KotlinVisitorBase<RuleContext, Void>`, override `visitXxx()` methods
- **pmd-kotlin dependency**: must be `compile`-scoped (not `test`) so the rule class can import from `net.sourceforge.pmd.lang.kotlin.*`
- **XML registration**: use `class="com.jpinpoint.perf.lang.kotlin.rule.common.MyRule"` instead of `class="net.sourceforge.pmd.lang.rule.xpath.XPathRule"`; keep `language="kotlin"` attribute

**Key API notes** (PMD 7.x ANTLR-based Kotlin AST):
- `node.descendants(KtFoo.class)` / `node.ancestors(KtFoo.class)` / `node.children(KtFoo.class)` return `NodeStream<T>`
- `NodeStream` methods: `.any(Predicate)`, `.filter(Predicate)`, `.first()`, `.toList()`, `.nonEmpty()`, `.isEmpty()`
- `KotlinTerminalNode.getText()` returns the token image (e.g. `"String"`, `"+="`, `"+"`)
- **Do NOT use** `node.getToken(type, idx)` or `terminalNode.ADD()` / `terminalNode.ADD_ASSIGNMENT()` style methods — `BaseAntlrTerminalNode.getTokenKind()` returns the token stream index, not the token type, causing these to always return null. Use **text comparison** instead: `t.getText().equals("+=")`
- Reference implementation in pmd-kotlin jar: `net.sourceforge.pmd.lang.kotlin.rule.errorprone.OverrideBothEqualsAndHashcodeRule`

## Code Style

- Indentation: **spaces only** (no tabs)
