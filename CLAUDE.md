# Claude Code Reference

## Project Overview

This project implements FHIRPath to SQL translation.

## Important References

### FHIRPath Specification

The complete FHIRPath specification is available at `specs/FHIRPath.md`. This document should be consulted when:
- Implementing FHIRPath operators and functions
- Resolving questions about FHIRPath semantics and behavior
- Verifying correct interpretation of the FHIRPath type system
- Understanding FHIRPath grammar and expression evaluation rules

## Testing Guidelines

### FHIRPath Function/Operator Unit Tests

For writing or reviewing unit tests for FHIRPath functions and operators, use the **fhirpath-test-writer agent**:

```
/task Use fhirpath-test-writer to write tests for [function name]
```

**Testing Philosophy:**
- We rely on SparkSQL's underlying implementations to work correctly
- Focus tests on FHIRPath-specific behavior and edge cases only
- Do NOT perform exhaustive testing of the underlying SQL operations

**The agent will:**
1. Read the FHIRPath specification for the function
2. Extract all spec examples and conditions
3. Generate minimal, focused test cases
4. Write complete test classes following project conventions

**Reference:** See `src/test/java/com/example/fhirpath/ir/string/SubstringTest.java` for an example of the testing style the agent produces.

**Agent location:** `.claude/agents/fhirpath-test-writer.md`

## Project Guidelines

### Design Documentation

The detailed design is in @DESIGN.md. Use it both when writing the code and also to record new or modified design choices.

### Java Coding Style

1. **Use `final` modifier whenever possible**
  - Mark all local variables as `final` if they are not reassigned
  - Mark method parameters as `final`
  - Mark class fields as `final` when they are initialized once
  - Example:
    ```java
    public static void processData(@Nonnull final String input) {
      final List<String> items = parseInput(input);
      final String result = transform(items);
      return result;
    }
    ```

2. **Use `jakarta.annotation.Nonnull` annotations**
  - Annotate all non-null method parameters with `@Nonnull`
  - Annotate all non-null return types with `@Nonnull`
  - This helps with static analysis and makes nullability explicit
  - Example:
    ```java
    @Nonnull
    public static Column structProduct(@Nonnull final Column... columns) {
      // implementation
    }
    ```

3. **Prefer Java Streams for collection operations**
  - Use streams for functional-style operations on collections
  - Example:
    ```java
    final List<Expression> expressions = Arrays.stream(columns)
        .map(ExpressionUtils::expression)
        .collect(Collectors.toList());
    ```

4. **Prefer immutable collections and streams over imperative loops:**
- Use `List.of()`, `Set.of()`, `Map.of()` for immutable collections
- Use Stream API (`map`, `filter`, `collect`) instead of for/while loops when possible
- Use `Stream.concat()` for combining streams
- Favor functional transformations over mutation

**Examples:**
```java
// Good: immutable collection with stream
List<IRNode> args = Stream.concat(
    Stream.of(targetIR),
    call.arguments().stream().map(this::analyze)
).toList();

// Avoid: mutable collection with loop
List<IRNode> args = new ArrayList<>();
args.add(targetIR);
for (AstNode arg : call.arguments()) {
    args.add(analyze(arg));
}
```

### Commit Messages

Keep commit messages succinct and focused:
- Max a few sentences describing the **purpose** of the change
- Focus on **what** and **why**, not **how**
- Avoid details that are easily visible in `git diff`
- Keep it concise and to the point