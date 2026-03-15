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
