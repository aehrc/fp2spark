# FHIRPath → SparkSQL Translator (Direct-eval, single module)

## Overview

Java 17 library that parses FHIRPath and evaluates directly to Apache Spark 3.5 SQL Column expressions. The IR nodes expose eval() that returns a Spark Column, removing the need for a separate backend compiler.

Simplified pipeline:

```
FHIRPath string
  ↓ (ANTLR)
AST (AstLiteral, AstTraversal, AstFunctionCall)
  ↓ (Analyzer: typing, overload resolution, implicit casts)
IR (Literal, Traversal, Add, Cast, Count, Exists, ...)
  ↓ (IR.eval())
Spark Column
```

Key properties:

- Direct eval on IR nodes: Add.eval() returns left.eval().plus(right.eval()).
- Typed IR with simple implicit casts (INTEGER→DECIMAL, DATE→DATE_TIME, etc.).
- Pluggable FunctionRegistry for functions/operators and overload resolution.
- Single-module Maven project with ANTLR grammar and Java sources.

---

## Features

- Parse FHIRPath using ANTLR4.
- Build a minimal AST (literal, traversal, function call).
- Analyze into typed IR with implicit casts.
- Evaluate IR directly to Spark Column without a separate compiler.
- Basic functions/operators: +, -, count(), exists().

---

## Architecture

- AST (com.example.fhirpath.ast): AstLiteral, AstTraversal, AstFunctionCall.
- IR (com.example.fhirpath.ir): IRNode with Type getType() and Column eval(). Nodes: Literal, Traversal, Add, Sub, Cast, Count, Exists.
- Typing (com.example.fhirpath.typing): Type enum, TypeSystem rules, SparkTypeMapper for cast targets.
- Analyzer (com.example.fhirpath.analyzer): converts AST→IR and calls FunctionRegistry for function/operator nodes.
- Parser (com.example.fhirpath.parser): ANTLR grammar FhirPath.g4, generated lexer/parser, and AstBuilderVisitor.

---

## Usage

High-level API:

```java
import com.example.fhirpath.FhirPath;
import org.apache.spark.sql.Column;

Column c = FhirPath.toColumn("5 + 10");
// Use with a DataFrame
// df.select(c.alias("result")).show();
```

Manual pipeline:

```java
import com.example.fhirpath.parser.ParserFacade;
import com.example.fhirpath.analyzer.Analyzer;
import com.example.fhirpath.ast.AstNode;
import com.example.fhirpath.ir.IRNode;
import org.apache.spark.sql.Column;

AstNode ast = ParserFacade.parse("5 + 10");
IRNode ir = new Analyzer().analyze(ast);
Column col = ir.eval();
```

Notes:
- count() returns an aggregate Column intended for df.agg(...). You must use it in an aggregation context.
- exists() is a simple size(col) > 0 on the child column and assumes array semantics when appropriate.

---

## Project Structure

```
src/main/
  antrl/FhirPath.g4
  java/com/example/fhirpath/
    FhirPath.java
    analyzer/{ Analyzer.java, FunctionRegistry.java }
    ast/{ AstLiteral.java, AstTraversal.java, AstFunctionCall.java }
    ir/{ IRNode.java, Literal.java, Traversal.java, Add.java, Sub.java, Cast.java, Count.java, Exists.java }
    parser/{ AstBuilderVisitor.java, ParserFacade.java }
    typing/{ Type.java, TypeSystem.java, SparkTypeMapper.java }
    util/{ SourceLocation.java }
```

---

## Build

```bash
mvn -DskipTests package
```

---

## Coding Conventions

This project follows strict coding conventions to ensure code quality, safety, and maintainability:

### Nullability Annotations

**Always use `jakarta.annotation.Nonnull` (or `@Nullable`) on all method return types and parameters.**

```java
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@Nonnull
public String getName(@Nonnull final String id, @Nullable final String fallback) {
    return fallback != null ? fallback : "default";
}
```

Benefits:
- Explicit null-safety contracts in APIs
- IDE and static analysis tool support for null-checking
- Self-documenting code

### Immutability and Final

**Use `final` on method parameters and local variables whenever possible.**

```java
// Method parameters - always final
public void process(@Nonnull final String input, final int count) {
    // Local variables - final when not reassigned
    final String processed = input.trim();
    final List<String> results = new ArrayList<>();

    // Only non-final if reassignment is needed
    int remaining = count;
    while (remaining > 0) {
        results.add(processed);
        remaining--;
    }
}
```

**Prefer immutable data structures:**

```java
// Records for immutable data classes (Java 17+)
public record SignatureDefinition(
    @Nonnull List<Type> parameterTypes,
    @Nonnull ResultSpec resultSpec,
    int minArity
) implements TypeGroup { }

// Immutable collections
public static final Set<Type> NUMERIC = Set.of(INTEGER, DECIMAL);
public static final List<String> KEYWORDS = List.of("and", "or", "not");

// Prefer Collections.unmodifiableList/Set/Map for older patterns
private final List<String> items = Collections.unmodifiableList(sourceList);
```

**Benefits:**
- Thread-safety by default
- Easier to reason about code behavior
- Prevents accidental mutations
- Better performance in some cases (JVM optimizations)

### General Guidelines

1. **Immutability First**: Default to immutable unless mutability is explicitly required
2. **Final by Default**: Mark everything `final` unless reassignment is needed
3. **Null Safety**: Annotate all public APIs with `@Nonnull`/`@Nullable`
4. **Collections**: Use immutable collections (`List.of()`, `Set.of()`, `Map.of()`) or `Collections.unmodifiable*()`

---

## Roadmap

- Expand FHIRPath coverage: collections (where/select/flatten), string/date ops, quantity arithmetic.
- Cardinality tracking in IR (single/optional/many) to improve exists/collection ops.
- Improved diagnostics with source spans and better error messages.
- FHIR StructureDefinition-aware traversal typing.

---

## License

Apache 2.0 (placeholder).
