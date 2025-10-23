# Shared Operation Providers for Type Handlers

**Date**: 2025-01-22
**Extension**: Builds on `CODEGEN_ANNOTATION_FACTORY_METHODS.md`
**Purpose**: Eliminate duplication for operations shared across primitive types

---

## Table of Contents

1. [Motivation](#motivation)
2. [Design Overview](#design-overview)
3. [Core Abstractions](#core-abstractions)
4. [Implementation](#implementation)
5. [Usage Examples](#usage-examples)
6. [Complete Examples](#complete-examples)

---

## Motivation

### Problem: Massive Duplication Across Primitive Types

**Current Issue**: Many operations are identical across types:

```java
// IntegerHandler.java
@OperationMappings
public Stream<NamedMapping> arithmetic() {
    return Stream.of(
        binary("add", (a, b) -> a.plus(b)),
        binary("sub", (a, b) -> a.minus(b)),
        binary("multiply", (a, b) -> a.multiply(b)),
        binary("divide", (a, b) -> a.divide(b))
    );
}

@OperationMappings
public Stream<NamedMapping> comparisons() {
    return Stream.of(
        binary("gt", (a, b) -> a.gt(b)),
        binary("lt", (a, b) -> a.lt(b)),
        binary("geq", (a, b) -> a.geq(b)),
        binary("leq", (a, b) -> a.leq(b))
    );
}

// DecimalHandler.java - IDENTICAL CODE!
@OperationMappings
public Stream<NamedMapping> arithmetic() {
    return Stream.of(
        binary("add", (a, b) -> a.plus(b)),        // Same!
        binary("sub", (a, b) -> a.minus(b)),       // Same!
        binary("multiply", (a, b) -> a.multiply(b)), // Same!
        binary("divide", (a, b) -> a.divide(b))    // Same!
    );
}

@OperationMappings
public Stream<NamedMapping> comparisons() {
    return Stream.of(
        binary("gt", (a, b) -> a.gt(b)),   // Same!
        binary("lt", (a, b) -> a.lt(b)),   // Same!
        // ... identical to IntegerHandler
    );
}

// BooleanHandler.java
@OperationMappings
public Stream<NamedMapping> logic() {
    return Stream.of(
        binary("and", (a, b) -> a.and(b)),
        binary("or", (a, b) -> a.or(b)),
        binary("xor", (a, b) -> a.bitwiseXOR(b))
    );
}

// StringHandler.java
@OperationMappings
public Stream<NamedMapping> stringOps() {
    return Stream.of(
        direct("upper", str -> upper(str)),
        direct("lower", str -> lower(str)),
        direct("trim", str -> trim(str)),
        binary("concat", (a, b) -> concat(a, b)),
        // Comparisons SAME as Integer/Decimal!
        binary("gt", (a, b) -> a.gt(b)),    // Same!
        binary("lt", (a, b) -> a.lt(b))     // Same!
    );
}
```

**Duplication Analysis**:

| Operation Group | Shared By | LOC Duplicated |
|----------------|-----------|----------------|
| Arithmetic (add, sub, mul, div, mod) | INTEGER, DECIMAL | 10 lines × 2 = 20 |
| Comparisons (gt, lt, geq, leq) | INTEGER, DECIMAL, STRING, DATE, TIME, DATE_TIME | 8 lines × 6 = 48 |
| Boolean logic (and, or, xor, not, implies) | BOOLEAN | 10 lines × 1 = 10 |
| Math (abs, ceiling, floor, sqrt, exp, ln) | INTEGER, DECIMAL | 12 lines × 2 = 24 |
| **Total Duplication** | | **~102 lines** |

### Solution: Shared Operation Providers

**Key Insight**: Operations can be **provided** by shared modules, then **included** by handlers.

```java
// Shared provider (defined once)
public class CommonOperations {
    public static Stream<NamedMapping> arithmeticOps() {
        return Stream.of(
            binary("add", (a, b) -> a.plus(b)),
            binary("sub", (a, b) -> a.minus(b)),
            binary("multiply", (a, b) -> a.multiply(b)),
            binary("divide", (a, b) -> a.divide(b))
        );
    }

    public static Stream<NamedMapping> comparisonOps() {
        return Stream.of(
            binary("gt", (a, b) -> a.gt(b)),
            binary("lt", (a, b) -> a.lt(b)),
            binary("geq", (a, b) -> a.geq(b)),
            binary("leq", (a, b) -> a.leq(b))
        );
    }
}

// IntegerHandler - include shared operations
@OperationMappings
public Stream<NamedMapping> sharedOps() {
    return Stream.concat(
        CommonOperations.arithmeticOps(),
        CommonOperations.comparisonOps()
    );
}

// DecimalHandler - reuse same providers
@OperationMappings
public Stream<NamedMapping> sharedOps() {
    return Stream.concat(
        CommonOperations.arithmeticOps(),
        CommonOperations.comparisonOps()
    );
}

// StringHandler - include only comparisons
@OperationMappings
public Stream<NamedMapping> sharedOps() {
    return Stream.concat(
        CommonOperations.comparisonOps(),
        stringSpecificOps()  // String-specific operations
    );
}
```

**Benefits**:
- ✅ Define once, use in multiple handlers
- ✅ Clear separation: shared vs type-specific
- ✅ Easy to customize (override or extend)
- ✅ Reduces code by ~50-70% for primitive types

---

## Design Overview

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│ Shared Operation Providers (Static Utilities)          │
│                                                          │
│  CommonOperations.arithmeticOps()                       │
│  CommonOperations.comparisonOps()                       │
│  CommonOperations.mathOps()                             │
│  CommonOperations.booleanLogicOps()                     │
│  StringOperations.basicStringOps()                      │
│  StringOperations.regexOps()                            │
└────────────────────┬────────────────────────────────────┘
                     │
                     ↓ included by
┌─────────────────────────────────────────────────────────┐
│ Type Handlers                                           │
│                                                          │
│  IntegerHandler:                                        │
│    @OperationMappings                                   │
│    public Stream<NamedMapping> ops() {                  │
│      return Stream.concat(                              │
│        CommonOperations.arithmeticOps(),                │
│        CommonOperations.comparisonOps(),                │
│        CommonOperations.mathOps()                       │
│      );                                                  │
│    }                                                     │
│                                                          │
│  StringHandler:                                         │
│    @OperationMappings                                   │
│    public Stream<NamedMapping> ops() {                  │
│      return Stream.concat(                              │
│        CommonOperations.comparisonOps(),                │
│        StringOperations.basicStringOps()                │
│      );                                                  │
│    }                                                     │
└─────────────────────────────────────────────────────────┘
```

### Composition Patterns

**Pattern 1: Full Inclusion** (use all shared operations)
```java
@OperationMappings
public Stream<NamedMapping> ops() {
    return CommonOperations.arithmeticOps();
}
```

**Pattern 2: Selective Inclusion** (pick specific groups)
```java
@OperationMappings
public Stream<NamedMapping> ops() {
    return Stream.concat(
        CommonOperations.comparisonOps(),
        StringOperations.basicStringOps()
    );
}
```

**Pattern 3: Extension** (add type-specific operations)
```java
@OperationMappings
public Stream<NamedMapping> ops() {
    return Stream.concat(
        CommonOperations.arithmeticOps(),
        Stream.of(
            binary("mod", (a, b) -> a.mod(b))  // Integer-specific
        )
    );
}
```

**Pattern 4: Override** (customize shared operations)
```java
@OperationMappings
public Stream<NamedMapping> ops() {
    return Stream.concat(
        CommonOperations.arithmeticOps()
            .filter(m -> !m.operationName().equals("divide")),  // Exclude divide
        Stream.of(
            binary("divide", (a, b) -> customDivide(a, b))  // Custom implementation
        )
    );
}
```

---

## Core Abstractions

### 1. CommonOperations (Shared Provider)

```java
package com.example.fhirpath.codegen.spark.handler.shared;

import com.example.fhirpath.codegen.spark.handler.NamedMapping;
import org.apache.spark.sql.Column;

import java.util.stream.Stream;

import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;
import static org.apache.spark.sql.functions.*;

/**
 * Shared operation providers for primitive types.
 *
 * These operations work identically across multiple types:
 * - Arithmetic: INTEGER, DECIMAL
 * - Comparisons: INTEGER, DECIMAL, STRING, DATE, TIME, DATE_TIME
 * - Math functions: INTEGER, DECIMAL
 *
 * Usage:
 * <pre>
 * {@literal @}OperationMappings
 * public Stream<NamedMapping> ops() {
 *     return Stream.concat(
 *         CommonOperations.arithmeticOps(),
 *         CommonOperations.comparisonOps()
 *     );
 * }
 * </pre>
 */
public final class CommonOperations {

    private CommonOperations() {} // Utility class

    /**
     * Arithmetic operations: add, sub, multiply, divide
     *
     * Shared by: INTEGER, DECIMAL
     */
    public static Stream<NamedMapping> arithmeticOps() {
        return Stream.of(
            binary("add", (a, b) -> a.plus(b)),
            binary("sub", (a, b) -> a.minus(b)),
            binary("multiply", (a, b) -> a.multiply(b)),
            binary("divide", (a, b) -> a.divide(b))
        );
    }

    /**
     * Modulo operation
     *
     * Shared by: INTEGER, DECIMAL
     */
    public static Stream<NamedMapping> moduloOp() {
        return Stream.of(
            binary("mod", (a, b) -> a.mod(b))
        );
    }

    /**
     * Comparison operations: gt, lt, geq, leq
     *
     * Shared by: INTEGER, DECIMAL, STRING, DATE, TIME, DATE_TIME
     */
    public static Stream<NamedMapping> comparisonOps() {
        return Stream.of(
            binary("gt", (a, b) -> a.gt(b)),
            binary("lt", (a, b) -> a.lt(b)),
            binary("geq", (a, b) -> a.geq(b)),
            binary("leq", (a, b) -> a.leq(b))
        );
    }

    /**
     * Math functions: abs, ceiling, floor, sqrt, exp, ln, log
     *
     * Shared by: INTEGER, DECIMAL
     */
    public static Stream<NamedMapping> mathOps() {
        return Stream.of(
            unary("abs", value -> abs(value)),
            unary("ceiling", value -> ceil(value)),
            unary("floor", value -> floor(value)),
            unary("sqrt", value -> sqrt(value)),
            unary("exp", value -> exp(value)),
            unary("ln", value -> log(value)),
            unary("log", value -> log(10.0, value))
        );
    }

    /**
     * Truncate operation (truncate towards zero)
     *
     * Shared by: INTEGER, DECIMAL
     */
    public static Stream<NamedMapping> truncateOp() {
        return Stream.of(
            unary("truncate", value ->
                when(value.geq(lit(0)), floor(value))
                    .otherwise(ceil(value))
            )
        );
    }

    /**
     * Boolean logic operations: and, or, xor, implies, not
     *
     * Shared by: BOOLEAN only (but defined here for consistency)
     */
    public static Stream<NamedMapping> booleanLogicOps() {
        return Stream.of(
            binary("and", (a, b) -> a.and(b)),
            binary("or", (a, b) -> a.or(b)),
            binary("xor", (a, b) -> a.bitwiseXOR(b)),
            binary("implies", (a, b) -> not(a).or(b)),
            unary("not", value -> not(value))
        );
    }

    /**
     * All numeric operations (arithmetic + comparisons + math)
     *
     * Convenience method for INTEGER, DECIMAL
     */
    public static Stream<NamedMapping> allNumericOps() {
        return Stream.of(
            arithmeticOps(),
            moduloOp(),
            comparisonOps(),
            mathOps(),
            truncateOp()
        ).flatMap(s -> s);
    }

    /**
     * Comparable operations (just comparisons)
     *
     * For types that only support comparisons (DATE, TIME)
     */
    public static Stream<NamedMapping> comparableOps() {
        return comparisonOps();
    }
}
```

### 2. StringOperations (String-Specific Provider)

```java
package com.example.fhirpath.codegen.spark.handler.shared;

import com.example.fhirpath.codegen.spark.handler.NamedMapping;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.stream.Stream;

import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;
import static org.apache.spark.sql.functions.*;

/**
 * Shared operation providers for String type.
 */
public final class StringOperations {

    private StringOperations() {} // Utility class

    /**
     * Basic string operations: upper, lower, trim, length
     */
    public static Stream<NamedMapping> basicStringOps() {
        return Stream.of(
            unary("upper", str -> upper(str)),
            unary("lower", str -> lower(str)),
            unary("trim", str -> trim(str)),
            unary("length", str -> length(str))
        );
    }

    /**
     * String predicates: startsWith, endsWith, contains
     */
    public static Stream<NamedMapping> stringPredicates() {
        return Stream.of(
            binary("startsWith", (str, prefix) -> str.startsWith(prefix)),
            binary("endsWith", (str, suffix) -> str.endsWith(suffix)),
            binary("contains", (str, substr) -> str.contains(substr))
        );
    }

    /**
     * Regex operations: replace, matches
     */
    public static Stream<NamedMapping> regexOps() {
        return Stream.of(
            ternary("replace", (str, pattern, replacement) ->
                regexp_replace(str, pattern, replacement)),
            binary("matches", (str, pattern) ->
                functions.call_function("regexp_like", str, pattern))
        );
    }

    /**
     * Concatenation (can also be viewed as arithmetic for strings)
     */
    public static Stream<NamedMapping> concatOp() {
        return Stream.of(
            binary("add", (a, b) -> concat(a, b))  // String addition = concatenation
        );
    }

    /**
     * All string operations
     */
    public static Stream<NamedMapping> allStringOps() {
        return Stream.of(
            basicStringOps(),
            stringPredicates(),
            regexOps(),
            concatOp(),
            CommonOperations.comparisonOps()  // Strings are also comparable
        ).flatMap(s -> s);
    }
}
```

### 3. CollectionOperations (Collection-Related Shared Ops)

```java
package com.example.fhirpath.codegen.spark.handler.shared;

import com.example.fhirpath.codegen.spark.CodeGenContext;
import com.example.fhirpath.codegen.spark.handler.NamedMapping;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.stream.Stream;

import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;
import static org.apache.spark.sql.functions.*;

/**
 * Shared operation providers for collection-related operations.
 *
 * These work on ANY type (polymorphic).
 */
public final class CollectionOperations {

    private CollectionOperations() {} // Utility class

    /**
     * Collection aggregators: count, exists, empty
     *
     * Work on collections of ANY type
     */
    public static Stream<NamedMapping> aggregators() {
        return Stream.of(
            // count() - needs to know if singular or collection
            complexUnary("count", (child, ctx) -> {
                boolean isSingular = ctx.argTypes().get(0).isSingular();
                if (isSingular) {
                    return when(child.isNotNull(), lit(1)).otherwise(lit(0));
                } else {
                    return when(child.isNotNull(), functions.size(child))
                        .otherwise(lit(0));
                }
            }),

            // exists() - returns true if not empty
            complexUnary("exists", (child, ctx) ->
                when(child.isNotNull(), lit(true)).otherwise(lit(false))
            ),

            // empty() - returns true if empty
            complexUnary("empty", (child, ctx) ->
                when(child.isNull(), lit(true)).otherwise(lit(false))
            )
        );
    }

    /**
     * Element extractors: first
     */
    public static Stream<NamedMapping> elementExtractors() {
        return Stream.of(
            complexUnary("first", (child, ctx) -> {
                boolean isSingular = ctx.argTypes().get(0).isSingular();
                if (isSingular) {
                    return child;  // Singular: return itself
                } else {
                    return functions.get(child, lit(0));  // Collection: first element
                }
            })
        );
    }

    /**
     * All collection operations
     */
    public static Stream<NamedMapping> allCollectionOps() {
        return Stream.concat(
            aggregators(),
            elementExtractors()
        );
    }
}
```

---

## Implementation

### Handler Implementation Pattern

**Base Pattern**: Include shared operations + add type-specific

```java
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.handler.shared.*;
import java.util.stream.Stream;

public class IntegerHandler extends AnnotatedTypeHandler {

    /**
     * Include all numeric operations from shared provider.
     */
    @OperationMappings("Numeric operations (shared)")
    public Stream<NamedMapping> numericOps() {
        return CommonOperations.allNumericOps();
    }

    /**
     * Integer-specific operations (if any).
     */
    @OperationMappings("Integer-specific operations")
    public Stream<NamedMapping> integerSpecificOps() {
        return Stream.of(
            // Example: integer-specific rounding mode
            // (if different from default)
        );
    }

    // Complex operations still use @Operation if needed
    @Operation("power")
    public Column power(Column base, Column exponent) {
        // Integer exponentiation logic
        return pow(base, exponent).cast(DataTypes.IntegerType);
    }
}
```

---

## Usage Examples

### Example 1: Integer Handler (Full Shared)

```java
import com.example.fhirpath.codegen.spark.handler.shared.*;

/**
 * Handler for INTEGER type.
 *
 * Uses all shared numeric operations - no duplication!
 */
public class IntegerHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return CommonOperations.allNumericOps();
        // Includes: arithmetic, mod, comparisons, math, truncate
    }

    // No other methods needed - all operations are shared!
}
```

**Total LOC**: ~15 lines (vs ~80 lines without sharing)

### Example 2: Decimal Handler (Shared with Extension)

```java
import com.example.fhirpath.codegen.spark.handler.shared.*;

/**
 * Handler for DECIMAL type.
 *
 * Reuses numeric operations, adds decimal-specific operations.
 */
public class DecimalHandler extends AnnotatedTypeHandler {

    @OperationMappings("Shared numeric operations")
    public Stream<NamedMapping> numericOps() {
        return CommonOperations.allNumericOps();
    }

    @OperationMappings("Decimal-specific operations")
    public Stream<NamedMapping> decimalOps() {
        return Stream.of(
            binary("round", (value, precision) ->
                round(value, precision.cast(DataTypes.IntegerType)))
        );
    }
}
```

**Total LOC**: ~20 lines (vs ~90 lines without sharing)

### Example 3: String Handler (Selective Inclusion)

```java
import com.example.fhirpath.codegen.spark.handler.shared.*;

/**
 * Handler for STRING type.
 *
 * Includes comparisons from CommonOperations,
 * all string operations from StringOperations.
 */
public class StringHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return Stream.concat(
            CommonOperations.comparisonOps(),  // Strings are comparable
            StringOperations.allStringOps()    // String-specific operations
        );
    }

    // Complex operation: substring with null handling
    @Operation("substring")
    public Column substring(Column str, Column pos, Column len) {
        Column adjustedPos = pos.plus(lit(1));  // 0-based → 1-based
        Column safeLen = coalesce(len, lit(Integer.MAX_VALUE));

        Column nullCondition = str.isNull()
            .or(pos.isNull())
            .or(pos.leq(0))
            .or(pos.gt(length(str)));

        return when(not(nullCondition), substr(str, adjustedPos, safeLen));
    }
}
```

**Total LOC**: ~35 lines (vs ~120 lines without sharing)

### Example 4: Boolean Handler (Single Provider)

```java
import com.example.fhirpath.codegen.spark.handler.shared.*;

/**
 * Handler for BOOLEAN type.
 *
 * Only uses boolean logic operations.
 */
public class BooleanHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return CommonOperations.booleanLogicOps();
    }
}
```

**Total LOC**: ~10 lines (vs ~30 lines without sharing)

### Example 5: Date Handler (Comparisons Only)

```java
import com.example.fhirpath.codegen.spark.handler.shared.*;

