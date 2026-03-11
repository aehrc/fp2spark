# SparkSQL Code Generator - Testing Strategy

**Aligned with**: Stateful handlers, automatic boxing/unboxing, wrapper-based design

---

## Testing Philosophy

**Two-layer approach:**
1. **Component Tests** - Test wrappers and utilities in isolation
2. **FHIRPath Expression Tests** - Test complete system with FHIRPath expressions

**Skip handler/CodeGen tests** - They are plumbing between components; FHIRPath tests exercise them implicitly.

**Key insight**: FHIRPath expression tests become a reusable specification test suite, independent of code generator implementation.

---

## 1. Component Tests

Test building blocks with non-trivial logic.

### Collection Wrapper

**Goal**: Verify cardinality-aware operations

```java
@Test
void count_onSingular_returnsOne() {
    Collection input = Collection.singular(lit(5));
    assertEquals(1, evaluateColumn(input.count(), spark));
}

@Test
void count_onNull_returnsZero() {
    Collection input = Collection.nonSingular(lit(null));
    assertEquals(0, evaluateColumn(input.count(), spark));  // NOT NULL!
}
```

**Key tests**:
- Singular vs array dispatch
- Null handling (empty = NULL)
- `filter()`, `map()` with lambdas
- `first()`, `last()`, `count()`

### Quantity Wrapper

**Goal**: Verify struct field access

```java
@Test
void value_extractsCorrectField() {
    Quantity qty = createQuantity(5.0, "mg");
    assertEquals(5.0, evaluateColumn(qty.value(), spark));
}

@Test
void mapValue_transformsValueOnly() {
    Quantity qty = createQuantity(5.0, "mg");
    Quantity result = qty.mapValue(v -> v.multiply(2));

    assertEquals(10.0, evaluateColumn(result.value(), spark));
    assertEquals("mg", evaluateColumn(result.unit(), spark));
}
```

**Key tests**: Field accessors, transformations preserve structure

### LambdaExpression Wrapper

**Goal**: Verify lambda evaluation with `$this` binding

```java
@Test
void apply_bindsThisToElement() {
    Lambda irLambda = /* lambda: $this > 5 */;
    LambdaExpression lambda = new LambdaExpression(irLambda, context);

    Column result = lambda.apply(lit(10));

    assertTrue(evaluateColumn(result, spark));  // 10 > 5
}
```

**Key tests**: Element binding, context propagation

### InvocationBinder

**Goal**: Verify automatic boxing/unboxing

```java
@Test
void boxing_createsCollectionWithCorrectCardinality() {
    Column col = lit(5);
    IRNode node = /* Shape(ONE, INTEGER) */;

    Object boxed = binder.boxArgument(col, node, Collection.class);

    assertInstanceOf(Collection.class, boxed);
    assertTrue(((Collection) boxed).isSingular());
}

@Test
void unboxing_extractsColumn() {
    Collection collection = Collection.singular(lit(5));

    Column unboxed = binder.unboxResult(collection);

    assertEquals(5, evaluateColumn(unboxed, spark));
}
```

**Key tests**: Type conversion rules, cardinality preservation

---

## 2. FHIRPath Expression Tests

**Primary testing strategy**: Extensive test suite covering FHIRPath specification.

### Why This Approach

**Benefits:**
- Tests what users care about (correct FHIRPath behavior)
- Reusable across code generators (SparkSQL today, DuckDB/Polars tomorrow)
- Serves as specification compliance suite
- Exercises entire pipeline (parser, analyzer, CodeGen, handlers)
- Natural test organization (follows FHIRPath spec structure)

**Comparison to handler tests:**
- Handler tests: Test implementation details, not reusable
- FHIRPath tests: Test contract, highly reusable

### Test Organization

Organize by FHIRPath feature area:

```
src/test/java/com/example/fhirpath/integration/
├── ArithmeticOperationsTest.java
├── ComparisonOperationsTest.java
├── CollectionOperationsTest.java
├── StringOperationsTest.java
├── TypeOperationsTest.java
├── QuantityOperationsTest.java
└── LambdaOperationsTest.java
```

### Test Pattern

