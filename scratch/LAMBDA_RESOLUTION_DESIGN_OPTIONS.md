# Lambda Function Resolution - Design Options

## Context

**Current Situation:**
- Lambda-taking functions (where, select, exists, all, iif, etc.) require special analysis
- Arguments to lambda parameters need `$this` bound to collection element type
- Lambda functions have NO overloads (single signature per function)
- Lambda functions may be variadic (e.g., `iif`, `exists`)
- Arguments are either lambda OR non-lambda, never both across overloads

**Current Problems:**
1. Hardcoded function names in `isLambdaFunction()`
2. Separate code paths: `resolve()` vs `resolveLambda()`
3. Special `FunctionRegistry.resolveLambda()` method
4. Cannot handle functions with mixed lambda/non-lambda arguments

**Performance Requirement:**
- Each argument should be analyzed exactly once (avoid duplicate analysis)

---

## Design Option 1: Early Signature Inspection (Pre-Analysis)

### Approach
Get signatures before analyzing arguments, inspect first signature (since no overloads), then analyze each argument based on its parameter type.

```java
private IRNode resolveFunctionCall(AstFunctionCall call) {
    AstFunctionCall resolvedCall = resolveWithImplicitTarget(call);
    AstNode targetAst = resolvedCall.target();
    IRNode targetIR = analyze(targetAst);

    // Get signatures BEFORE analyzing arguments
    List<SignatureDefinition> signatures = OperationRegistry.getSignatures(call.functionName());

    if (signatures.isEmpty()) {
        // Fallback to special cases
        return handleSpecialFunctions(call, targetIR);
    }

    // Since lambda functions have no overloads, just pick first signature
    SignatureDefinition sig = signatures.get(0);

    // Determine element type for lambda binding
    Type elementType = extractElementType(targetIR.getType());

    // Analyze arguments based on signature parameter types
    List<IRNode> args = new ArrayList<>();
    args.add(targetIR);

    for (int i = 0; i < call.arguments().size(); i++) {
        Type paramType = sig.parameterTypes().get(i + 1); // +1 for target
        AstNode argAst = call.arguments().get(i);

        if (paramType instanceof LambdaType) {
            // Analyze with $this binding
            IRNode lambdaBody = withThisType(elementType).analyze(argAst);
            args.add(new Lambda(lambdaBody));
        } else {
            // Analyze normally
            args.add(analyze(argAst));
        }
    }

    // Resolve with pre-analyzed args
    return OverloadResolver.resolveCall(signatures, args);
}
```

### Pros
- ✅ Each argument analyzed exactly once
- ✅ Signature-driven (no hardcoded names)
- ✅ Simple control flow
- ✅ Works for variadic functions

### Cons
- ❌ Assumes first signature is correct (works only because no overloads)
- ❌ Still need special handling for functions not in registry
- ❌ Couples resolution order (must get signatures before analyzing args)

---

## Design Option 2: Lazy Argument Resolution (Analysis on Demand)

### Approach
Create an ArgumentResolver abstraction that caches analyzed arguments and resolves them on-demand based on parameter types during overload resolution.

```java
// Create a lazy argument resolver
class ArgumentResolver {
    private final Analyzer normalAnalyzer;
    private final Analyzer lambdaAnalyzer;
    private final List<AstNode> argAsts;
    private final Map<Integer, IRNode> cache = new HashMap<>();

    IRNode resolve(int index, Type paramType) {
        return cache.computeIfAbsent(index, i -> {
            AstNode argAst = argAsts.get(i);
            if (paramType instanceof LambdaType) {
                IRNode body = lambdaAnalyzer.analyze(argAst);
                return new Lambda(body);
            } else {
                return normalAnalyzer.analyze(argAst);
            }
        });
    }
}

private IRNode resolveFunctionCall(AstFunctionCall call) {
    AstFunctionCall resolvedCall = resolveWithImplicitTarget(call);
    IRNode targetIR = analyze(resolvedCall.target());

    List<SignatureDefinition> signatures = OperationRegistry.getSignatures(call.functionName());
    Type elementType = extractElementType(targetIR.getType());

    ArgumentResolver resolver = new ArgumentResolver(
        this,
        withThisType(elementType),
        call.arguments()
    );

    // OverloadResolver calls resolver.resolve() as needed
    return OverloadResolver.resolveCall(signatures, targetIR, resolver);
}
```

