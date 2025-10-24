# Implement FHIRPath Support for SQL on FHIR ShareableViewDefinition

## Overview

Implement the FHIRPath language subset required for SQL on FHIR v2 ShareableViewDefinition profile. This enables defining tabular projections of FHIR resources using FHIRPath expressions for column definitions and filtering.

**Specification**: `specs/FHIRPath_Sharable_Reqquirements.md`

## Amendments to Original Specification

This implementation includes the following additions/modifications to the ShareableViewDefinition requirements:

- **Boolean literal** (`true`, `false`) - added for completeness
- **Full comparison operator set** - added `<`, `>=` to the spec's `>`, `<=`
- **Equality scope clarification** - `=` and `!=` explicitly limited to primitive types only

## Scope

### In Scope - Required Capabilities

#### Literals
- String: `'hello'`, `'Patient/123'`
- Integer: `42`, `-10`, `0`
- Decimal: `3.14`, `-0.5`, `100.0`
- **Boolean**: `true`, `false`

#### Functions
- `where(criteria)` - filter collections by boolean expression
- `exists()` - true if collection non-empty
- `empty()` - true if collection empty
- `extension(url)` - access FHIR extensions by URL
- `ofType(type)` - filter collection by type
- `first()` - return first element or empty collection

#### Operators

**Boolean Logic**:
- `and` - logical AND
- `or` - logical OR
- `not` - logical negation

**Arithmetic** (on Integer/Decimal):
- `+` - addition
- `-` - subtraction
- `*` - multiplication
- `/` - division

**Comparison**:
- `=` - equals (primitives only)
- `!=` - not equals (primitives only)
- `<` - less than
- `<=` - less than or equal
- `>` - greater than
- `>=` - greater than or equal

**Collection Access**:
- Indexer: `collection[0]`, `collection[index]`

#### SQL on FHIR Extension Functions
- `getResourceKey()` - returns primary key for current resource
- `getReferenceKey([type])` - returns foreign key from Reference element
  - MUST support relative literal form: `Patient/123`
  - Returns empty collection `{}` for unsupported/unresolvable references

### Out of Scope

**Equality on Complex Types** (not supported):
- Equality operators (`=`, `!=`) NOT supported for complex types:
  - CodeableConcept, Reference, Identifier, Period, etc.
  - Only primitive types supported: String, Integer, Decimal, Boolean, Date, DateTime, Time
  - Collections of primitives are supported

**Experimental Functions** (not yet normative):
- `join()` - collection joining
- `lowBoundary()`, `highBoundary()` - boundary functions


## Implementation Phases

### Phase 1: FHIRPath System Types
Support for FHIRPath System types only:
- **Literals**: String, Integer, Decimal, Boolean
- **Functions**: `where()`, `exists()`, `empty()`, `ofType()`, `first()`
- **Operators**: All boolean, arithmetic, comparison operators
- **Collection Access**: Indexer expressions
- **Excluded**:
  - No FHIR-specific types or resources
  - No `extension()` function
  - No SQL on FHIR extension functions

### Phase 2: FHIR Types and Resources
Add FHIR-specific support:
- **FHIR Types**: Support for FHIR primitive and complex types
- **Resource Navigation**: Path navigation through FHIR resource structures
- **Extension Access**: `extension(url)` function for accessing FHIR extensions
- **Type System**: Integration with FHIR type system

### Phase 3: SQL on FHIR Extensions
Add SQL on FHIR-specific functions:
- `getResourceKey()` - primary key for resources
- `getReferenceKey([type])` - foreign key from Reference elements
  - Support for relative literal references (`Patient/123`)
  - Empty collection for unsupported references

## References

- SQL on FHIR v2 [ViewDefinition](https://build.fhir.org/ig/FHIR/sql-on-fhir-v2/StructureDefinition-ViewDefinition.html)
- SQL on FHIR v2 [ShareableViewDefinition](https://build.fhir.org/ig/FHIR/sql-on-fhir-v2/StructureDefinition-ShareableViewDefinition.html)
- FHIRPath Specification: `specs/FHIRPath.md`
- FHIR FHIRPath Binding: `specs/FHIR_FHIRpath.md`
