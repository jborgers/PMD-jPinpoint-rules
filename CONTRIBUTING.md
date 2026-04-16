# Contributing

This repository contains **PMD jPinpoint rules, unit tests and documentation** (Java + Kotlin).

## General guidelines

A couple of general guidelines that help the review process:

- Create an issue with a clear problem description and add test cases and a solution approach when possible.
- Align on the applicability, scope and approach before opening a pull request.
- Please avoid reformatting unrelated code; keep changes limited to the area you’re fixing so the diff stays focused and easier to review.
- When changing a rule, please add (or update) unit tests that cover the new behavior.

## How to contribute

1. **Open an issue first**
   - Bug report: include version, rule name, minimal reproduction code, and expected vs actual behavior.
   - Rule enhancement / new rule: include examples for **violations** and **no violations**.
   - Please use standard issue title prefixes and include the language:
     - `Fix Request (Java): ...` / `Fix Request (Kotlin): ...`
     - `Rule Request (Java): ...` / `Rule Request (Kotlin): ...`
2. **Create a branch** from `pmd7` (!) and keep the change focused.
3. **Update/add unit tests** for the behavior you changed.
4. **Run tests locally** (`./mvnw clean test`) and ensure they pass.
5. **Open a pull request**
   - Link the issue.
   - Describe what changed and why.
   - Include notes about any false-positive / false-negative tradeoffs if relevant.

## Development

To start development on the ruleset the PMD tool designer may come in handy.
Download it from the [PMD project at github](https://pmd.github.io/) and install it using the instructions on their site.

After installation and configuration you can start the designer from the command prompt:

```bash
pmd.bat designer
```

or

```bash
./pmd designer
```

## Build and run tests

The project is built using **Maven**. The build runs the **unit tests** which validate the rules.

From the repository root:

```bash
./test
```

## Adding new rules

You can add new rules using the steps below.

The steps basically tell you to create an issue, add documentation and create 3 files.
As an example you can copy existing files and change the content according to your needs.
Always work along the lines of what already exists.

For Kotlin: use the paths that contain `/kotlin/` instead of `/java/`.

- Create an issue like **"Rule Request: AvoidRecreatingExpensiveThing"** with simple compiling examples which can be used as tests.
  Use this issue reference with check-in.
- Document the pitfall in the proper page and category in `docs/` and
  [regenerate the ToC](https://luciopaiva.com/markdown-toc/).
- Add the test class in
  `src/test/java/com/.../perf/lang/java/ruleset/yourruleset/YourRule.java`.
  Elements from the package structure are used to lookup the rules XML file you add next.
  The relevant items based on the example given are: `lang/java/ruleset/yourruleset`.
- Rules go into XML files found in `src/main/resources/category/`.
  In this case: `src/main/resources/category/java/yourruleset.xml`.
  Also add a rule with name `YourRule` since that is what the framework expects.
  For new rule files (a new category) you will also need to register it in the
  `categories.properties` file found in the same directory
  (`category/java/categories.properties`), in this case add `category/java/yourruleset.xml`.
- Add the unit test in an XML file in
  `src/test/resources/com/.../perf/lang/java/ruleset/yourruleset/xml/YourRule.xml`.
  Pay attention to the package structure which is also dictated by the first Java test class.

Depending on what you want to add you may find it is sufficient to change one or more existing files.
Or to add only a test class and unit test XML file.

### Conventions for XML unit test files

Following are some conventions and recommendations on how to construct the unit test files:

- Separate test code (create separate `<test-code>` blocks)
- Specify test code description (`<test-code><description>`). Start the description with:
  - **violation:** or
  - **no violation:**
- Specify number of occurrences (`<test-code><expected-problems>`)
- Specify line-numbers (`<test-code><expected-linenumbers>`)
- Code conventions (`<test-code><code>`):
  - use class names like `Foo`
  - use method names like `bad` and `good`
  - add comment at the end of bad lines `//bad`
  - remove useless code and `import` statements

## Run Kotlin unit tests

When running unit tests for Kotlin, PMD 7 is needed.
Make sure you have access to the PMD jars of the `7.2.24-SNAPSHOT` branch (e.g. `./mvnw install` the PMD 7.2.x jars from https://github.com/pmd/pmd).
Use the Maven `kotlin-pmd7` profile when running the Kotlin unit tests.

> Note: use `./mvnw` (the Maven Wrapper) for all builds in this repository.

## Code style indentation

- Indentation: use spaces (disable tabs):
  *Settings → Editor → Code Style → Java → Use tab character (disable)*

## Contents of the project

- `rulesets/java/jpinpoint-rules.xml` contains the PMD custom rule definitions.
- `src/main/java/pinpointrules` contains the Java code containing pitfalls for testing the rules.
- `rulesets-merger` contains the Java code for a ruleset merger tool.

## Merging rules

The merger tool can merge rule categories into a single ruleset file used by IDE plugins.

Build the merger tool:

```bash
cd rulesets-merger
./mvnw clean install
```

Run the merger tool:

```bash
cd rulesets-merger
./mvnw exec:java -Dexec.args="java"
```

or simply:

```bash
./merge
```

For Kotlin instead of Java:

```bash
./merge kotlin
```

### Merging with company-specific rules

Company-specific rules are useful for instance for checking the right use of company-specific or company-bought frameworks and libraries.

Copy `rulesets-merger` to your company rules directory and adjust a few constants at the top to make it work for your company.

The merge tool runs for either Java or Kotlin rules. Use the first argument to choose: `java` or `kotlin`.

After building, the merger tool can be run with:

```bash
cd rulesets-merger
./mvnw exec:java -Dexec.args="java"
```

or simply:

```bash
./merge
```

For Kotlin instead of Java:

```bash
./merge kotlin
```

It will attempt to locate the `PMD-jPinpoint-rules` project next to your own project and merge
`rulesets/[java|kotlin]/jpinpoint-rules.xml` together with your rule files (from `src/main/resources/category/[java|kotlin]/*.xml`).

It assumes you have repositories in directories next to each other:

```text
PMD-Company-jPinpoint-rules
PMD-jPinpoint-rules (optional)
```

It will generate:

```text
company-rules.xml
company-jpinpoint-rules.xml
```

You can also specify the external repo explicitly, e.g.:

```bash
cd target
java -jar rulesets-merger-1.0-SNAPSHOT.jar PMD-jPinpoint-rules rulesets/java jpinpoint-rules.xml
```

For Kotlin:

```bash
cd target
java -jar rulesets-merger-1.0-SNAPSHOT.jar PMD-jPinpoint-rules rulesets/kotlin jpinpoint-rules.xml
```


