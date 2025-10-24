# FHIRPath to SparkSQL Translator

Parse and evaluate FHIRPath expressions directly to Apache Spark SQL Column expressions.

## Project Status

**Current Phase**: Phase 1 - FHIRPath System Types Support

This project is implementing the FHIRPath language subset required for [SQL on FHIR v2 ShareableViewDefinition](https://build.fhir.org/ig/FHIR/sql-on-fhir-v2/StructureDefinition-ShareableViewDefinition.html). See [docs/SHAREABLE_VIEW_REQUIREMENTS.md](docs/SHAREABLE_VIEW_REQUIREMENTS.md) for complete requirements and [issue #3](https://github.com/piotrszul/fp2spark/issues/3) for tracking.

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

**Phase 1 (In Progress)**:
- Literals: String, Integer, Decimal, Boolean
- Boolean operators: `and`, `or`, `not`
- Arithmetic operators: `+`, `-`, `*`, `/`
- Comparison operators: `=`, `!=`, `<`, `<=`, `>`, `>=`
- Functions: `where()`, `exists()`, `empty()`, `ofType()`, `first()`
- Collection indexer: `collection[index]`

**Coming in Phase 2**:
- FHIR-specific types and resources
- Field traversal on FHIR resources
- `extension()` function

**Coming in Phase 3**:
- SQL on FHIR extension functions: `getResourceKey()`, `getReferenceKey()`

## Requirements

- Java 21+
- Maven 3.6+
- Apache Spark 4.0.1 (provided scope)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines, code style requirements, and pull request process.

Key points:
- All PRs must have **zero compilation warnings**
- Follow [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)
- Write tests for all new code
- Create or reference an issue before starting work

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - System architecture and design patterns
- [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md) - Java coding conventions
- [docs/SHAREABLE_VIEW_REQUIREMENTS.md](docs/SHAREABLE_VIEW_REQUIREMENTS.md) - FHIRPath requirements
- [docs/phase1/PHASE1_STRATEGY.md](docs/phase1/PHASE1_STRATEGY.md) - Phase 1 implementation plan

## License

Apache 2.0