### Pros
- ✅ Each argument analyzed exactly once (cached)
- ✅ Works even if overloads existed (future-proof)
- ✅ Clean separation of concerns
- ✅ OverloadResolver controls when arguments are resolved

### Cons
- ❌ More complex (new ArgumentResolver abstraction)
- ❌ Over-engineered for current needs (no overloads exist)
- ❌ OverloadResolver needs API changes

---

## Design Option 3: Two-Phase with Signature Metadata

### Approach
Add explicit metadata to SignatureDefinition indicating which parameters are lambdas, then use this for fast-path optimization.

```java
// Add metadata to SignatureDefinition
record SignatureDefinition(
    List<Type> parameterTypes,
    ResultSpec resultSpec,
    int minArity,
    Set<Integer> lambdaParameterIndices  // NEW: which params are lambdas
) {
    boolean hasLambdaParameters() {
        return !lambdaParameterIndices.isEmpty();
    }
}

private IRNode resolveFunctionCall(AstFunctionCall call) {
    AstFunctionCall resolvedCall = resolveWithImplicitTarget(call);
    IRNode targetIR = analyze(resolvedCall.target());

    List<SignatureDefinition> signatures = OperationRegistry.getSignatures(call.functionName());

    // Check if any signature has lambda parameters
    boolean hasLambdas = signatures.stream().anyMatch(SignatureDefinition::hasLambdaParameters);

    if (!hasLambdas) {
        // Fast path: analyze all args normally
        List<IRNode> args = Stream.concat(
            Stream.of(targetIR),
            call.arguments().stream().map(this::analyze)
        ).toList();

        return resolveWithArgs(signatures, args);
    } else {
        // Lambda path: use signature to guide analysis
        SignatureDefinition sig = signatures.get(0); // Only one signature exists
        Type elementType = extractElementType(targetIR.getType());

        List<IRNode> args = new ArrayList<>();
        args.add(targetIR);

        for (int i = 0; i < call.arguments().size(); i++) {
            int paramIndex = i + 1; // +1 for target
            AstNode argAst = call.arguments().get(i);

            if (sig.lambdaParameterIndices().contains(paramIndex)) {
                IRNode body = withThisType(elementType).analyze(argAst);
                args.add(new Lambda(body));
            } else {
                args.add(analyze(argAst));
            }
        }

        return resolveWithArgs(signatures, args);
    }
}
```

### Pros
- ✅ Each argument analyzed exactly once
- ✅ Fast path for non-lambda functions (current common case)
- ✅ Signature metadata is explicit and self-documenting
- ✅ Easy to understand control flow

### Cons
- ❌ Requires adding metadata to SignatureDefinition
- ❌ Must update all signature creation sites
- ❌ Duplicates type information (lambdaParameterIndices vs parameterTypes)

---

## Design Option 4: Signature Introspection (Minimal Changes)

### Approach
Inspect existing signature parameter types to detect LambdaType, then use fast path for non-lambda functions and lambda path for lambda functions.

