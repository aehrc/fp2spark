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

### Writing Tests

- Write unit tests for all new functionality
- Use `@ParameterizedTest` for testing multiple scenarios
- Follow the testing philosophy: focus on FHIRPath-specific behavior, not underlying SparkSQL operations

**Testing Philosophy:**
- Rely on SparkSQL's underlying implementations to work correctly
- Focus tests on FHIRPath-specific behavior and edge cases only
- Do NOT perform exhaustive testing of underlying SQL operations

See `src/test/java/com/example/fhirpath/ir/string/SubstringTest.java` for an example.

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

## Pull Request Process

1. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** following the code style guidelines

3. **Write tests** for your changes

4. **Run the full test suite** and ensure all tests pass:
   ```bash
   mvn clean test
   ```

5. **Commit your changes** with clear, focused commit messages

6. **Push to your fork** and create a pull request

7. **Address review feedback** promptly

### Pull Request Checklist

- [ ] Code follows [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)
- [ ] All new code has unit tests
- [ ] All tests pass (`mvn test`)
- [ ] No compiler warnings
- [ ] Documentation updated (if applicable)
- [ ] Commit messages are clear and follow guidelines

## Questions or Issues?

- Check existing issues before creating a new one
- Provide detailed information when reporting bugs
- Include minimal reproduction steps for bug reports

## License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.
