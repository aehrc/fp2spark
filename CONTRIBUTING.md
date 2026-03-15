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

## Development Guidelines

### Code Style

All Java code must follow the conventions in [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md).

### Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for system architecture and design patterns.

### Testing

See [TESTING.md](TESTING.md) for the test DSL, description format, and guidelines.

**Testing Philosophy:**
- Rely on SparkSQL's underlying implementations to work correctly
- Focus tests on FHIRPath-specific behavior and edge cases only
- Do NOT perform exhaustive testing of underlying SQL operations

## Commit Guidelines

Keep commit messages succinct and focused:

- **Max a few sentences** describing the purpose of the change
- Focus on **what** and **why**, not **how**
- Avoid details that are easily visible in `git diff`

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

- [ ] Implementation follows [ARCHITECTURE.md](ARCHITECTURE.md) patterns
- [ ] Code follows [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)
- [ ] Tests added following [TESTING.md](TESTING.md) guidelines
- [ ] Test classes named by capability (e.g., `StringFunctionsTest`, not `Stage14Test`)
- [ ] Tests cover spec examples and edge cases (nulls, empty collections, boundary conditions)
- [ ] All tests pass (`mvn test`)
- [ ] Zero compilation warnings (`mvn clean compile`)
- [ ] Javadoc comments added for new public methods/classes
- [ ] Documentation updated if public API or architecture changed

## Pull Request Process

1. **Communicate first**: Create or comment on an issue before starting significant work
2. **Create a feature branch** from `main`:
   ```bash
   git checkout -b issue/<issue-number>-<short-description>
   ```
3. **Make your changes**, write tests, and verify quality gates:
   ```bash
   mvn clean compile  # Must have ZERO warnings
   mvn test           # All tests must pass
   ```
4. **Commit** with clear messages following the format above
5. **Push** and create a pull request referencing the related issue

### Pull Request Checklist

- [ ] Code follows [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)
- [ ] All new code has unit tests
- [ ] All tests pass (`mvn test`)
- [ ] **Zero compilation warnings** (`mvn clean compile`)
- [ ] Documentation updated (if applicable)
- [ ] Commit messages follow guidelines
- [ ] PR references related issue number

## Code Quality Standards

### Compilation Warnings
- **Zero compilation warnings are required**
- CI will fail if any warnings are present

### Test Coverage
- All new code should have comprehensive unit tests
- Target: >80% coverage for core FHIRPath components

### Code Review
- All PRs require review before merging
- Address all review comments

## Questions or Issues?

- Check existing issues before creating a new one
- Provide detailed information when reporting bugs
- Include minimal reproduction steps for bug reports

## License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.
