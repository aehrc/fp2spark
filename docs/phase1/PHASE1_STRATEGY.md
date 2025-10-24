# Phase 1 Strategy: From Prototype to Production-Ready Project

## Vision

Transform the prototype into a well-organized software project with:
- ✅ Basic CI/CD setup (GitHub Actions)
- ✅ Project process documentation
- ✅ Code quality standards and enforcement
- ✅ Comprehensive test coverage with FHIRPath testing harness
- ✅ Diagnostic logging for troubleshooting
- ✅ Implementation guidelines for new FHIRPath capabilities
- ✅ Phase 1 FHIRPath functionality fully implemented

## Work Organization

**Epic**: "Implement FHIRPath Support for SQL on FHIR ShareableViewDefinition" (overall requirements)

**Individual Issues**: Small, focused tasks/features delivered via PRs

**Process**: All work captured in GitHub issues → delivered via pull requests

## Recommended Implementation Sequence

### Stage 1.1: Project Foundation & Code Quality Standards
*Goal: Establish CI/CD, documentation, and code quality standards*

**Single Issue**: "Establish Project Foundation & Code Quality Standards"

**Task Checklist:**
- [ ] Set up GitHub Actions workflow (Maven build + test execution)
- [ ] Set up code quality checks (compilation warnings = 0)
- [ ] PR validation pipeline
- [ ] Borrow from Pathling: CONTRIBUTING.md, CODE_OF_CONDUCT.md
- [ ] Adapt existing ARCHITECTURE.md, JAVA_CODING_STYLE.md
- [ ] Update README.md (project status, goals, getting started)
- [ ] Define code quality metrics and gates (defer coverage to Stage 1.3)
- [ ] Document coding standards and quality expectations

**Deliverable**: PR-ready project with CI pipeline, documentation, and quality standards (no coverage gate yet)

---

### Stage 1.2: Code Cleanup
*Goal: Remove out-of-scope code and bring existing code to quality standards*

**Single Issue**: "Clean Up Code for Phase 1 Scope"

**Task Checklist:**
- [ ] Audit codebase for out-of-scope functionality
- [ ] Remove/disable code not related to Phase 1 (FHIR-specific features)
- [ ] Remove/disable code not related to ShareableView scope
- [ ] Refactor existing code to meet quality metrics:
  - [ ] Zero compilation warnings
  - [ ] Apply SOLID principles
  - [ ] Align with current architecture (analyzer/operation/typing)
  - [ ] Code smells and violations addressed
- [ ] Update documentation to reflect Phase 1 scope

**Deliverable**: Clean, standards-compliant codebase scoped to Phase 1

---

### Stage 1.3: Testing Infrastructure
*Goal: Create testing harness and establish testing patterns*

**Single Issue**: "Implement FHIRPath Testing Infrastructure"

**Task Checklist:**
- [ ] Research Pathling's FHIRPath testing approach
- [ ] Design integration test framework:
  - [ ] Expression parser → evaluator → assertions
  - [ ] Test fixture utilities for System types
  - [ ] Support for empty collections and null handling
- [ ] Implement FHIRPath testing harness
- [ ] Add structured diagnostic logging:
  - [ ] Expression parsing
  - [ ] Type checking
  - [ ] Operation resolution
  - [ ] SQL generation
- [ ] Re-implement existing FHIRPath integration tests using new harness
- [ ] Document testing guidelines in TESTING.md or CLAUDE.md
- [ ] Create troubleshooting guide for diagnostic logging

**Coverage Strategy**: Not enforcing global coverage gate yet (comes later in Stage 1.9)

**Deliverable**: Working test harness with migrated tests, diagnostic logging, and testing documentation

---

### Stage 1.4: System Types & Literals
*Goal: Implement/verify System type support and literal parsing*

**Single Issue**: "Implement System Types and Literals"

**Implementation Approach:**
- Extensive tests using new harness first
- Implement/re-implement using current design
- New implementation can co-exist with existing (incremental change)
- Enforce coverage gate only on added/modified code

