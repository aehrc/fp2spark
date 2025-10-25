# Contributing to FHIRPath to SparkSQL Translator

Thank you for your interest in contributing to this project!

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.6+
- Git

### Setup

1. Fork and clone the repository
2. Build the project:
   ```bash
   mvn clean install
   ```
3. Run tests to verify setup:
   ```bash
   mvn test
   ```

## Your First Contribution

**Never contributed to open source before?** Here's a quick walkthrough:

### Step-by-Step Workflow

1. **Find a task**: Look for issues labeled `good-first-issue` or `help-wanted`

2. **Claim it**: Comment "I'd like to work on this" to avoid duplicates

3. **Fork & clone**: Fork this repo, clone your fork locally

4. **Set up upstream**:
   ```bash
   git remote add upstream git@github.com:piotrszul/fp2spark.git
   git fetch upstream
   ```

5. **Create branch**:
   ```bash
   git checkout main
   git pull upstream main
   git checkout -b issue/<issue-number>-short-description
   ```

6. **Make changes**: Edit code, following [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)

7. **Test locally**:
   - `mvn clean compile` (must have ZERO warnings)
   - `mvn test` (all tests must pass)
   - Add tests for your changes (see [TESTING.md](TESTING.md))

8. **Commit**: Use conventional commit format (see Commit Guidelines below)

9. **Push & PR**:
   ```bash
   git push origin issue/<issue-number>-short-description
   ```
   Then open PR on GitHub against `main` branch

10. **Review**: Address feedback, update PR as needed

### Good First Contributions

Here are some great ways to start:

