# TODO

## Functional Gaps

### Full UCUM support for Quantity

**Current limitation:** Quantity equality and comparison use strict code comparison — calendar duration codes and UCUM codes are compared as-is without any mapping. This means expressions like `1 second = 1 's'` return empty instead of `true`, which contradicts the FHIRPath spec (section "Quantity Equality").

**Spec requirement (lines 641-649):** The spec defines two levels of calendar-to-UCUM relationship:
- **Equality (`=`):** `second`/`seconds` = `'s'`, `millisecond`/`milliseconds` = `'ms'`
- **Equivalence (`~`) only:** `year`/`years` ~ `'a'`, `month`/`months` ~ `'mo'`, `week`/`weeks` ~ `'wk'`, `day`/`days` ~ `'d'`, `hour`/`hours` ~ `'h'`, `minute`/`minutes` ~ `'min'`

**What needs to be done:**
- Map calendar codes to their UCUM-equal equivalents during equality comparison (`second` → `s`, `millisecond` → `ms`)
- Ensure equivalence-only mappings (`year` ~ `'a'`, etc.) are NOT resolved by `=` but reserved for future `~` operator
- Fix test expectation: `1 second = 1 's'` should assert `true`, not empty
- Consider whether to normalize codes at parse time (in `QuantityValue`) or at comparison time (in `QuantityOps`)
