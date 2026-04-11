# FHIRPath Testing Guide

## Testing Infrastructure

This project uses a fluent DSL for writing FHIRPath tests. Tests are built using `FhirPathTestBuilder` and executed as JUnit 5 dynamic tests.

## Test Description Format

All tests use a self-documenting format:

```
expression [with context] => expected [: description] [group]
```

**Examples:**
```
5 + 10 => 15 [Integer addition]
%context.count() with 'x' => 1 [Context count]
5 + %context with 10 => 15 : Add to context value [Context arithmetic]
(1 ; 2) + 2 => CardinalityMismatchException : MANY left operand [Cardinality errors]
```

## Writing Tests

### Basic Test Structure

```java
@TestFactory
Stream<DynamicTest> testArithmetic() {
    return builder()
        .group("Integer addition")
        .testEquals(15, "5 + 10")
        .testEquals(10, "5 + 5")
        .build();
}
```

### Available Test Methods

All methods support optional descriptions and optional context:

```java
// No description, no context
testEquals(15, "5 + 10")
testTrue("5 < 10")
testFalse("5 > 10")
testEmpty("{}")
testError(CardinalityMismatchException.class, "(1 ; 2) + 2")

// With description
testEquals(15, "5 + 10", "Simple addition")

// With context (import static context())
testEquals(15, "5 + %context", context("10"))

// With both
testEquals(15, "5 + %context", context("10"), "Add to context")
```

## Using Descriptions Effectively

**Include descriptions when:**
- Testing non-obvious behavior (e.g., "Division always returns decimal")
- Clarifying which aspect is being tested (e.g., "MANY left operand rejected")
- Distinguishing similar tests (e.g., "Empty + value" vs "Value + empty")

**Omit descriptions when** the expression + group already make the test clear:

```java
// Good: no description needed
.group("Integer addition")
.testEquals(15, "5 + 10")

// Good: description adds value
.group("Division")
.testEquals(5.0, "10 / 2", "Division always returns decimal")
```

## Groups

Use groups to organize related tests:

```java
.group("Integer arithmetic")
.testEquals(15, "5 + 10")
.testEquals(6, "10 - 4")

.group("Decimal arithmetic")
.testEquals(15.3, "5.1 + 10.2")
```

Groups appear at the end of test descriptions as metadata: `[Integer arithmetic]`

## Context Tests

Use the `context()` wrapper for tests with context:

```java
import static com.example.fhirpath.test.FhirPathTestBuilder.context;

.group("Context operations")
.testEquals(1, "%context.count()", context("'x'"))
.testTrue("exists()", context("'x'"))
.testEquals(15, "5 + %context", context("10"), "Add to context value")
```

## Resource Tests

Use the `withSubject()` method to test FHIRPath expressions that traverse resource fields:

```java
@TestFactory
Stream<DynamicTest> testResourceFieldAccess() {
    return builder()
        .group("Field access")
        .withSubject("Patient", p -> p
            .string("id", "patient-1")
            .integer("age", 30)
            .element("name", n -> n
                .string("family", "Smith")
                .stringArray("given", "John", "Jane")
            )
        )
        .testEquals("patient-1", "id")
        .testEquals(30, "age")
        .testEquals("Smith", "name.family")
        .testEquals(2, "name.given.count()")
        .build();
}
```

### Resource Builder Methods

**Primitive fields:** `string()`, `integer()`, `decimal()`, `bool()`
**Array fields:** `stringArray()`, `integerArray()`, `decimalArray()`, `boolArray()`
**Complex fields:** `element()` (single), `elementArray()` (multiple)

## Error Tests

Test for expected exceptions:

```java
.group("Cardinality errors")
.testError(CardinalityMismatchException.class, "(1 ; 2) + 2", "MANY left operand")
.testError(CardinalityMismatchException.class, "2 + (1 ; 2)", "MANY right operand")
```

Descriptions are **highly valuable** for error tests - they explain what makes the test invalid.

## Empty Collection Tests

Test for empty results:

```java
.group("Empty results")
.testEmpty("{}")
.testEmpty("{} + 10", "Empty propagates in arithmetic")
```

## Running Tests

### Maven

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=LiteralExpressionsTest