/**
 * Handler for DATE type.
 *
 * Dates only support comparisons (no arithmetic in base FHIRPath).
 */
public class DateHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return CommonOperations.comparableOps();
        // Just: gt, lt, geq, leq
    }
}
```

**Total LOC**: ~10 lines (vs ~25 lines without sharing)

### Example 6: DateTime Handler (Override Pattern)

```java
import com.example.fhirpath.codegen.spark.handler.shared.*;
import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;

/**
 * Handler for DATE_TIME type.
 *
 * Dates support comparisons, but with precision handling.
 * Override default comparisons to use UDF when available.
 */
public class DateTimeHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> comparisonOps() {
        // Use shared comparisons as fallback, but prefer UDF
        return Stream.of("gt", "lt", "geq", "leq").map(op ->
            complexBinary(op, (left, right, ctx) ->
                ctx.udfRegistry().isRegistered("dateTimeCompare")
                    ? compareViaUdf(left, right, op, ctx)
                    : left.gt(right)  // Fallback: direct comparison
            )
        );
    }

    @Operation("add")
    public Column addQuantity(Column dateTime, Column quantity, CodeGenContext ctx) {
        // Complex: add duration to datetime
        return ctx.udfRegistry().isRegistered("dateTimeAdd")
            ? functions.call_function("dateTimeAdd", dateTime, quantity)
            : simpleAdd(dateTime, quantity);
    }

    private Column compareViaUdf(Column left, Column right, String op, CodeGenContext ctx) {
        Column result = functions.call_function("dateTimeCompare", left, right);
        return switch (op) {
            case "gt" -> result.gt(lit(0));
            case "lt" -> result.lt(lit(0));
            case "geq" -> result.geq(lit(0));
            case "leq" -> result.leq(lit(0));
            default -> throw new IllegalArgumentException();
        };
    }

    private Column simpleAdd(Column dateTime, Column quantity) {
        return dateTime.plus(quantity.getField("value"));
    }
}
```

**Total LOC**: ~45 lines (vs ~80 lines without sharing)

---

## Complete Examples

### Example: All Primitive Type Handlers

```java
// ========== IntegerHandler.java ==========
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.handler.shared.CommonOperations;
import java.util.stream.Stream;

