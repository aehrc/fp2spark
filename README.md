# FHIRPath → SparkSQL Translator

## Overview

This project is a Java 17 library for parsing, analyzing, and compiling [FHIRPath](https://hl7.org/fhirpath/) expressions into [Apache Spark 3.5](https://spark.apache.org/) SQL `Column` expressions. It is designed for use in FHIR analytics pipelines where structured health data must be queried and transformed efficiently.

The translator pipeline:

```
FHIRPath string
   ↓ (ANTLR grammar)
AST (AstLiteral, AstTraversal, AstFunctionCall, ...)
   ↓ (Translator: type checking, overload resolution, implicit casts)
IR (Literal, Traversal, Add, Cast, Count, Exists, ...)
   ↓ (Backend)
Spark SQL Column
```

Key properties:

* **Early diagnostics** with line/column information.
* **Typed intermediate representation (IR)** with implicit conversion rules from FHIRPath.
* **Extensible**: new functions/operators can be registered in the FunctionRegistry.
* **FHIR-aware**: traversal nodes can be resolved against FHIR resource definitions.

---

## Features

* Parse FHIRPath expressions using ANTLR grammar.
* Build a simple, uniform **AST** (`AstLiteral`, `AstTraversal`, `AstFunctionCall`).
* Translate AST to **typed IR** nodes (`Add`, `Cast`, `Count`, `Exists`, etc.).
* Perform **type checking** and apply **FHIRPath implicit casts**:

  * `INTEGER → DECIMAL → QUANTITY`
  * `DATE → DATE_TIME`
* Report **semantic errors with source locations**.
* Translate IR nodes to **Spark SQL `Column`** expressions.
* Support **function and operator overloads** via a `FunctionRegistry`.

---

## Architecture

### 1. AST (Abstract Syntax Tree)

* Represents syntactic structure of FHIRPath expressions.
* Small set of nodes, prefixed with `Ast*`:

  * `AstLiteral`
  * `AstTraversal`
  * `AstFunctionCall`

### 2. IR (Intermediate Representation)

* Represents **typed, resolved** expressions.
* Node names are short and similar to Spark Catalyst:

  * `Literal`, `Traversal`, `Add`, `Cast`, `Count`, `Exists`, etc.
* Each IR node implements `IRNode` with a `getType()` method.

### 3. SourceMap

* Associates AST nodes with source locations (line, column, text).
* Uses `System.identityHashCode()` as stable keys.
* Keeps AST classes minimal (no embedded location data).

### 4. Type System

* Enum `Type` defines supported types: `STRING`, `INTEGER`, `DECIMAL`, `QUANTITY`, `DATE`, `DATE_TIME`, `BOOLEAN`.
* Rules for **implicit casts** and **common type resolution**.
* Example: `INTEGER + DECIMAL` → `DECIMAL`.

### 5. Function Registry

* Registry of functions/operators with overloads and builders.
* Responsible for creating specialized IR nodes.
* Example: `+` operator builds an `Add` IR node.

### 6. Translator

* Converts AST to IR:

  * Resolves functions/operators from registry.
  * Inserts `Cast` nodes as needed.
  * Throws `SemanticException` with source location on errors.

### 7. Spark Backend

* Converts IR nodes to Spark SQL `Column` expressions.
* Uses Spark 3.5 API (`functions.*`).
* Example:

  * `Add(left, right, DECIMAL)` → `leftCol.plus(rightCol)`.
  * `Cast(child, DATE_TIME)` → `childCol.cast("timestamp")`.

---

## Example

Input FHIRPath:

```fhirpath
1 + '2'
```

Pipeline:

1. **AST**

   ```
   AstFunctionCall("+", [AstLiteral(1), AstLiteral("2")])
   ```

2. **IR**

   ```
   Add(Cast(Literal(1, INTEGER) → DECIMAL),
       Cast(Literal("2", STRING) → DECIMAL),
       DECIMAL)
   ```

3. **Spark**

   ```java
   lit(1).cast("double").plus(lit("2").cast("double"))
   ```

---

## Error Reporting

Example: unknown function `cunt()`

```
SemanticException: No matching overload for function/operator 'cunt'
at line 1, column 7, fragment="cunt"
```

Errors always include line/column and offending expression snippet.

---

## Project Structure

```
fhirpath-translator/
  ├── fhirpath-core/
  │    ├── src/main/java/... (AST, IR, TypeSystem, Translator, Registry)
  │    └── src/main/antlr/... (FHIRPath grammar)
  ├── fhirpath-spark/
  │    └── src/main/java/... (SparkTranslator backend)
  └── fhirpath-tests/
       └── src/test/java/... (unit tests)
```

---

## Example Usage

```java
String expr = "Patient.name.given.count()";

// Parse FHIRPath string to AST
AstNode ast = parser.parse(expr);
SourceMap sourceMap = parser.getSourceMap();

// Translate AST → IR
AstToIrTranslator translator = new AstToIrTranslator(functionRegistry, sourceMap);
IRNode ir = translator.translate(ast);

// Translate IR → Spark Column
SparkTranslator sparkTranslator = new SparkTranslator();
Column col = sparkTranslator.translate(ir);

// Use in Spark DataFrame
Dataset<Row> patients = ...;
patients.select(col.alias("given_count")).show();
```

---

## Dependencies

* **Java**: 17
* **Spark**: 3.5
* **ANTLR**: for FHIRPath grammar
* **JUnit 5**: for tests

Example Maven dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>org.antlr</groupId>
    <artifactId>antlr4-runtime</artifactId>
    <version>4.13.1</version>
  </dependency>
  <dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-sql_2.12</artifactId>
    <version>3.5.0</version>
    <scope>provided</scope>
  </dependency>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.0</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

---

## Roadmap

* [ ] Add support for full FHIRPath function set.
* [ ] Support for collection operations (`where`, `select`, `flatten`).
* [ ] Integration with FHIR StructureDefinitions for traversal typing.
* [ ] Unit-aware Quantity handling.
* [ ] Optimizations (e.g. pushdown simplifications).

---

## License

Apache 2.0 (suggested — confirm based on project needs).