# Run specific test method
mvn test -Dtest=LiteralExpressionsTest#testArithmetic
```

### Filter by Description

Use the `fhirpath.test.filter` system property to filter tests by description (case-insensitive substring matching):

```bash
# Run only tests containing "5 + 10"
mvn test -Dfhirpath.test.filter="5 + 10"

# Run all tests in "Integer addition" group
mvn test -Dfhirpath.test.filter="Integer addition"

# Combine with test class filter
mvn test -Dtest=LiteralExpressionsTest -Dfhirpath.test.filter="5 + 10"
```

## YAML Reference Compatibility Suite

The `src/test/java/com/example/fhirpath/compat/yaml/` package runs the official
[fhirpath.js 3.16.4](https://github.com/hl7/fhirpath.js) reference tests against fp2sql's
Spark-backed evaluator. YAML cases, sample FHIR resources, and the exclusion `config.yaml` are
copied verbatim from Pathling at `.local/pathling/fhirpath/src/test/resources/fhirpath-js/` and
should be refreshed as a unit when Pathling updates its snapshot.

### Running

```bash
# Run the full suite (infra + green YAML cases)
mvn test -Dtest=YamlReferenceCompatTest

# Run one YAML file as a focused set
mvn test -Dtest=YamlReferenceCompatTest#testLiterals
```

Each `@YamlTest` method in `YamlReferenceCompatTest` expands into one JUnit 5 parameterized
test per non-disabled case in the referenced YAML file. Display names come from the case's
`desc` (or the expression when `desc` is absent).

### Exclusion format

Expected failures, skips, and regression guards live in
`src/test/resources/fhirpath-js/config.yaml`. The schema mirrors Pathling's
`ExcludeRule` / `ExcludeSet` types:

```yaml
excludeSet:
  - title: "Set title"
    glob: "*.yaml"          # file-name glob; * means all files
    exclude:
      - title: "Rule title"
        type: feature        # feature | bug | wontfix
        comment: "Why this exclusion is in place"
        outcome: failure     # null (skip) | error | failure (XFAIL) | pass
        any: ["substring"]   # substring match on expression or description
        expression: ["regex"]
        function: ["funcName"]
```

Outcome semantics:

- **omitted** (or explicit `null`) — the matching case is skipped entirely (`TestAbortedException`)
- **`error`** — the case must throw during evaluation; unexpected success fails the test
- **`failure`** — the case must throw an `AssertionError` (XFAIL); unexpected success fails
- **`pass`** — the case must still pass; acts as a regression guard tied to the exclusion reason

fp2sql-specific exclusions live in sets titled **"fp2sql exclusions"** or **"fp2sql — skip
entire <file>"** at the top of `config.yaml`. Upstream Pathling rules follow after. When
enabling a currently-skipped file, delete or narrow its `fp2sql — skip entire <file>` set and
run the suite; add per-case exclusions for whatever remains red.

### Adding YAML test files

1. Refresh `.local/pathling` to the desired Pathling snapshot.
2. Re-copy the files that changed under `src/test/resources/fhirpath-js/` (cases, resources,
   and `config.yaml`).
3. If new YAML files were added, add a corresponding `@YamlTest` method to
   `YamlReferenceCompatTest`.
4. Run the suite, classify any new failures, and add exclusions to `config.yaml`.

### Unsupported features

The port deliberately skips a few Pathling capabilities:

- **`spel:` matchers** — Spring Expression Language rules in `config.yaml` are ignored.
- **YAML-level `variables:`** — fp2sql has no public hook for injecting variables into the
  compiler, so cases that declare `variables:` are skipped at the executor level.
- **Arbitrary (non-FHIR) subjects** — the subject factory only supports FHIR resources parsed
  through HAPI; synthetic top-level subject types (e.g. `Functions`, `MathTestData`) are
  skipped when HAPI rejects them.

## Troubleshooting

**Enable DEBUG logging** to see which test is executing:
```bash
mvn test -Dorg.slf4j.simpleLogger.log.com.example.fhirpath.test=DEBUG
```

### Common Failure Patterns

- **Type mismatch**: FHIRPath division always returns Decimal (`10 / 2` → `5.0`)
- **Cardinality errors**: Use `.first()` or ensure expression returns ONE element
- **Empty vs null**: Empty collection `{}` → `null` in Java; use `testEmpty()` for assertions
- **Collection order**: Collections return as `List<?>` — order matters in comparison