**Writing Tests**
- Use the `fhirpath-test-writer` agent to write tests for FHIRPath functions
- See [CLAUDE.md](CLAUDE.md#testing-guidelines) for agent usage
- Follow patterns in [TESTING.md](TESTING.md)

**Documentation**
- Fix typos or improve clarity in documentation
- Add missing examples to existing docs
- Improve code comments and Javadoc

**Code Quality**
- Add missing `@Nonnull`/`@Nullable` annotations
- Add missing Javadoc comments to public methods
- Extract magic numbers into named constants

### Local Testing Checklist

Before creating your PR, verify:

- [ ] `mvn clean compile` produces ZERO warnings
- [ ] `mvn test` passes all tests
- [ ] New code has unit tests (see [TESTING.md](TESTING.md))
- [ ] Code follows [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)
- [ ] Commit messages follow format (see Commit Guidelines below)

## Development Guidelines

### Code Style

**All Java code must follow the project's coding conventions.**

See [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md) for detailed guidelines including:
- Naming conventions
- Use of `final` modifier
- Nullability annotations (`@Nonnull`, `@Nullable`)
- Immutability preferences
- Documentation requirements

**Key points:**
- Use `final` for all parameters and local variables that are not reassigned
- Annotate all method parameters and return types with `@Nonnull` or `@Nullable`
- Prefer immutable collections (`List.of()`, `Set.of()`, `Map.of()`)
- Use Java Streams for functional-style collection operations
- Keep methods short and focused on a single responsibility

### Architecture

For understanding the project architecture and design decisions, see:
- [ARCHITECTURE.md](ARCHITECTURE.md) - System architecture and design patterns

## Testing

This project uses a fluent DSL for writing FHIRPath tests that are self-documenting and easy to read.

### Testing Guide

**See [TESTING.md](TESTING.md) for comprehensive guidelines including:**
- Test description format that makes tests understandable without consulting code
- When to use descriptions vs. when expressions are self-explanatory
- Examples of good vs. bad description usage
- Available test methods and context support
- Group organization and test structure

### Writing Tests

**Use the fluent test builder DSL:**

```java
@TestFactory
Stream<DynamicTest> testArithmetic() {
    return builder()
        .group("Integer addition")
        .testEquals(15, "5 + 10")
        .testEquals(10, "5 + 5")
        .group("Division")
        .testEquals(5.0, "10 / 2", "Division always returns decimal")
        .build();
}
```

**Testing Philosophy:**
- Rely on SparkSQL's underlying implementations to work correctly
- Focus tests on FHIRPath-specific behavior and edge cases only
- Do NOT perform exhaustive testing of underlying SQL operations
- Test descriptions should make the test understandable without reading code

**Test output format:** `expression [with context] => expected [: description] [group]`

Example: `5 + 10 => 15 [Integer addition]`

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ClassName

# Run with coverage
mvn clean test jacoco:report
```

## Commit Guidelines

Keep commit messages succinct and focused:

- **Max a few sentences** describing the purpose of the change
- Focus on **what** and **why**, not **how**
- Avoid details that are easily visible in `git diff`
- Keep it concise and to the point

### Commit Message Format

```
<type>: <short summary>

<optional body explaining what and why>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `docs`: Documentation changes
- `build`: Build system or dependency changes
- `chore`: Maintenance tasks

**Examples:**

```
feat: add support for iif() function with collection-wise lambdas

Implements the FHIRPath iif() conditional function with proper lambda
binding semantics. Both criterion and result lambdas use COLLECTION_WISE
binding where $this refers to the entire input collection.
```

```
fix: correct type resolution for union operator

Fixed issue where union operator incorrectly promoted INTEGER to DECIMAL
when combining heterogeneous numeric collections.
```

## Definition of Done

A contribution is considered complete when ALL applicable criteria are met:

### For Bug Fixes

- [ ] Bug is reproducible with a test case that initially fails
- [ ] Root cause identified and explained in commit message or PR description
- [ ] Fix implemented with minimal scope (only what's needed)
- [ ] All existing tests still pass (`mvn test`)
- [ ] New test added that would catch this bug in the future (regression test)
- [ ] Zero compilation warnings (`mvn clean compile`)
- [ ] Code follows [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)

### For New Features

- [ ] Design discussed in issue comments (for non-trivial features)
- [ ] Implementation follows [ARCHITECTURE.md](ARCHITECTURE.md) patterns and principles
- [ ] Comprehensive unit tests added using test DSL (see [TESTING.md](TESTING.md))
- [ ] Tests cover edge cases (nulls, empty collections, boundary conditions)
- [ ] All tests pass (`mvn test`)
- [ ] Zero compilation warnings (`mvn clean compile`)
- [ ] ARCHITECTURE.md updated if design patterns changed
- [ ] README.md updated if public API changed
- [ ] Javadoc comments added for all public methods/classes
- [ ] Code follows [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)

### For Tests

- [ ] **Test class named by capability**: Use `[Capability]Test` format (e.g., `TypesAndLiteralsTest`, `ArithmeticOperatorsTest`)
  - ✅ GOOD: `StringFunctionsTest` (named after FHIRPath capability)
  - ❌ BAD: `Stage14Test` (tied to implementation stage)
  - ❌ BAD: `Issue15Test` (tied to issue number)
- [ ] Tests follow [TESTING.md](TESTING.md) guidelines (fluent DSL, descriptions when needed)
- [ ] Tests are focused on FHIRPath-specific behavior (not exhaustive SQL testing)
- [ ] **Spec-driven test coverage**: All examples and edge cases from referenced FHIRPath spec sections are tested
- [ ] Test descriptions make tests understandable without reading code
- [ ] All tests pass (`mvn test`)
- [ ] Zero compilation warnings (`mvn clean compile`)

### For Documentation

- [ ] Content is technically accurate
- [ ] Follows existing documentation style and format
- [ ] Code examples are tested and working (if included)
- [ ] Links are valid and point to correct locations
- [ ] Grammar and spelling checked
- [ ] Markdown renders correctly (preview in IDE or GitHub)

### For Refactoring

- [ ] Behavior is unchanged (all existing tests still pass)
- [ ] Code quality improved (complexity reduced, readability enhanced)
- [ ] Follows SOLID principles (see [ARCHITECTURE.md](ARCHITECTURE.md))
- [ ] Zero compilation warnings (`mvn clean compile`)
- [ ] No test coverage regression

### Writing Good Issue Acceptance Criteria

When creating feature requests or bug reports, include clear acceptance criteria:

**Bug fix example:**
```
## Acceptance Criteria
- [ ] Expression `(1 ; 2) + 3` throws CardinalityMismatchException
- [ ] Error message identifies left operand as the problem
- [ ] Test added to prevent regression
```

**Feature example:**
```
## Acceptance Criteria
- [ ] `substring(string, start)` function implemented
- [ ] `substring(string, start, length)` overload implemented
- [ ] Follows FHIRPath spec semantics (1-based indexing)
- [ ] Tests cover spec examples from FHIRPath.md
- [ ] Tests cover edge cases (negative indices, out of bounds)
- [ ] OperationRegistry updated with signatures
```

## Pull Request Process

1. **Communicate first**: Create or comment on an issue before starting significant work
   - Prevents duplicate efforts
   - Ensures alignment with project goals
   - Opportunity to discuss approach

2. **Create a feature branch** from `main`:
   ```bash
   git checkout -b issue/<issue-number>-<short-description>
   ```

3. **Make your changes** following the code style guidelines

4. **Write tests** for your changes

5. **Run the full test suite** and ensure all tests pass:
   ```bash
   mvn clean test
   ```

6. **Verify quality gates**:
   ```bash
   # Must have ZERO compilation warnings
   mvn clean compile
   ```

7. **Commit your changes** with clear, focused commit messages

8. **Push to your fork** and create a pull request

9. **Address review feedback** promptly

### Pull Request Checklist

- [ ] Code follows [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)
- [ ] All new code has unit tests
- [ ] All tests pass (`mvn test`)
- [ ] **Zero compilation warnings** (`mvn clean compile`)
- [ ] Documentation updated (if applicable)
- [ ] Commit messages are clear and follow guidelines
- [ ] PR references related issue number

### What Happens After You Submit a PR?

1. **Automated checks run** (CI builds, tests, warning checks)
   - Must pass before human review
   - Fix failures and push updates

2. **Maintainer review** (typically within 2-3 business days)
   - Review focuses on: correctness, tests, style, design, documentation
   - Expect 1-3 rounds of feedback for non-trivial changes

3. **Address feedback**
   - Respond to ALL comments or explain disagreement
   - Make requested changes in new commits (easier to review)
   - Re-request review when ready

4. **Approval & merge**
   - Requires 1 maintainer approval
   - All CI checks must be green
   - Maintainer will squash/merge commits

## Code Quality Standards

This project enforces the following quality gates:

### Compilation Warnings
- **Zero compilation warnings are required**
- CI will fail if any warnings are present
- Address all warnings before submitting PR

### Test Coverage
- Test coverage gates will be enforced starting in Phase 1, Stage 1.9
- Until then, focus on quality over quantity
- All new code should have comprehensive unit tests
- Target: >80% coverage for core FHIRPath components (to be enforced)

### Code Review
- All PRs require review before merging
- Address all review comments
- Maintain respectful, constructive dialogue

## Questions or Issues?

- Check existing issues before creating a new one
- Provide detailed information when reporting bugs
- Include minimal reproduction steps for bug reports
- Communicate before starting significant work

## License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.