public class IntegerHandler extends AnnotatedTypeHandler {
    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return CommonOperations.allNumericOps();
    }
}

// ========== DecimalHandler.java ==========
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.handler.shared.CommonOperations;
import java.util.stream.Stream;
import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;

public class DecimalHandler extends AnnotatedTypeHandler {
    @OperationMappings
    public Stream<NamedMapping> numericOps() {
        return CommonOperations.allNumericOps();
    }

    @OperationMappings
    public Stream<NamedMapping> decimalOps() {
        return Stream.of(
            binary("round", (value, precision) ->
                round(value, precision.cast(DataTypes.IntegerType)))
        );
    }
}

// ========== StringHandler.java ==========
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.CodeGenContext;
import com.example.fhirpath.codegen.spark.handler.shared.*;
import org.apache.spark.sql.Column;
import java.util.stream.Stream;

import static org.apache.spark.sql.functions.*;

public class StringHandler extends AnnotatedTypeHandler {
    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return Stream.concat(
            CommonOperations.comparisonOps(),
            StringOperations.allStringOps()
        );
    }

    @Operation("substring")
    public Column substring(Column str, Column pos, Column len) {
        Column adjustedPos = pos.plus(lit(1));
        Column safeLen = coalesce(len, lit(Integer.MAX_VALUE));
        Column nullCondition = str.isNull()
            .or(pos.isNull())
            .or(pos.leq(0))
            .or(pos.gt(length(str)));
        return when(not(nullCondition), substr(str, adjustedPos, safeLen));
    }
}