```java
private IRNode resolveFunctionCall(AstFunctionCall call) {
    AstFunctionCall resolvedCall = resolveWithImplicitTarget(call);
    IRNode targetIR = analyze(resolvedCall.target());

    List<SignatureDefinition> signatures = OperationRegistry.getSignatures(call.functionName());

    if (signatures.isEmpty()) {
        return handleSpecialFunctions(call, targetIR);
    }

    // Check if signature has lambda parameters by inspecting types
    SignatureDefinition sig = signatures.get(0);
    boolean hasLambdas = sig.parameterTypes().stream()
        .anyMatch(t -> t instanceof LambdaType);

    if (!hasLambdas) {
        // Fast path: normal function
        List<IRNode> args = Stream.concat(
            Stream.of(targetIR),
            call.arguments().stream().map(this::analyze)
        ).toList();
        return createOperation(signatures, args);
    }

    // Lambda path
    Type elementType = extractElementType(targetIR.getType());
    List<IRNode> args = new ArrayList<>();
    args.add(targetIR);

    for (int i = 0; i < call.arguments().size(); i++) {
        Type paramType = sig.parameterTypes().get(i + 1);
        AstNode argAst = call.arguments().get(i);

        if (paramType instanceof LambdaType) {
            IRNode body = withThisType(elementType).analyze(argAst);
            args.add(new Lambda(body));
        } else {
            args.add(analyze(argAst));
        }
    }

    return createOperation(signatures, args);
}
```

### Pros
- ✅ Each argument analyzed exactly once
- ✅ No changes to SignatureDefinition
- ✅ Signature-driven (introspects existing type info)
- ✅ Fast path for common case
- ✅ Minimal code changes

### Cons
- ❌ Assumes first signature (OK given no overloads)
- ❌ Slightly duplicated logic between fast/lambda paths

### Optional Enhancement
Extract lambda-specific logic into helper method:

```java
private List<IRNode> analyzeFunctionArguments(
    IRNode targetIR,
    List<AstNode> argAsts,
    SignatureDefinition sig
) {
    Type elementType = extractElementType(targetIR.getType());
    List<IRNode> args = new ArrayList<>();
    args.add(targetIR);

    for (int i = 0; i < argAsts.size(); i++) {
        Type paramType = sig.parameterTypes().get(i + 1);
        AstNode argAst = argAsts.get(i);

        if (paramType instanceof LambdaType) {
            IRNode body = withThisType(elementType).analyze(argAst);
            args.add(new Lambda(body));
        } else {
            args.add(analyze(argAst));
        }
    }

    return args;
}
```

---

## Recommendation

**Option 4: Signature Introspection** is recommended because:

1. **Minimal invasiveness**: Works with existing SignatureDefinition
2. **Performance**: Preserves fast path for non-lambda functions
3. **Simplicity**: No new abstractions, straightforward logic
4. **Signature-driven**: Eliminates hardcoded function names
5. **Maintainable**: Future lambda functions work automatically

---

## Implementation Notes

### Key Changes Required

1. **Analyzer.java**:
   - Remove `isLambdaFunction()` method
   - Remove `resolveLambdaFunction()` method
   - Unify into single `resolveFunctionCall()` with signature introspection
   - Add `extractElementType()` helper

2. **FunctionRegistry.java**:
   - Remove `resolveLambda()` method
   - Unify into single `resolve()` method

3. **No changes needed**:
   - SignatureDefinition
   - OverloadResolver
   - OperationRegistry
   - Lambda IR node

### Element Type Extraction

```java
private Type extractElementType(Type collectionType) {
    return (collectionType instanceof CollectionType ct)
        ? ct.elementType()
        : collectionType;
}
```

### Special Functions Handling

Functions not in OperationRegistry (getValue, equals, union) still need special handling:

```java
private IRNode handleSpecialFunctions(AstFunctionCall call, IRNode targetIR) {
    List<IRNode> args = Stream.concat(
        Stream.of(targetIR),
        call.arguments().stream().map(this::analyze)
    ).toList();

    return switch (call.functionName()) {
        case "getValue" -> new CastToSystem(args.get(0));
        case "equals" -> new Equals(args.get(0), args.get(1));
        case "union", "|" -> new Union(args.get(0), args.get(1));
        default -> throw new UnsupportedOperationException(
            "Function '" + call.functionName() + "' is not supported"
        );
    };
}
```
