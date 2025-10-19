# Java Coding Style

This document outlines the coding conventions for Java code within this project.
These conventions are designed to ensure code quality, maintainability, and consistency across the codebase. T
hey are based on industry best practices and project specific requirements.

## Naming Conventions

- **Classes and Interfaces:** Use PascalCase (e.g., `MyClass`).
- **Methods and Variables:** Use camelCase (e.g., `myVariable`, `calculateTotal`).
- **Constants:** Use UPPER_SNAKE_CASE (e.g., `MAX_SIZE`).
- **Packages:** Use lowercase, with words separated by dots (e.g., `au.csiro.pathling.fhirpath`).
- **Test Methods:** Name test methods to describe the scenario and expected outcome (e.g.,
  `testArrayCrossProductWithNullsAndEmptys`).
- **Use meaningful and descriptive names** for classes, methods, and variables. Avoid abbreviations unless widely
  understood.

## Structure and Organization

- **Each class should be defined in its own file.** Avoid inner classes, records, and enums unless necessary.
- **Keep methods short and focused** on a single responsibility.
- **Extract common logic** into reusable methods to avoid code duplication.
- **Package-private classes:** Use package-private visibility for classes that are internal implementation details and
  should not be exposed outside their package.

## Formatting and Style

- **Indentation:** Use 2 spaces for indentation, no tabs.
- **Line length:** Limit lines to 120 characters where practical.
- **Always use braces `{}`** for `if`, `else`, `for`, `while`, and `do` statements, even for single statements.
- **Use `final`** for variables, parameters, and methods that should not change.
- **Avoid magic numbers;** define constants with meaningful names.
- **Avoid deeply nested code;** refactor to improve readability.
- **Remove unused code, imports, and variables.**
- **Do not leave unused or commented-out code** in the codebase.

## Imports

- **Use explicit imports;** avoid wildcard imports (e.g., `import java.util.*`) except for static imports in test code.
- **Organize imports** by groups: standard library, third-party libraries, then project packages.
- **Remove unused imports.**

## Type Safety and Generics

- **Use generics** to provide type safety and avoid raw types and unchecked casts.
- **Avoid type casting** where possible; prefer generics and polymorphism.
- **Use type specifiers** for domain-specific type information when appropriate.

## Immutability and Data Objects

- **Prefer immutability** for data objects and collections where possible.
- **Use immutable collections** (e.g., `List.of()`, `Set.of()`, `Map.of()`) for data that should not change.
- **Make fields `final`** in immutable classes.
- **Consider using records or Lombok `@Value`** for simple immutable data carriers.

## Functional Programming

- **Prefer streams and lambdas** for collection processing, but avoid overcomplicating simple logic.
- **Use method references** where they improve readability (e.g., `::method` instead of `x -> method(x)`).
- **Keep lambdas short;** extract complex logic into named methods.
- **Use `@FunctionalInterface`** annotation for interfaces intended for lambda use.

## Documentation

- **Document public classes and methods**
  with [Javadoc comments](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html).
- **Include `@param`, `@return`, and `@throws` tags** in Javadoc where applicable.
- **Use `@author` tags** to attribute original authors of significant classes or modules.
- **Comments must use correct grammar** and be written as complete sentences, terminated with a period.
- **Focus comments on intent, rationale, or non-obvious behaviour.**
- **Update comments when code changes.**
- **Do not include TODOs** in submitted code; create an issue instead.
- **Use `@Deprecated` annotation and Javadoc** for deprecated code, including guidance on alternatives.

## Error Handling and Resource Management

- **Handle exceptions appropriately;** do not use empty catch blocks.
- **Throw specific exceptions;** avoid catching or throwing `Exception` or `Throwable` unless necessary.
- **Close resources** (e.g., streams, connections) in a `finally` block or use try-with-resources.
- **Do not ignore method return values** unless intentional and documented.
- **Document exceptions** that methods can throw using `@throws` in Javadoc.

## Logging and Output

- **Use logging frameworks** (e.g., SLF4J) instead of `System.out` or `System.err` for output.
- **Use appropriate log levels** (TRACE, DEBUG, INFO, WARN, ERROR).

## Testing

- **Write unit tests** for all public methods and critical logic.
- **Use parameterized tests** (`@ParameterizedTest`) for testing multiple scenarios with similar logic.
- **Name test methods descriptively** to indicate the scenario and expected outcome.
- **Use assertion libraries** (e.g., JUnit assertions, AssertJ) for clear and expressive test assertions.
- **Test edge cases** including null values, empty collections, and boundary conditions.

## Configuration and Security

- **Avoid hardcoding file paths, URLs, or credentials;** use configuration files or environment variables.
- **Do not expand secrets in run blocks** or log statements; this is security-sensitive.

## Access Modifiers and Encapsulation

- **Use access modifiers** (`private`, `protected`, `public`) appropriately to encapsulate data.
- **Default to the most restrictive access level** that meets the requirements.
- **Use package-private (no modifier)** for internal implementation classes.

## Annotations

- **Use `@Override`** for all overridden methods to catch errors at compile time.
- **Do not suppress warnings** (`@SuppressWarnings`) without a clear justification and comment.
- **Use `jakarta.annotation` package for annotations,** not `javax.annotation`. This project uses Jakarta EE annotations.
- **Use nullability annotations** (`jakarta.annotation.Nonnull` and `jakarta.annotation.Nullable`) on method parameters,
  return values, and class or record fields.
- **Use custom annotations** for domain-specific metadata when appropriate.

## Thread Safety

- **Document thread safety assumptions** for classes with shared or mutable state.
- **Use appropriate synchronization** mechanisms for concurrent access.
- **Prefer immutable objects** to avoid thread safety issues.

## Quality Assurance

- **Ensure code is free of major bugs, vulnerabilities, and code smells** as reported by SonarQube.
- **Run static analysis tools** regularly to maintain code quality.
- **Address code review feedback** promptly and thoroughly.

---

For general contribution guidelines, see [CONTRIBUTING.md](CONTRIBUTING.md).