// ========== BooleanHandler.java ==========
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.handler.shared.CommonOperations;
import java.util.stream.Stream;

public class BooleanHandler extends AnnotatedTypeHandler {
    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return CommonOperations.booleanLogicOps();
    }
}

// ========== DateHandler.java ==========
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.handler.shared.CommonOperations;
import java.util.stream.Stream;

public class DateHandler extends AnnotatedTypeHandler {
    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return CommonOperations.comparableOps();
    }
}

// ========== TimeHandler.java ==========
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.handler.shared.CommonOperations;
import java.util.stream.Stream;

public class TimeHandler extends AnnotatedTypeHandler {
    @OperationMappings
    public Stream<NamedMapping> allOps() {
        return CommonOperations.comparableOps();
    }
}
```

**Summary**:
- **IntegerHandler**: 10 lines (was ~80)
- **DecimalHandler**: 20 lines (was ~90)
- **StringHandler**: 35 lines (was ~120)
- **BooleanHandler**: 10 lines (was ~30)
- **DateHandler**: 10 lines (was ~25)
- **TimeHandler**: 10 lines (was ~25)
- **Total**: 95 lines (was ~370)
- **Savings**: **74% reduction!**

---

## Shared Provider Organization

### Recommended Package Structure

```
com.example.fhirpath.codegen.spark.handler/
  ├── AnnotatedTypeHandler.java
  ├── TypeHandler.java
  ├── NamedMapping.java
  ├── MappingBuilder.java
  ├── @Operation.java
  ├── @OperationMappings.java
  │
  ├── shared/
  │   ├── CommonOperations.java      (arithmetic, comparisons, math, boolean)
  │   ├── StringOperations.java      (string-specific)
  │   ├── CollectionOperations.java  (collection aggregators)
  │   └── TemporalOperations.java    (date/time helpers - future)
  │
  ├── IntegerHandler.java
  ├── DecimalHandler.java
  ├── StringHandler.java
  ├── BooleanHandler.java
  ├── DateHandler.java
  ├── TimeHandler.java
  ├── DateTimeHandler.java
  ├── QuantityHandler.java
  └── ... (other handlers)
