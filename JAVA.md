# FHIRPath → Spark SQL Translator (Java Scaffold, direct-eval)

This document specifies the Java scaffolding for a FHIRPath→Spark SQL translator with a simplified design: IR nodes evaluate directly to Spark Columns.

---

## Package Layout

```
com.example.fhirpath
 ├── FhirPath.java
 ├── ast
 │    ├── AstNode.java
 │    ├── AstTraversal.java
 │    ├── AstFunctionCall.java
 │    ├── AstLiteral.java
 ├── ir
 │    ├── IRNode.java
 │    ├── Add.java
 │    ├── Sub.java
 │    ├── Cast.java
 │    ├── Count.java
 │    ├── Exists.java
 │    ├── Literal.java
 │    ├── Traversal.java
 ├── typing
 │    ├── Type.java
 │    ├── TypeSystem.java
 │    ├── SparkTypeMapper.java
 ├── analyzer
 │    ├── Analyzer.java
 │    ├── FunctionRegistry.java
 ├── parser
 │    ├── FhirPath.g4
 │    ├── AstBuilderVisitor.java
 │    ├── ParserFacade.java
 └── util
      ├── SourceLocation.java
```

---

## Design Summary

- Direct eval: each IRNode exposes Column eval().
- Analyzer performs basic typing and inserts implicit casts (e.g., INTEGER→DECIMAL).
- FunctionRegistry resolves operators and functions (e.g., +, -, count, exists).
- No separate compiler/backend class is needed.

---

## IR Layer (direct eval)

Example node:

```java
public class Add implements IRNode {
    private final IRNode left; private final IRNode right;
    public Add(IRNode left, IRNode right) { this.left = left; this.right = right; }
    public Type getType() { return Type.DECIMAL; }
    public org.apache.spark.sql.Column eval() { return left.eval().plus(right.eval()); }
}
```

Other nodes follow the same pattern (Sub.minus, Cast.cast, Count.count, Exists.size>0, Literal.lit, Traversal.col).

---

## Analyzer + Registry

- Analyzer maps AST→IR and infers literal types.
- FunctionRegistry implements add/sub with numeric promotion to DECIMAL, and simple count/exists.

---

## Parser

- ANTLR grammar supports literals, identifiers, function calls, and + / - with left-associativity.
- AstBuilderVisitor folds infix +, - into function-call AST nodes that the Analyzer resolves.

---

## Public API

Use the facade to compile a FHIRPath string into a Spark Column:

```java
import com.example.fhirpath.FhirPath;
import org.apache.spark.sql.Column;

Column c = FhirPath.toColumn("5 + 10");
```

Aggregate functions like count() return aggregate Columns; use them inside DataFrame.agg(...).

---

## Roadmap

- Cardinality tracking in IR to better support collection semantics and exists().
- More functions and operators, null/empty semantics, dates/quantities.
- Improved diagnostics with source locations.
