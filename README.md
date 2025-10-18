# FHIRPath to SparkSQL Translator

Parse and evaluate FHIRPath expressions directly to Apache Spark SQL Column expressions.

## Tech Stack

- **Java 21**
- **Apache Spark 4.0.1** (Scala 2.13)
- **ANTLR 4.13.1** for parsing

## Quick Start

### Build

```bash
mvn clean package
```

### Test

```bash
mvn test
```

### Usage

```java
import com.example.fhirpath.FhirPath;
import org.apache.spark.sql.Column;

// Simple expression
Column col = FhirPath.toColumn("5 + 10");

// With FHIR resource context
Column result = FhirPath.toColumn("Patient.name.family");
```

## Architecture

```
FHIRPath Expression
  ↓ ANTLR Parser
AST (Abstract Syntax Tree)
  ↓ Analyzer (type resolution, overload resolution)
IR (Intermediate Representation)
  ↓ Code Generator
Spark SQL Column
```

### Key Components

- **Parser** (`src/main/antlr/FhirPath.g4`): ANTLR grammar for FHIRPath
- **AST** (`com.example.fhirpath.ast`): Abstract syntax tree nodes
- **Analyzer** (`com.example.fhirpath.analyzer`): Type system and semantic analysis
- **IR** (`com.example.fhirpath.ir`): Typed intermediate representation
- **Code Generator** (`com.example.fhirpath.codegen.spark`): Spark SQL Column generation

## Supported Features

- Literals (strings, numbers, booleans)
- Arithmetic operators (`+`, `-`, `*`, `/`)
- Comparison operators (`=`, `>`, `<`, `>=`, `<=`)
- Collection operators (`|` union)
- Functions: `count()`, `exists()`, `where()`, `first()`, `iif()`, `substring()`
- Field traversal on FHIR resources
- Lambda expressions with `$this` binding

## Requirements

- Java 21+
- Maven 3.6+
- Apache Spark 4.0.1 (provided scope)

## License

Apache 2.0