```

---

## Advanced Patterns

### Pattern 1: Conditional Inclusion

```java
@OperationMappings
public Stream<NamedMapping> selectiveOps() {
    return Stream.concat(
        CommonOperations.arithmeticOps()
            .filter(m -> !m.operationName().equals("divide")),  // Exclude divide
        Stream.of(
            binary("divide", (a, b) -> customDivide(a, b))  // Custom implementation
        )
    );
}
```

### Pattern 2: Operation Transformation

```java
@OperationMappings
public Stream<NamedMapping> transformedOps() {
    // Apply custom wrapper to all arithmetic operations
    return CommonOperations.arithmeticOps()
        .map(mapping -> new NamedMapping(
            mapping.operationName(),
            (args, ctx) -> {
                Column result = mapping.implementation().generate(args, ctx);
                return applyCustomWrapper(result);  // Apply transformation
            }
        ));
}
```

### Pattern 3: Multi-Source Composition

```java
@OperationMappings
public Stream<NamedMapping> compositeOps() {
    return Stream.of(
        CommonOperations.arithmeticOps(),
        CommonOperations.comparisonOps(),
        StringOperations.basicStringOps(),
        customOperations()  // Handler-specific
    ).flatMap(s -> s);
}

private Stream<NamedMapping> customOperations() {
    return Stream.of(
        unary("customOp", value -> /* implementation */)
    );
}
```

### Pattern 4: Parameterized Providers

```java
public class CommonOperations {
    /**
     * Parameterized comparison operations with custom null handling.
     */
    public static Stream<NamedMapping> comparisonOps(boolean nullsFirst) {
        return Stream.of(
            binary("gt", (a, b) -> nullsFirst
                ? coalesce(a, lit(MIN_VALUE)).gt(coalesce(b, lit(MIN_VALUE)))
                : a.gt(b)
            ),
            // ... other comparisons
        );
    }
}