**Use parameterized tests** following existing `FhirPathIntegrationTest.java`:

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArithmeticOperationsTest {

    private SparkSession spark;

    @BeforeAll
    void setupSpark() {
        spark = SparkSession.builder()
            .appName("test")
            .master("local[*]")
            .config("spark.ui.enabled", "false")
            .getOrCreate();
    }

    Stream<Arguments> expressions() {
        return Stream.of(
            Arguments.of("5 + 3", "8"),
            Arguments.of("10 - 4", "6"),
            Arguments.of("10 / 4", "2.5"),
            Arguments.of("5 + {}", null),          // null propagation
            Arguments.of("5.1 + 10.2", "15.3")     // decimal arithmetic
        );
    }

    @ParameterizedTest
    @MethodSource("expressions")
    void testExpressions(String expression, String expectedResult) {
        Column column = FhirPath.toColumn(expression);

        Dataset<Row> result = spark.range(1).select(column.alias("result"));
        String actual = result.first().isNullAt(0)
            ? null
            : valueToString(result.first().get(0));

        assertEquals(expectedResult, actual);
    }
}
```

**Benefits of this approach:**
- Easy to add test cases (just add to `Stream<Arguments>`)
- Clear, tabular test data
- Minimal boilerplate per test case
- Consistent with existing codebase pattern

### Test Coverage

**Include tests for:**
- All FHIRPath operators (arithmetic, comparison, boolean, etc.)
- All FHIRPath functions (count, where, select, first, etc.)
- Edge cases (null, empty collections, boundary values)
- Cardinality rules (singular → array conversions)
- Type conversions
- Lambda expressions with `$this` and `$index`
- Nested operations
- FHIR-specific functions (resolve, getValue, etc.)

**Source test cases from:**
- FHIRPath specification examples
- FHIR FHIRPath binding spec examples
- Edge cases discovered during development

### Implementation Approach

**Phase 1 (Initial)**: Use parameterized test pattern from `FhirPathIntegrationTest.java`
- `@ParameterizedTest` with `@MethodSource`
- `Stream<Arguments>` providing (expression, expectedResult) pairs
- Multiple test methods for different contexts:
  - `testFhirPathExpressions()` - no context (literals only)
  - `testFhirPathExpressionsWithContext()` - with context expression
  - `testFhirPathExpressionsWithResource()` - with full FHIR resource schema
- Easy to add test cases: just add `Arguments.of(expr, expected)` to stream

**Phase 2 (Future)**: Develop comprehensive test harness
- Still supports streamlined testing of large numbers of expressions
- Enhanced features:
  - Test case extraction from FHIRPath spec examples
  - Test data builders for FHIR resources
  - Assertion helpers for complex results
  - Performance benchmarks
  - Better error reporting and debugging

---

## Test Utilities

### SparkSession Management

Use `@TestInstance(PER_CLASS)` pattern from `FhirPathIntegrationTest.java`:

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyTest {
    private SparkSession spark;

    @BeforeAll
    void setupSpark() {
        spark = SparkSession.builder()
            .appName("test")
            .master("local[*]")
            .config("spark.ui.enabled", "false")
            .getOrCreate();
    }

    @AfterAll
    void teardownSpark() {
        if (spark != null) spark.stop();
    }
}
```

### Evaluation and Assertion Helpers

```java
// Evaluates Column and converts result to string for assertion
static String valueToString(Object value) {
    if (value instanceof BigDecimal bd) {
        return bd.stripTrailingZeros().toString();
    } else if (value instanceof scala.collection.Seq<?> seq) {
        List<?> javaList = scala.jdk.javaapi.CollectionConverters.asJava(seq);
        List<String> elements = javaList.stream()
            .map(MyTest::valueToString)
            .toList();
        return "[" + String.join(", ", elements) + "]";
    }
    return value.toString();
}
```

---

## Testing Checklist

### Component Tests
- [ ] Collection: singular/array dispatch, null handling, lambdas
- [ ] Quantity: field access, transformations
- [ ] LambdaExpression: `$this` binding, evaluation
- [ ] InvocationBinder: boxing/unboxing, type conversions

### FHIRPath Expression Tests
- [ ] Arithmetic operators (add, subtract, multiply, divide, mod)
- [ ] Comparison operators (=, !=, <, >, <=, >=)
- [ ] Boolean operators (and, or, xor, implies)
- [ ] Collection functions (count, where, select, first, last, etc.)
- [ ] String functions (substring, length, contains, etc.)
- [ ] Type operations (is, as, ofType)
- [ ] Quantity operations (comparison, arithmetic with units)
- [ ] Lambda expressions (where, select, all, any, etc.)
- [ ] Edge cases (null, empty, cardinality boundaries)
- [ ] Real FHIR data scenarios

---

## Key Principles

**1. Test Behavior, Not Implementation**
- Focus on correct results, not how SQL is generated
- Trust SparkSQL to work correctly
- Don't test internal handler logic directly

**2. Test at Right Level**
- Component tests: Atomic units with clear contracts
- FHIRPath tests: Complete system behavior

**3. Reusability First**
- FHIRPath tests are specification compliance suite
- Can reuse for other code generators
- Tests serve as documentation of FHIRPath semantics

**4. Minimal Mocking**
- Use real SparkSession (local mode is fast enough)
- Use real components (no mocks for handlers, wrappers)
- Only mock external dependencies if needed

**5. Comprehensive but Focused**
- Cover FHIRPath spec thoroughly
- Don't exhaustively test SparkSQL internals
- Focus on FHIRPath-specific semantics (null = empty, cardinality rules)

---

## Summary

| Test Level | What | Why | Example |
|------------|------|-----|---------|
| **Component** | Wrappers, utilities | Non-trivial logic worth isolating | Collection.count() |
| **FHIRPath Expression** | Full pipeline | User contract, reusable across impls | "age > 18" |
| **~~Handler/CodeGen~~** | ~~Plumbing~~ | ~~Already covered by FHIRPath tests~~ | ~~Skipped~~ |

**Key Decision**: Skip handler/CodeGen tests; invest in comprehensive, reusable FHIRPath expression test suite.

**Benefits**: Clear testing strategy, maintainable, reusable, tests what matters.