**Task Checklist:**
- [ ] **Tests**: String literals: `'hello'`, `'test'`
- [ ] **Tests**: Integer literals: `42`, `-10`, `0`
- [ ] **Tests**: Decimal literals: `3.14`, `-0.5`, `100.0`
- [ ] **Tests**: Boolean literals: `true`, `false`
- [ ] **Tests**: Type system integration for System types
- [ ] **Implementation**: Literal parsing for all System types
- [ ] **Implementation**: Type system integration
- [ ] Coverage gate on new/modified code only

**Deliverable**: Complete System type and literal support with tests

---

### Stage 1.5: Boolean Operators
*Goal: Implement boolean logic operators*

**Single Issue**: "Implement Boolean Operators"

**Task Checklist:**
- [ ] **Tests**: `and` - logical AND with propagating-empty semantics
- [ ] **Tests**: `or` - logical OR with propagating-empty semantics
- [ ] **Tests**: `not` - logical negation with propagating-empty semantics
- [ ] **Tests**: Edge cases (empty collections, null handling)
- [ ] **Implementation**: Boolean operator logic
- [ ] **Implementation**: Propagating-empty semantics
- [ ] Coverage gate on new/modified code only

**Deliverable**: Complete boolean operator support with tests

---

### Stage 1.6: Equality Operators
*Goal: Implement equality operators with primitive-only constraint*

**Single Issue**: "Implement Equality Operators (=, !=)"

**Task Checklist:**
- [ ] **Tests**: `=` on primitive types (String, Integer, Decimal, Boolean)
- [ ] **Tests**: `!=` on primitive types
- [ ] **Tests**: Collections of primitives
- [ ] **Tests**: Error handling for complex types (should fail)
- [ ] **Tests**: Empty collection semantics
- [ ] **Implementation**: Equality operators for primitives
- [ ] **Implementation**: Validation to reject complex types
- [ ] **Implementation**: Clear error messages for invalid types
- [ ] Coverage gate on new/modified code only

**Deliverable**: Equality operators with primitive-only constraint

---

### Stage 1.7: Comparison Operators
*Goal: Implement comparison operators*

**Single Issue**: "Implement Comparison Operators (<, <=, >, >=)"

**Task Checklist:**
- [ ] **Tests**: `<`, `<=`, `>`, `>=` on Integer/Decimal
- [ ] **Tests**: Comparison on String (lexicographic)
- [ ] **Tests**: Comparison on Date/DateTime (if supported)
- [ ] **Tests**: Type compatibility checking
- [ ] **Tests**: Empty collection semantics
- [ ] **Implementation**: Comparison operators with type enforcement
- [ ] **Implementation**: Type compatibility validation
- [ ] Coverage gate on new/modified code only

**Deliverable**: Complete comparison operator support with tests

---

### Stage 1.8: Arithmetic Operators
*Goal: Implement arithmetic operators*

**Single Issue**: "Implement Arithmetic Operators (+, -, *, /)"

**Task Checklist:**
- [ ] **Tests**: `+`, `-`, `*`, `/` on Integer
- [ ] **Tests**: `+`, `-`, `*`, `/` on Decimal
- [ ] **Tests**: Type checking and coercion rules (Integer → Decimal)
- [ ] **Tests**: Division by zero handling
- [ ] **Tests**: Empty collection semantics
- [ ] **Implementation**: Arithmetic operators with type enforcement
- [ ] **Implementation**: Type coercion logic
- [ ] Coverage gate on new/modified code only

**Deliverable**: Complete arithmetic operator support with tests

---

### Stage 1.9: Collection Functions (where, exists, empty)
*Goal: Implement collection filtering and checking functions*

**Single Issue**: "Implement Collection Functions: where, exists, empty"

**Task Checklist:**
- [ ] **Tests**: `where(criteria)` - collection filtering
- [ ] **Tests**: `exists()` - non-empty check
- [ ] **Tests**: `empty()` - empty check
- [ ] **Tests**: Empty collection semantics
- [ ] **Implementation**: Function parsing and resolution
- [ ] **Implementation**: Collection filtering logic
- [ ] **Implementation**: Existence checking
- [ ] Coverage gate on new/modified code only

