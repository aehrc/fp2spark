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

This format ensures tests are understandable without consulting code.

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

### When to Include Descriptions

**Include descriptions when:**
- Testing non-obvious behavior
- Documenting edge cases
- Explaining why a specific value is expected
- Clarifying the purpose beyond what the expression shows

**Omit descriptions when:**
- The expression is self-explanatory
- The group already provides sufficient context
- The test is trivial or obvious

### Good vs Bad Description Usage

#### ✅ Good: Adds Value

```java
.group("Division")
.testEquals(5.0, "10 / 2", "Division always returns decimal")
.testEquals(2.5, "5 / 2", "Integer division yields decimal result")

.group("Cardinality errors")
.testError(CardinalityMismatchException.class, "(1 ; 2) + 2", "MANY left operand rejected")
.testError(CardinalityMismatchException.class, "2 + (1 ; 2)", "MANY right operand rejected")

.group("Empty collections")
.testEmpty("{} + 10", "Empty + value returns empty")
.testEmpty("10 + {}", "Value + empty returns empty")
```

**Why good:**
- "Division always returns decimal" - Documents important FHIRPath semantics
- "MANY left/right operand" - Clarifies which side causes the error
- "Empty + value" vs "Value + empty" - Distinguishes similar tests

#### ❌ Bad: Redundant

```java
.group("Integer addition")
.testEquals(15, "5 + 10", "Basic integer addition")  // ❌ Redundant
.testEquals(10, "5 + 5", "Adding five and five")     // ❌ Obvious
.testEquals(-5, "5 + -10", "Negative operand")        // ❌ Expression shows this

.group("Literals")
.testEquals(12, "12", "Basic integer literal")        // ❌ Extremely obvious
.testTrue("true", "Boolean true literal")             // ❌ Adds nothing
```

**Why bad:**
- Group "Integer addition" + expression "5 + 10" already says everything
- "Adding five and five" just rephrases "5 + 5"
- "Negative operand" is visible in "-10"
- Literal tests are self-evident

#### ✅ Better: No Description

```java
.group("Integer addition")
.testEquals(15, "5 + 10")      // Clear from expression + group
.testEquals(10, "5 + 5")       // Clear from expression + group
.testEquals(-5, "5 + -10")     // Clear from expression + group

.group("Literals")
.testEquals(12, "12")          // Obvious
.testTrue("true")              // Obvious
```

**Test output:**
```
✓ 5 + 10 => 15 [Integer addition]
✓ 5 + 5 => 10 [Integer addition]
✓ 5 + -10 => -5 [Integer addition]
✓ 12 => 12 [Literals]
✓ true => true [Literals]
```

This is perfectly clear without descriptions!

#### ✅ Good: Strategic Descriptions

```java
.group("Type coercion")
.testEquals(15.2, "5 + 10.2", "Integer promoted to decimal")
.testEquals(15.1, "5.1 + 10", "Integer promoted to decimal")

.group("Edge cases")
.testEquals(0, "5 - 5", "Subtraction to zero")
.testEmpty("{} + {}", "Empty + empty yields empty (not null)")
.testTrue("(1 ; 2 ; 3).where($this > 1).exists()", "Filter preserves non-empty result")
```

**Why good:**
- Documents type promotion rules (not obvious from expression alone)
- "to zero" emphasizes the edge case being tested
- "not null" clarifies important semantic distinction
- "preserves non-empty" states the specific property being verified

## Groups

Use groups to organize related tests:

```java
.group("Integer arithmetic")
.testEquals(15, "5 + 10")
.testEquals(6, "10 - 4")

.group("Decimal arithmetic")
.testEquals(15.3, "5.1 + 10.2")
.testEquals(6.3, "10.5 - 4.2")
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

## Summary: Description Guidelines

**Golden Rule:** If the test description (`expression => expected [group]`) is already clear, omit the user description. Add description only when it provides genuine insight beyond what's visible.

**Ask yourself:**
1. Does the description explain **why** the test exists?
2. Does it document non-obvious behavior?
3. Does it clarify an important edge case?
4. Does it distinguish this test from similar ones?

If **no** to all four → skip the description!

**Remember:** The format already shows:
- What expression is being tested
- What context is used (if any)
- What result is expected
- What category it belongs to (group)

Only add description if it adds value beyond these four things.
