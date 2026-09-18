# FHIRPath to SQL

Compile FHIRPath expressions into SQL engine expressions — today, Apache Spark SQL
`Column` trees.

Rather than interpreting FHIRPath row by row, fp2sql translates an expression once,
ahead of execution, into a native query expression that the engine plans and optimises
like any hand-written query.

## Project Status

This project implements the FHIRPath language subset required for [SQL on FHIR v2 ShareableViewDefinition](https://build.fhir.org/ig/FHIR/sql-on-fhir-v2/StructureDefinition-ShareableViewDefinition.html). See [docs/SHAREABLE_VIEW_REQUIREMENTS.md](docs/SHAREABLE_VIEW_REQUIREMENTS.md) for the original requirements analysis (its phase breakdown reflects historical planning, not current status — see Supported Features below for what's implemented today).

It is under active development; the language surface below reflects what's implemented today, not a finished spec.

## Design

Two assumptions shape the whole system.

### Statically validated expressions

fp2sql resolves every type and cardinality at **compile time**, from the FHIR schema —
never from the data. An expression that compiles is guaranteed to evaluate on any
conformant dataset, whatever the values or collection sizes turn out to be. Errors that
a dynamic FHIRPath engine would surface mid-query — a math operator handed two values, a
`where()` clause that isn't boolean — surface here as a compilation failure, before a
single row is read.

This is a deliberate trade. The FHIRPath specification assumes a dynamic evaluator and
permits runtime singleton coercion, so fp2sql rejects a few expressions the spec allows:
`Patient.name + 'x'` fails to compile because `name` is schema-cardinality `MANY`, even
for a patient who happens to have exactly one name. The cost is paid at authoring time;
what you get back is that a compiled view definition cannot fail on data it has not seen
yet. See [D1 in SPEC_DIVERGENCES.md](SPEC_DIVERGENCES.md#d1-static-typing-and-cardinality) for the full consequences.

### One validation layer, pluggable emitters

Parsing, type resolution, overload resolution and cardinality checking are engine-agnostic
and happen exactly once. They produce a typed, target-independent IR in which every node
already carries its resolved `Shape` (element type + cardinality) — no type information is
recomputed downstream.

Emitting SQL is then a separate, deliberately thin concern: a visitor over that IR
(`IRNodeVisitor<T>`, where `T` is whatever the target builds — `Column` for Spark, a
`String` for a textual SQL dialect). A new backend implements that one interface and
touches nothing else; the analyzer, the type system and the spec-conformance test suite
are shared across every target, so dialect support is not re-litigated per engine.

**Spark is currently the only emitter shipped.** The seam is real and load-bearing in the
design, but a second backend has not been written yet.

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
import au.csiro.fhirpath.FhirPath;
import org.apache.spark.sql.Column;

// Simple expression
Column col = FhirPath.toColumn("5 + 10");

// With FHIR resource context
Column result = FhirPath.toColumn("Patient.name.family");
```

### Terminology server

Terminology functions — currently `memberOf()` — need a FHIR terminology server. Supply one via
`CompilationOptions`, the general options type for `toColumn`/`generate` (config beyond the
expression itself grows here, as a field, rather than as another parameter):

```java
import ca.uhn.fhir.context.FhirContext;
import au.csiro.fhirpath.CompilationOptions;
import au.csiro.fhirpath.terminology.DefaultTerminologyServiceFactory;
import au.csiro.fhirpath.typing.FhirResourceType;

var terminology = DefaultTerminologyServiceFactory.forServer("https://tx.ontoserver.csiro.au/fhir");
var observation = new FhirResourceType(
    FhirContext.forR4().getResourceDefinition("Observation"));
var options = CompilationOptions.defaults().withTerminologyServiceFactory(terminology);

Column vitalSigns = FhirPath.toColumn(
    "code.memberOf('http://hl7.org/fhir/ValueSet/observation-vitalsignresult')",
    null,            // %context — optional
    observation,
    options);
```

Membership is resolved with `ValueSet/$validate-code`. Responses are cached per JVM — by default up
to 200,000 answers for 10 minutes, matching Pathling's own fallback expiry — so repeated codes cost
one request rather than one per row. Unlike Pathling, fp2sql doesn't yet respect a server-provided
expiry or revalidate via ETag ([#288](https://github.com/aehrc/fp2spark/issues/288)). Tune the cache
size and TTL, and the connection timeouts, by constructing a `TerminologyConfiguration` directly.

**Without a configured server**, terminology functions still compile and evaluate, but every value
set is reported as unresolvable, which the FHIR FHIRPath specification maps to an *empty* result.
Because empty is falsy inside `where()`, an expression such as
`Observation.component.where(code.memberOf(url))` then yields **no** rows rather than failing. Each
such call site logs a warning when it is compiled, since the empty output is otherwise
indistinguishable from data that genuinely matched nothing.

**Error handling.** Any 4xx response — most often because the value set URI doesn't resolve, but
also, deliberately, an authentication failure (401/403) — is treated the same as an unconfigured
server: an empty result. This matches Pathling's terminology client exactly, including the
401/403 case; [#283](https://github.com/aehrc/fp2spark/issues/283) tracks the risk that carries (a
rate-limited or otherwise-misbehaving server returning some other 4xx could silently look like "no
code is a member" instead of failing the job). Anything else — a 5xx, or a connection problem that
survives retries — throws and fails the Spark task.

Some request failures are retried before that happens, by the underlying Apache HttpClient's
default retry handler — 2 retries by default, tune `retryEnabled`/`retryCount` on
`TerminologyConfiguration`. That default handler's exclusion list is narrower than "connection
problem" might suggest: a socket timeout, connection refused, DNS failure, and TLS failure are
**not** retried by it — only other `IOException`s (e.g. a connection reset mid-response) are. This
again matches Pathling's terminology client exactly, down to the retry handler class.

Authentication is not yet supported: the server must be reachable without credentials
([#282](https://github.com/aehrc/fp2spark/issues/282)).

## Architecture

```
FHIRPath expression
  │
  │  ── shared, engine-agnostic ──────────────────────────
  ↓
ANTLR parser        →  AST
  ↓
Analyzer            →  type resolution, overload resolution,
                       cardinality checking, implicit casts
  ↓
Typed IR               every node attributed with Shape(cardinality, type)
  │
  │  ── per-target emitter (IRNodeVisitor<T>) ────────────
  ↓
SparkCodeGenerator  →  Spark SQL Column
(other targets)     →  SQL string, …
```

### Key components

| Layer | Package | Role |
|---|---|---|
| Parser | `src/main/antlr/.../FhirPath.g4` | ANTLR grammar for FHIRPath |
| AST | `au.csiro.fhirpath.ast` | Untyped syntax tree |
| Analyzer | `au.csiro.fhirpath.analyzer` | Type system, semantic analysis, static validation |
| IR | `au.csiro.fhirpath.ir` | Typed, target-agnostic representation |
| Emitter | `au.csiro.fhirpath.spark` | Spark SQL `Column` generation |

Everything above the emitter boundary is shared. Operation signatures live in a single
`OperationRegistry`, which is what makes auditing the implementation against the FHIRPath
specification tractable.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full design and
[TYPE_SYSTEM.md](TYPE_SYSTEM.md) for the element-first type model.

## Supported Features

**Language core**:
- Literals: String, Integer, Decimal, Boolean, Date, DateTime, Time, Quantity
- Boolean operators: `and`, `or`, `xor`, `implies`, `not()`
- Arithmetic and math operators/functions: `+`, `-`, `*`, `/`, `mod`, `ceiling()`, `floor()`,
  `round()`, `sqrt()`, `truncate()`, `exp()`, `ln()`, `log()`
- Comparison and equality operators: `=`, `!=`, `<`, `<=`, `>`, `>=`
- Membership and set operators: `in`, `contains`, `union` (`|`), `combine()`, `intersect()`,
  `exclude()`, `distinct()`, `isDistinct()`, `subsetOf()`, `supersetOf()`
- String functions: `substring()`, `startsWith()`, `endsWith()`, `contains()`, `matches()`,
  `replace()`, `replaceMatches()`, `length()`, `upper()`, `lower()`, `trim()`, `split()`,
  `join()`, `toChars()`
- Type functions and operators: `is`, `as`, `ofType()`, `type()`, `convertsTo*()`, `to*()`
- Filtering and projection: `where()`, `select()`, `repeat()`
- Existence and subsetting: `exists()`, `empty()`, `all()`, `first()`, `last()`, `single()`,
  `skip()`, `take()`, collection indexer (`collection[index]`)
- Utility functions: `trace()`, `iif()`

**FHIR-specific**:
- Field traversal on FHIR resources
- `extension()` function
- `resolve()` for reference navigation
- SQL on FHIR extension functions: `getResourceKey()`, `getReferenceKey()`
- Terminology functions: `memberOf()` (needs a terminology server — see
  [Terminology server](#terminology-server))

**Not yet implemented**:
- Equivalence operators: `~`, `!~`
- Terminology server authentication ([#282](https://github.com/aehrc/fp2spark/issues/282))
- Terminology response cache respecting server-provided expiry / ETag revalidation
  ([#288](https://github.com/aehrc/fp2spark/issues/288))

See [SPEC_DIVERGENCES.md](SPEC_DIVERGENCES.md) for known differences from the FHIRPath
specification and reference implementations.

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

## Licensing and attribution

This project is licensed under the [Apache License, Version 2.0](LICENSE),
copyright © Commonwealth Scientific and Industrial Research Organisation
(CSIRO).

This is experimental, research software. It is provided without warranty of
any kind, express or implied, including but not limited to fitness for a
particular purpose. See the [LICENSE](LICENSE) for the full terms.
