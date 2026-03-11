# Code Generation Design: Functional Operation Registry

## Overview

Operations are pure functions dispatched through a simple name → function map. No reflection, annotations, base classes, or per-invocation objects.

## Core Components

### `SparkOperationDef` (functional interface)

```java
@FunctionalInterface
public interface SparkOperationDef {
    Column generate(List<Column> args, List<IRNode> argNodes,
                    Type resultType, SparkCodeGenerator generator);
}
```

Parameters provide everything an operation might need:
- `args` — evaluated Spark Columns (null for Lambda arguments)
- `argNodes` — original IR nodes for type/cardinality metadata
- `resultType` — the operation's result type
- `generator` — the code generator (for lambda evaluation in where/iif)

### `SparkOperationRegistry`

Simple `Map<String, SparkOperationDef>` with convenience registration:

```java
registry.binary("and", Column::and);              // two args, ignore type
registry.unary("not", functions::not);             // one arg, ignore type
registry.register("add", (args, nodes, type, gen) -> { ... });  // full control
```

### `SparkCodeGenerator`

The visitor's `visitOperation()` does a single map lookup:

```java
SparkOperationDef def = registry.get(op.name());
return def.generate(argColumns, op.args(), op.getType(), this);
```

## Operation Groups

Operations are organized by domain in `codegen/spark/ops/`:

| Class | Operations | Registration Style |
|-------|-----------|-------------------|
| `BooleanOps` | and, or, xor, implies, not | `binary()` / `unary()` |
| `ArithmeticOps` | add, sub, multiply, divide, mod | `register()` (type dispatch) |
| `ComparisonOps` | gt, lt, geq, leq | `register()` (input type validation) |
| `CollectionOps` | count, exists, empty, first | `register()` (isSingular access) |
| `FilteringOps` | where, iif | `register()` (lambda evaluation) |

### Adding a New Operation

1. Choose the appropriate `*Ops` class (or create a new one)
2. Add the registration call — typically 1-5 lines
3. If new class, add `NewOps.register(registry)` to `SparkOperationRegistry.standard()`

### Examples

**Simple binary** — delegates directly to Spark Column method:
```java
registry.binary("and", Column::and);
```

**Type-dispatched** — switches on result type:
```java
registry.register("add", (args, nodes, type, gen) ->
    switch ((PrimitiveType) type) {
        case INTEGER, DECIMAL -> args.get(0).plus(args.get(1));
        case STRING -> concat(args.get(0), args.get(1));
        default -> throw new IllegalArgumentException(...);
    });
```

**Metadata-dependent** — uses IR node properties:
```java
registry.register("count", (args, nodes, type, gen) -> {
    var col = args.get(0);
    if (nodes.get(0).isSingular()) {
        return when(col.isNotNull(), lit(1)).otherwise(lit(0));
    } else {
        return when(col.isNotNull(), size(col)).otherwise(lit(0));
    }
});
```

**Lambda-evaluating** — delegates to generator methods:
```java
registry.register("where", (args, nodes, type, gen) -> {
    Lambda lambda = (Lambda) nodes.get(1);
    return gen.evaluateWhere(args.get(0), nodes.get(0).isSingular(), lambda);
});
```

## Design Rationale

The previous handler-based architecture (7 classes with reflection, annotations, stateful command objects) was overengineered for Stage 1 where operations are one-liner functions. The functional registry:

- **Eliminates accidental complexity**: No reflection, no annotations, no base classes
- **Makes the simple case simple**: `registry.binary("and", Column::and)` is one line
- **Scales linearly**: Adding an operation is O(1) lines, not O(1) classes
- **Keeps full power available**: `register()` provides access to types, IR nodes, and generator

## Future Consideration

If the number of type-dispatched operations grows significantly, consider a fluent type-checked mapping API:

```java
binary("sub", expr(Column::minus).forResultTypes(INTEGER, DECIMAL));
binary("add", expr(Column::plus).forResultTypes(INTEGER, DECIMAL),
              expr((l, r) -> concat(l, r)).forResultTypes(STRING));
```

Defer until there are enough operations to justify the extra abstraction.
