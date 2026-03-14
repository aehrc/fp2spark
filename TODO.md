# TODO

## Functional Gaps

### Full UCUM support for Quantity

**Current limitation:** Quantity equality and comparison use strict code comparison — calendar duration codes and UCUM codes are compared as-is without any mapping. This means expressions like `1 second = 1 's'` return empty instead of `true`, which contradicts the FHIRPath spec (section "Quantity Equality").

**Spec requirement (lines 641-649):** The spec defines two levels of calendar-to-UCUM relationship:
- **Equality (`=`):** `second`/`seconds` = `'s'`, `millisecond`/`milliseconds` = `'ms'`
- **Equivalence (`~`) only:** `year`/`years` ~ `'a'`, `month`/`months` ~ `'mo'`, `week`/`weeks` ~ `'wk'`, `day`/`days` ~ `'d'`, `hour`/`hours` ~ `'h'`, `minute`/`minutes` ~ `'min'`

### Safe traversal for missing struct fields

**Current limitation:** Traversing a field that exists in the FHIR type model but is absent from the Spark schema (due to Pathling encoder depth limits) throws an `AnalysisException`. Instead, it should return NULL (empty collection).

**What needs to be done:**
- Detect when a struct field is missing from the Spark schema at code generation time
- Return `lit(null)` instead of `col.getField(name)` for missing fields
- This enables safe traversal of deeply nested types even when the encoder truncates the schema

---

### Full UCUM support for Quantity

**What needs to be done:**
- Map calendar codes to their UCUM-equal equivalents during equality comparison (`second` → `s`, `millisecond` → `ms`)
- Ensure equivalence-only mappings (`year` ~ `'a'`, etc.) are NOT resolved by `=` but reserved for future `~` operator
- Fix test expectation: `1 second = 1 's'` should assert `true`, not empty
- Consider whether to normalize codes at parse time (in `QuantityValue`) or at comparison time (in `QuantityOps`)