**Deliverable**: Collection filtering and checking functions with tests

---

### Stage 1.10: Type & Selection Functions (ofType, first)
*Goal: Implement type filtering and element selection*

**Single Issue**: "Implement Type and Selection Functions: ofType, first"

**Task Checklist:**
- [ ] **Tests**: `ofType(type)` - type filtering with System types
- [ ] **Tests**: `first()` - first element or empty collection
- [ ] **Tests**: Bounds checking and edge cases
- [ ] **Implementation**: Type filtering logic
- [ ] **Implementation**: First element selection
- [ ] Coverage gate on new/modified code only

**Deliverable**: Type filtering and selection functions with tests

---

### Stage 1.11: Collection Access (Indexer)
*Goal: Implement indexer expressions*

**Single Issue**: "Implement Indexer Expressions"

**Task Checklist:**
- [ ] **Tests**: `collection[index]` syntax
- [ ] **Tests**: Bounds checking (out of bounds → empty collection)
- [ ] **Tests**: Negative indices (if supported)
- [ ] **Tests**: Edge cases (empty collection, index = 0)
- [ ] **Implementation**: Indexer parsing
- [ ] **Implementation**: Bounds checking and error handling
- [ ] Coverage gate on new/modified code only

**Deliverable**: Indexer expressions with comprehensive tests

---

### Stage 1.12: Test Coverage & Quality Gates
*Goal: Achieve comprehensive coverage and enable global gates*

**Single Issue**: "Establish Comprehensive Test Coverage and Quality Gates"

**Task Checklist:**
- [ ] Review code coverage for entire FHIRPath codebase
- [ ] Identify coverage gaps in existing code
- [ ] Implement additional tests for uncovered paths
- [ ] Target: >80% coverage for FHIRPath components
- [ ] Enable global test coverage gate in CI
- [ ] Verify all quality gates pass
- [ ] Document coverage requirements in CONTRIBUTING.md

**Deliverable**: >80% test coverage with global coverage gate enabled

---

### Stage 1.13: Implementation Guidelines & Documentation
*Goal: Enable future contributors and document Phase 1*

**Single Issue**: "Create Implementation Guidelines and Phase 1 Documentation"

**Task Checklist:**
- [ ] Create FHIRPath Implementation Guidelines:
  - [ ] How to add new functions
  - [ ] How to add new operators
  - [ ] Testing requirements and patterns
  - [ ] Code review checklist
- [ ] Update ARCHITECTURE.md with FHIRPath evaluation flow
- [ ] Document Phase 1 capabilities and limitations
- [ ] Create examples demonstrating all Phase 1 features
- [ ] Document Phase 1 completion status
- [ ] Prepare roadmap for Phase 2 (FHIR types)

**Deliverable**: Complete implementation guidelines and Phase 1 documentation

---

## Issue Template Structure

Each issue should include:

```markdown
## Context
[Why this is needed, what problem it solves]

## Scope
[What's in scope, what's explicitly out of scope]

## Acceptance Criteria
- [ ] Specific, testable criteria
- [ ] Test coverage requirement
- [ ] Documentation updated

## Implementation Notes
[Technical guidance, design considerations]

## References
[Links to specs, related issues, design docs]
```

## Pull Request Guidelines

Each PR should:
- Reference the issue it addresses
- Include tests (unit + integration where applicable)
- Pass CI checks (build, tests, quality gates)
- Update documentation if needed
- Be reviewed before merge

## Success Metrics

By end of Phase 1:
- ✅ CI pipeline operational (all PRs validated)
- ✅ Test coverage >80% for FHIRPath components
- ✅ All Phase 1 features implemented and tested
- ✅ Zero compilation warnings
- ✅ Documentation complete (CONTRIBUTING, TESTING, guidelines)
- ✅ Diagnostic logging in place
- ✅ Ready to begin Phase 2 (FHIR types)

## References

- Pathling project: `.local/pathling/` (borrow process docs, testing patterns)
- Epic issue: "Implement FHIRPath Support for SQL on FHIR ShareableViewDefinition"
- Phase 1 spec: `.local/work/issue-shareable-view-fhirpath-support.md`