// Usage:
@OperationMappings
public Stream<NamedMapping> ops() {
    return CommonOperations.comparisonOps(true);  // nulls first
}
```

---

## Benefits & Impact

### Code Reduction

| Handler | Without Sharing | With Sharing | Reduction |
|---------|-----------------|--------------|-----------|
| IntegerHandler | ~80 lines | ~10 lines | **88%** |
| DecimalHandler | ~90 lines | ~20 lines | **78%** |
| StringHandler | ~120 lines | ~35 lines | **71%** |
| BooleanHandler | ~30 lines | ~10 lines | **67%** |
| DateHandler | ~25 lines | ~10 lines | **60%** |
| TimeHandler | ~25 lines | ~10 lines | **60%** |
| **Total** | **~370 lines** | **~95 lines** | **74%** |

### Shared Provider LOC

| Provider | LOC |
|----------|-----|
| CommonOperations | ~120 |
| StringOperations | ~60 |
| CollectionOperations | ~40 |
| **Total** | **~220 lines** |

**Net Impact**:
- **Before**: 370 lines (handlers only)
- **After**: 95 lines (handlers) + 220 lines (shared) = 315 lines
- **Savings**: 55 lines (15% reduction)
- **Maintenance**: Changes to shared operations update ALL handlers automatically

### Maintainability Benefits

**Before**: Fix comparison bug
- Update IntegerHandler.java
- Update DecimalHandler.java
- Update StringHandler.java
- Update DateHandler.java
- Update TimeHandler.java
- Update DateTimeHandler.java
- **Total**: 6 files, 6 locations

**After**: Fix comparison bug
- Update CommonOperations.comparisonOps()
- **Total**: 1 file, 1 location
- **All 6 handlers updated automatically**

### Testability Benefits

**Before**:
- Test comparisons in IntegerHandlerTest
- Test comparisons in DecimalHandlerTest
- Test comparisons in StringHandlerTest
- ... (6 test classes, duplicated tests)

**After**:
- Test CommonOperations.comparisonOps() once
- Handler tests just verify shared ops are included
- **Reduced test duplication by ~60%**

---

## Migration Strategy

### Phase 1: Create Shared Providers (2-3 hours)

1. Create `shared/` package
2. Implement `CommonOperations` with all shared operations
3. Implement `StringOperations`
4. Implement `CollectionOperations`
5. Add comprehensive tests for each provider

### Phase 2: Refactor Primitive Handlers (4-6 hours)

1. **IntegerHandler**: Replace all operations with `CommonOperations.allNumericOps()`
2. **DecimalHandler**: Use shared ops + add `round()`
3. **StringHandler**: Use `CommonOperations.comparisonOps()` + `StringOperations.allStringOps()`
4. **BooleanHandler**: Use `CommonOperations.booleanLogicOps()`
5. **DateHandler/TimeHandler**: Use `CommonOperations.comparableOps()`

### Phase 3: Validate (1-2 hours)

1. Run all tests
2. Verify no regressions
3. Update documentation

**Total Effort**: 7-11 hours

---

## Guidelines

### When to Create Shared Provider

✅ **Create shared provider when**:
- Operation is identical across 2+ types
- Logic is simple and type-agnostic
- Implementation is stable (unlikely to diverge)

❌ **Don't create shared provider when**:
- Operation is type-specific
- Logic varies by type (even slightly)
- Only used by 1 type

### Naming Conventions

**Provider Classes**: `<Domain>Operations`
- `CommonOperations` - cross-type operations
- `StringOperations` - string-specific
- `CollectionOperations` - collection operations
- `TemporalOperations` - date/time helpers

**Provider Methods**: `<operationGroup>Ops()`
- `arithmeticOps()` - add, sub, multiply, divide
- `comparisonOps()` - gt, lt, geq, leq
- `mathOps()` - abs, ceiling, floor, sqrt
- `booleanLogicOps()` - and, or, xor, not

### Documentation

Each provider method should document:
```java
/**
 * Comparison operations: gt, lt, geq, leq
 *
 * Shared by: INTEGER, DECIMAL, STRING, DATE, TIME, DATE_TIME
 *
 * Semantics: Direct Spark Column comparison
 */
public static Stream<NamedMapping> comparisonOps() { ... }
```

---

## Conclusion

**Shared operation providers eliminate ~74% of code duplication** for primitive types through:
- ✅ Define once, use everywhere
- ✅ Single source of truth for shared operations
- ✅ Easy to maintain and test
- ✅ Clear separation: shared vs type-specific
- ✅ Flexible composition patterns

**Recommended adoption**:
1. Create shared providers for common operations
2. Use composition in handlers (`Stream.concat()`)
3. Override when type-specific behavior needed
4. Keep complex logic in `@Operation` methods

**Next Steps**:
1. Implement `CommonOperations` as PoC
2. Refactor IntegerHandler and DecimalHandler
3. Validate benefits
4. Roll out to all primitive handlers
