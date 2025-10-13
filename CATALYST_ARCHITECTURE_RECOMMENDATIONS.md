# Apache Spark Catalyst Architecture Analysis & Recommendations for FHIRPath IR

## Executive Summary

This document analyzes how Apache Spark's Catalyst optimizer architecture can inform improvements to the FHIRPath IR (Intermediate Representation) system. The current FHIRPath implementation uses a single-phase resolved tree construction, while Catalyst uses a sophisticated multi-phase approach with unresolved → resolved transformation pipelines.

**Key Recommendation**: Adopt a two-phase approach inspired by Catalyst to enable SQL dialect flexibility, better error reporting, and more maintainable code.

---

## Current FHIRPath IR Architecture

### Current Design (Spark 3.5.6)

```java
// Single interface - both type and evaluation
public interface IRNode {
    Type getType();           // Type information
    Column eval();            // SparkSQL generation
}

// Concrete nodes know their types immediately
public record Add(IRNode left, IRNode right) implements IRNode {
    public static final List<FunctionSignature> SIGNATURES = List.of(...);

    @Override
    public Type getType() {
        return left.getType();  // Type known at construction
    }

    @Override
    public Column eval() {
        return left.eval().plus(right.eval());  // Direct SparkSQL generation
    }
}
```

**Analysis Phase**: `Analyzer.analyze(AstNode)` produces fully resolved `IRNode` trees
- Overload resolution happens during tree construction
- Cast nodes inserted immediately
- Type checking performed eagerly
- SparkSQL generation coupled with type information

**Strengths**:
1. Simple, direct translation from AST → IR
2. Type safety enforced immediately
3. No separate resolution phase needed
4. Easy to understand and debug

**Limitations**:
1. **Dialect Lock-in**: `eval()` returns Spark `Column` - hard to target other SQL dialects
2. **No Inspection**: Cannot analyze unresolved expressions before type binding
3. **Limited Transformations**: Hard to apply tree rewrites after construction
4. **Tight Coupling**: Type system and evaluation logic intertwined
5. **Error Messages**: Type errors thrown during construction, harder to provide context

---

## Spark Catalyst Architecture

### Phase 1: Unresolved Expressions

Catalyst starts with **unresolved expressions** that don't know their types:

```scala
// From: sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/unresolved.scala

case class UnresolvedFunction(
    nameParts: Seq[String],
    arguments: Seq[Expression],
    isDistinct: Boolean,
    filter: Option[Expression] = None,
    ignoreNulls: Boolean = false
) extends Expression {

  override lazy val resolved = false

  override def dataType: DataType =
    throw UnresolvedException("dataType")

  override def nullable: Boolean =
    throw UnresolvedException("nullable")
}

case class UnresolvedAttribute(nameParts: Seq[String]) extends Expression {
  override lazy val resolved = false
  // Similar - throws on dataType/nullable access
}
```

**Key Properties**:
- `resolved: Boolean` flag indicates analysis state
- Accessing `dataType` or `nullable` throws exceptions before resolution
- Tree structure established, but semantics unknown

### Phase 2: Analysis & Resolution

The `Analyzer` transforms unresolved → resolved expressions through **rule-based transformations**:

```scala
// From: sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala

class Analyzer(catalogManager: CatalogManager) extends RuleExecutor[LogicalPlan] {

  lazy val batches: Seq[Batch] = Seq(
    Batch("Resolution", fixedPoint,
      ResolveReferences,
      ResolveRelations,
      ResolveFunctions,
      // ... many more rules
    ),
    Batch("Post-Hoc Resolution", Once,
      ResolveAggregateFunctions,
      // ...
    ),
    Batch("Type Coercion", fixedPoint,
      TypeCoercion.typeCoercionRules: _*
    ),
    // ... more batches
  )
}

// Rules are transformations: LogicalPlan => LogicalPlan
abstract class Rule[TreeType <: TreeNode[_]] {
  def apply(plan: TreeType): TreeType
}
```

**Key Insights**:
1. **Multiple Passes**: Rules run in batches until fixed point
2. **Separation of Concerns**: Different rules handle different aspects
3. **Composable**: Rules can be added/removed/reordered
4. **Inspectable**: Can examine plan between rule applications

### Phase 3: Type Coercion

Type coercion is a **separate concern** handled by dedicated rules:

```scala
// From: sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/TypeCoercion.scala

object TypeCoercion {
  def typeCoercionRules: List[Rule[LogicalPlan]] = List(
    WidenSetOperationTypes,
    InConversion,
    PromoteStrings,
    DecimalPrecision,
    BooleanEquality,
    FunctionArgumentConversion,
    ImplicitTypeCasts,
    // ... many more
  )

  // Finds common type for multiple expressions
  def findTightestCommonType(types: Seq[DataType]): Option[DataType]

  // Inserts cast nodes where needed
  def implicitCast(e: Expression, target: DataType): Expression = {
    if (e.dataType == target) e
    else Cast(e, target)
  }
}
```

**Key Properties**:
- Type coercion **separate from** function resolution
- Rules pattern-match on expression trees and insert casts
- Can see full context when making coercion decisions
- Different rules for different coercion strategies

### Phase 4: Expression Resolution

The `Expression` trait defines the contract for all expressions:

```scala
// From: sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/expressions/Expression.scala

abstract class Expression extends TreeNode[Expression] {

  // Indicates whether this expression has been resolved
  lazy val resolved: Boolean = childrenResolved && checkInputDataTypes().isSuccess

  // Type of the expression's result
  def dataType: DataType

  // Whether the expression can return null
  def nullable: Boolean

  // Child expressions (for tree traversal)
  def children: Seq[Expression]

  // Interpreted evaluation (for small data or tests)
  def eval(input: InternalRow = null): Any

  // Code generation (for performance)
  protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode

  // Validates input types
  def checkInputDataTypes(): TypeCheckResult
}
```

**Key Design Principles**:
1. **Late Binding**: Type information provided after resolution
2. **Tree Structure**: Expressions form a tree via `children`
3. **Two Evaluation Modes**: Interpreted (`eval`) + generated code (`doGenCode`)
4. **Validation**: Explicit `checkInputDataTypes()` method
5. **Lazy Resolution**: `resolved` computed from children state

---

## Comparison: Current FHIRPath vs Catalyst

| Aspect | FHIRPath (Current) | Catalyst |
|--------|-------------------|----------|
| **Phases** | Single (AST → Resolved IR) | Multi (AST → Unresolved → Resolved) |
| **Type Information** | Immediate (constructor time) | Deferred (after analysis) |
| **Overload Resolution** | During IR construction | During analysis phase |
| **Type Coercion** | Embedded in OverloadResolver | Separate TypeCoercion rules |
| **Cast Insertion** | OverloadResolver.adapt() | TypeCoercion.ImplicitTypeCasts rule |
| **SQL Generation** | Coupled (IRNode.eval()) | Separated (Catalyst → SparkPlan) |
| **Transformations** | None (tree built once) | Extensive (rule-based optimizer) |
| **Resolution State** | No tracking (always resolved) | Explicit `resolved` flag |
| **Error Context** | Limited (thrown during build) | Rich (full tree context available) |
| **Dialect Support** | Spark-only (`Column` return type) | Abstracted (logical → physical) |

---

## Catalyst-Inspired Recommendations

### Recommendation 1: Introduce Two-Phase Architecture

**Before**:
```java
public interface IRNode {
    Type getType();  // Always available
    Column eval();   // Spark-specific
}
```

**After (Catalyst-inspired)**:
```java
// Phase 1: Unresolved IR (tree structure only)
public interface UnresolvedIRNode {
    boolean isResolved();  // Always false
    List<UnresolvedIRNode> children();

    // Throws UnresolvedException if accessed before resolution
    Type getType();
}

// Phase 2: Resolved IR (with type information)
public interface ResolvedIRNode {
    boolean isResolved();  // Always true
    Type getType();        // Safe to call
    List<ResolvedIRNode> children();
}

// Phase 3: Evaluable IR (with SQL generation)
public interface EvaluableIRNode extends ResolvedIRNode {
    SqlExpression toSql(SqlDialect dialect);
}
```

**Example**:
```java
// Unresolved: just captures structure
record UnresolvedFunction(
    String functionName,
    List<UnresolvedIRNode> arguments
) implements UnresolvedIRNode {
    @Override
    public Type getType() {
        throw new UnresolvedException("Cannot get type of unresolved function: " + functionName);
    }
}

// Resolved: knows type and signature
record ResolvedFunction(
    String functionName,
    FunctionSignature signature,
    List<ResolvedIRNode> arguments
) implements ResolvedIRNode {
    @Override
    public Type getType() {
        return signature.resultType();
    }
}

// Evaluable: can generate SQL
record EvaluableFunction(
    String functionName,
    FunctionSignature signature,
    List<EvaluableIRNode> arguments,
    SqlGenerator generator
) implements EvaluableIRNode {
    @Override
    public SqlExpression toSql(SqlDialect dialect) {
        return generator.generate(dialect, arguments);
    }
}
```

**Benefits**:
1. **Separation of Concerns**: Structure, types, and SQL generation separated
2. **Inspectable**: Can analyze unresolved tree before committing to types
3. **Better Errors**: Can report errors with full tree context
4. **Flexible**: Can apply transformations at each phase

### Recommendation 2: Rule-Based Analyzer

**Create a Catalyst-style analyzer with transformation rules**:

```java
public interface AnalysisRule {
    /**
     * Apply this rule to transform unresolved nodes.
     * Returns the transformed tree, or the original if no changes.
     */
    UnresolvedIRNode apply(UnresolvedIRNode node);
}

public class FHIRPathAnalyzer {
    private final List<AnalysisRule> rules;

    public FHIRPathAnalyzer(List<AnalysisRule> rules) {
        this.rules = rules;
    }

    public ResolvedIRNode analyze(UnresolvedIRNode unresolved) {
        UnresolvedIRNode current = unresolved;

        // Apply rules until fixed point
        boolean changed = true;
        while (changed) {
            changed = false;
            for (AnalysisRule rule : rules) {
                UnresolvedIRNode next = rule.apply(current);
                if (next != current) {
                    current = next;
                    changed = true;
                }
            }
        }

        // Verify fully resolved
        if (!current.isResolved()) {
            throw new AnalysisException("Could not fully resolve expression: " + current);
        }

        return (ResolvedIRNode) current;
    }
}

// Example rules:
class ResolveFunctionsRule implements AnalysisRule {
    private final FunctionRegistry registry;

    @Override
    public UnresolvedIRNode apply(UnresolvedIRNode node) {
        return node.transform(n -> {
            if (n instanceof UnresolvedFunction uf) {
                // Resolve children first
                List<UnresolvedIRNode> resolvedArgs = uf.arguments().stream()
                    .map(this::apply)
                    .toList();

                // Then resolve function
                FunctionSignature sig = registry.findSignature(
                    uf.functionName(),
                    resolvedArgs.stream().map(UnresolvedIRNode::getType).toList()
                );

                return new ResolvedFunction(uf.functionName(), sig, resolvedArgs);
            }
            return n;
        });
    }
}

class TypeCoercionRule implements AnalysisRule {
    @Override
    public UnresolvedIRNode apply(UnresolvedIRNode node) {
        return node.transform(n -> {
            if (n instanceof ResolvedFunction rf) {
                // Insert casts where needed
                List<UnresolvedIRNode> coercedArgs = IntStream.range(0, rf.arguments().size())
                    .mapToObj(i -> {
                        UnresolvedIRNode arg = rf.arguments().get(i);
                        Type expected = rf.signature().parameterTypes().get(i);
                        Type actual = arg.getType();

                        if (!actual.equals(expected) && TypeSystem.canCast(actual, expected)) {
                            return new Cast(arg, expected);
                        }
                        return arg;
                    })
                    .toList();

                return new ResolvedFunction(rf.functionName(), rf.signature(), coercedArgs);
            }
            return n;
        });
    }
}
```

**Benefits**:
1. **Composable**: Add/remove/reorder rules easily
2. **Testable**: Test each rule in isolation
3. **Debuggable**: See intermediate states between rules
4. **Maintainable**: Each rule has single responsibility

### Recommendation 3: Separate Type Coercion from Overload Resolution

**Current approach** (both in OverloadResolver):
```java
// OverloadResolver does BOTH overload resolution AND cast insertion
private static Adapt adapt(IRNode arg, Type target) {
    Type actual = arg.getType();
    if (actual.effectiveType() == target.effectiveType()) {
        return new Adapt(arg, true, 0);
    }
    if (TypeSystem.canCast(actual, target)) {
        IRNode implicts = new Cast(arg, target);  // Insert cast here
        return new Adapt(implicts, true, 1);
    }
    return new Adapt(arg, false, Integer.MAX_VALUE);
}
```

**Catalyst approach** (separated):
```java
// Step 1: Resolve function to best overload (WITHOUT casts)
class ResolveFunctionsRule implements AnalysisRule {
    public UnresolvedIRNode apply(UnresolvedIRNode node) {
        if (node instanceof UnresolvedFunction uf) {
            // Find signature based on EXACT type match (no coercion yet)
            FunctionSignature sig = registry.findExactSignature(
                uf.functionName(),
                uf.arguments().stream().map(UnresolvedIRNode::getType).toList()
            );

            if (sig != null) {
                return new ResolvedFunction(uf.functionName(), sig, uf.arguments());
            }

            // Leave unresolved if no exact match - TypeCoercion rule will insert casts
            return uf;
        }
        return node;
    }
}

// Step 2: Insert casts to make resolved functions valid (SEPARATE rule)
class TypeCoercionRule implements AnalysisRule {
    public UnresolvedIRNode apply(UnresolvedIRNode node) {
        if (node instanceof UnresolvedFunction uf) {
            // Try to find signature that works WITH coercion
            for (FunctionSignature sig : registry.getSignatures(uf.functionName())) {
                if (canCoerce(uf.arguments(), sig.parameterTypes())) {
                    List<UnresolvedIRNode> coercedArgs = insertCasts(
                        uf.arguments(),
                        sig.parameterTypes()
                    );
                    return new ResolvedFunction(uf.functionName(), sig, coercedArgs);
                }
            }
        }
        return node;
    }
}
```

**Benefits**:
1. **Clarity**: Overload resolution and coercion are separate concerns
2. **Reusable**: TypeCoercion can be applied to other contexts (not just function calls)
3. **Debuggable**: Can see state before and after coercion
4. **Flexible**: Can have different coercion strategies for different contexts

### Recommendation 4: Abstract SQL Generation (Dialect Support)

**Problem**: Current `eval()` returns Spark `Column` directly
```java
public interface IRNode {
    Column eval();  // Spark-specific!
}
```

**Solution**: Abstract SQL dialect interface (inspired by Catalyst's logical → physical separation)

```java
// Abstract SQL representation
public sealed interface SqlExpression {
    record FunctionCall(String name, List<SqlExpression> args) implements SqlExpression {}
    record BinaryOp(String op, SqlExpression left, SqlExpression right) implements SqlExpression {}
    record Cast(SqlExpression expr, SqlType type) implements SqlExpression {}
    record Literal(Object value, SqlType type) implements SqlExpression {}
    record Column(String name) implements SqlExpression {}
}

// Dialect interface
public interface SqlDialect {
    String renderExpression(SqlExpression expr);
    SqlType mapType(Type fhirType);
    boolean supportsCast(SqlType from, SqlType to);
    String functionName(String fhirFunction);
}

// Dialect implementations
public class SparkDialect implements SqlDialect {
    @Override
    public String renderExpression(SqlExpression expr) {
        return switch (expr) {
            case FunctionCall(String name, List<SqlExpression> args) ->
                name + "(" + args.stream()
                    .map(this::renderExpression)
                    .collect(Collectors.joining(", ")) + ")";
            case BinaryOp(String op, SqlExpression left, SqlExpression right) ->
                "(" + renderExpression(left) + " " + op + " " + renderExpression(right) + ")";
            // ... more cases
        };
    }

    @Override
    public String functionName(String fhirFunction) {
        return switch (fhirFunction) {
            case "substring" -> "substr";  // Spark uses 'substr'
            case "abs" -> "abs";
            // ... mappings
            default -> fhirFunction;
        };
    }
}

public class DuckDBDialect implements SqlDialect {
    @Override
    public String functionName(String fhirFunction) {
        return switch (fhirFunction) {
            case "substring" -> "substring";  // DuckDB uses 'substring'
            // ... different mappings
            default -> fhirFunction;
        };
    }
}

// IR nodes produce dialect-independent SQL
public interface EvaluableIRNode extends ResolvedIRNode {
    SqlExpression toSql();  // Dialect-independent
}

// Usage
EvaluableIRNode ir = analyzer.analyze(ast);
SqlExpression sql = ir.toSql();

// Generate Spark SQL
SparkDialect sparkDialect = new SparkDialect();
String sparkSql = sparkDialect.renderExpression(sql);
Column sparkColumn = functions.expr(sparkSql);

// Generate DuckDB SQL
DuckDBDialect duckDialect = new DuckDBDialect();
String duckSql = duckDialect.renderExpression(sql);
```

**Benefits**:
1. **Portability**: Same IR can target multiple SQL engines
2. **Testing**: Easier to test SQL generation without Spark runtime
3. **Flexibility**: Can optimize for different dialect capabilities
4. **Maintainability**: Dialect-specific logic centralized

### Recommendation 5: Expression Resolution State Tracking

**Add explicit resolution state** (like Catalyst's `resolved` flag):

```java
public sealed interface IRNode permits UnresolvedIRNode, ResolvedIRNode {
    boolean isResolved();
    List<IRNode> children();

    // Recursive check
    default boolean isFullyResolved() {
        return isResolved() && children().stream().allMatch(IRNode::isFullyResolved);
    }
}

public non-sealed interface UnresolvedIRNode extends IRNode {
    @Override
    default boolean isResolved() { return false; }

    // Type throws until resolved
    default Type getType() {
        throw new UnresolvedException(this.getClass().getSimpleName() + " is not resolved");
    }
}

public non-sealed interface ResolvedIRNode extends IRNode {
    @Override
    default boolean isResolved() { return true; }

    // Type is safe to access
    Type getType();
}
```

**Benefits**:
1. **Type Safety**: Compiler enforces resolution before accessing type
2. **Clear Contracts**: Interface makes resolution requirements explicit
3. **Better Errors**: Can detect and report unresolved nodes with context
4. **Debugging**: Can inspect resolution state during analysis

---

## Migration Strategy

### Phase 1: Add Resolution State (Minimal Disruption)

1. **Add marker interfaces** without changing existing code:
```java
public sealed interface IRNode permits UnresolvedIRNode, ResolvedIRNode {
    boolean isResolved();
    Type getType();
    Column eval();
}

// All existing IRNode implementations become ResolvedIRNode
public non-sealed interface ResolvedIRNode extends IRNode {
    @Override default boolean isResolved() { return true; }
}

// New unresolved nodes for future use
public non-sealed interface UnresolvedIRNode extends IRNode {
    @Override default boolean isResolved() { return false; }

    @Override
    default Type getType() {
        throw new UnresolvedException("Not yet resolved");
    }

    @Override
    default Column eval() {
        throw new UnresolvedException("Cannot evaluate unresolved node");
    }
}
```

2. **No changes to existing IRNode implementations** (Add, Cast, etc.) - they automatically implement ResolvedIRNode

### Phase 2: Abstract SQL Generation (Parallel Implementation)

1. **Add SqlExpression abstraction** alongside existing `Column eval()`:
```java
public interface ResolvedIRNode extends IRNode {
    Column eval();  // Keep existing method

    default SqlExpression toSql() {  // Add new method
        // Default: convert eval() result to SqlExpression
        throw new UnsupportedOperationException("toSql not yet implemented for " + getClass());
    }
}
```

2. **Gradually implement toSql()** for each IRNode, starting with simplest ones

3. **Create SparkDialect** that can consume SqlExpression:
```java
public class SparkDialect {
    public Column toSparkColumn(SqlExpression sql) {
        String sqlString = renderExpression(sql);
        return functions.expr(sqlString);
    }
}
```

### Phase 3: Introduce Unresolved Nodes (Incremental)

1. **Create unresolved versions** of key nodes:
```java
record UnresolvedFunctionCall(String name, List<IRNode> args) implements UnresolvedIRNode {
    @Override
    public List<IRNode> children() { return args; }
}
```

2. **Add optional analyzer path** that produces unresolved → resolved:
```java
public class TwoPhaseAnalyzer {
    public UnresolvedIRNode buildUnresolved(AstNode ast) {
        // Build unresolved tree (structure only)
    }

    public ResolvedIRNode resolve(UnresolvedIRNode unresolved) {
        // Apply analysis rules
    }

    // Convenience: one-shot analysis (existing behavior)
    public ResolvedIRNode analyze(AstNode ast) {
        return resolve(buildUnresolved(ast));
    }
}
```

3. **Keep existing Analyzer** as default, make TwoPhaseAnalyzer opt-in

### Phase 4: Rule-Based Analysis (Future)

1. Implement rule framework and migrate resolution logic to rules
2. Deprecate direct construction in Analyzer
3. Eventually make two-phase approach the default

---

## Concrete Examples

### Example 1: Function Resolution

**Current (Single Phase)**:
```java
// Analyzer.resolveFunctionCall() does everything at once:
// 1. Analyze arguments (recursive)
// 2. Find matching signature
// 3. Insert casts
// 4. Build resolved node

IRNode analyzeFunctionCall(AstFunctionCall call) {
    List<IRNode> args = call.children().map(this::analyze).toList();  // Resolved args
    IRNodeBuilder builder = FunctionRegistry.FUNCTIONS.get(call.functionName());

    // OverloadResolver finds signature AND inserts casts
    ResolvedCall resolved = OverloadResolver.resolveCall(builder.getSignatures(), args);

    // Returns fully resolved node
    return builder.build(resolved.args().toArray(new IRNode[0]));
}
```

**Catalyst-Inspired (Two Phase)**:
```java
// Phase 1: Build unresolved tree (structure only)
UnresolvedIRNode buildUnresolvedFunction(AstFunctionCall call) {
    List<UnresolvedIRNode> args = call.children()
        .map(this::buildUnresolved)
        .toList();

    return new UnresolvedFunction(call.functionName(), args);
}

// Phase 2: Resolution rules transform tree
class ResolveFunctionsRule implements AnalysisRule {
    public IRNode apply(IRNode node) {
        if (node instanceof UnresolvedFunction uf && uf.children().stream().allMatch(IRNode::isResolved)) {
            // All children resolved, now resolve this node
            FunctionSignature sig = registry.findSignature(
                uf.functionName(),
                uf.arguments().stream().map(IRNode::getType).toList()
            );

            if (sig != null) {
                return new ResolvedFunction(uf.functionName(), sig, uf.arguments());
            }
        }
        return node;  // Leave unchanged if can't resolve yet
    }
}

// Phase 3: Type coercion (separate rule)
class TypeCoercionRule implements AnalysisRule {
    public IRNode apply(IRNode node) {
        if (node instanceof ResolvedFunction rf) {
            // Insert casts where types don't match exactly
            List<IRNode> coercedArgs = IntStream.range(0, rf.arguments().size())
                .mapToObj(i -> {
                    IRNode arg = rf.arguments().get(i);
                    Type expected = rf.signature().parameterTypes().get(i);
                    if (!arg.getType().equals(expected)) {
                        return new Cast(arg, expected);
                    }
                    return arg;
                })
                .toList();

            return new ResolvedFunction(rf.functionName(), rf.signature(), coercedArgs);
        }
        return node;
    }
}
```

### Example 2: Better Error Messages

**Current**:
```java
// Error thrown during tree construction - limited context
throw new IllegalArgumentException(
    "No matching overload for binary operation with arg types: " +
    args.stream().map(IRNode::getType).toList()
);
```

**With Two-Phase Approach**:
```java
// Can provide much richer error context
class AnalysisException extends RuntimeException {
    private final UnresolvedIRNode failedNode;
    private final List<IRNode> contextPath;

    public String getMessage() {
        return String.format(
            "Cannot resolve function '%s' with argument types %s\n" +
            "Available signatures:\n%s\n" +
            "Expression tree:\n%s",
            functionName,
            argumentTypes,
            formatSignatures(availableSignatures),
            formatTree(contextPath)
        );
    }
}

// Example error:
// Cannot resolve function 'substring' with argument types [QUANTITY, INTEGER]
// Available signatures:
//   - substring(STRING, INTEGER) -> STRING
//   - substring(STRING, INTEGER, INTEGER) -> STRING
// Expression tree:
//   substring(...)
//     arg0: Add(...)  -> QUANTITY
//       left: Literal(5)  -> INTEGER
//       right: Literal(3) -> INTEGER
//     arg1: Literal(2)  -> INTEGER
```

### Example 3: Multi-Dialect Support

**Current (Spark-only)**:
```java
public record Add(IRNode left, IRNode right) implements IRNode {
    @Override
    public Column eval() {
        return switch ((PrimitiveType) getType()) {
            case STRING -> functions.concat(left.eval(), right.eval());
            case INTEGER, DECIMAL -> left.eval().plus(right.eval());
            case DATE_TIME -> dateTime(left.eval()).plus(quantity(right.eval()));
            // ...
        };
    }
}
```

**With Dialect Abstraction**:
```java
public record Add(IRNode left, IRNode right) implements EvaluableIRNode {
    @Override
    public SqlExpression toSql() {
        return switch ((PrimitiveType) getType()) {
            case STRING -> new SqlExpression.FunctionCall("concat",
                List.of(left.toSql(), right.toSql()));
            case INTEGER, DECIMAL -> new SqlExpression.BinaryOp("+",
                left.toSql(), right.toSql());
            case DATE_TIME -> new SqlExpression.FunctionCall("date_add",
                List.of(left.toSql(), right.toSql()));
            // ...
        };
    }

    // Spark-specific evaluation (convenience method)
    @Override
    public Column eval() {
        SparkDialect dialect = new SparkDialect();
        return dialect.toColumn(toSql());
    }
}

// Now also works with other dialects:
Add addNode = ...;
SqlExpression sql = addNode.toSql();

// DuckDB
DuckDBDialect duckDialect = new DuckDBDialect();
String duckSql = duckDialect.render(sql);  // "left + right" or "concat(left, right)"

// PostgreSQL
PostgresDialect pgDialect = new PostgresDialect();
String pgSql = pgDialect.render(sql);

// Presto/Trino
PrestoDialect prestoDialect = new PrestoDialect();
String prestoSql = prestoDialect.render(sql);
```

---

## Answers to Specific Questions

### Q1: Could we use a two-phase approach like Catalyst (unresolved → resolved)?

**Answer: YES - Highly Recommended**

**How Catalyst Does It**:
- AST → UnresolvedExpression (structure only)
- UnresolvedExpression → ResolvedExpression (via Analyzer rules)
- ResolvedExpression → Physical plan (via query planning)

**How FHIRPath Could Do It**:
- AST → UnresolvedIRNode (structure only)
- UnresolvedIRNode → ResolvedIRNode (via analysis rules)
- ResolvedIRNode → SqlExpression (via dialect-specific generation)

**Benefits**:
1. Better error messages (full tree context available)
2. Inspectable intermediate state
3. Easier to add optimization passes
4. Clearer separation of concerns

**Trade-offs**:
- More complex architecture
- Slightly more code
- Need to manage resolution state

**Recommendation**: Start with marker interfaces and resolution state tracking (Phase 1 above), then gradually introduce unresolved nodes for new functionality.

---

### Q2: Could we apply transformations to the IR tree (like Catalyst rules) instead of building resolved trees directly?

**Answer: YES - Would Enable Powerful Optimizations**

**Catalyst's Approach**:
```scala
// Rules transform trees
abstract class Rule[TreeType <: TreeNode[_]] {
  def apply(tree: TreeType): TreeType
}

// Optimizer composes many rules
class Optimizer extends RuleExecutor[LogicalPlan] {
  val batches = Seq(
    Batch("ConstantFolding", FixedPoint(100),
      NullPropagation,
      ConstantFolding,
      SimplifyConditionals,
      // ...
    ),
    // ... more batches
  )
}
```

**FHIRPath Could Have**:
```java
interface IRTransformRule {
    IRNode apply(IRNode node);
}

// Example: Constant folding
class ConstantFoldingRule implements IRTransformRule {
    public IRNode apply(IRNode node) {
        return node.transform(n -> {
            if (n instanceof Add add &&
                add.left() instanceof Literal lit1 &&
                add.right() instanceof Literal lit2) {
                // Fold: Literal(3) + Literal(5) → Literal(8)
                return new Literal(
                    ((Integer) lit1.value()) + ((Integer) lit2.value()),
                    Type.INTEGER
                );
            }
            return n;
        });
    }
}

// Example: Push down casts
class PushDownCastsRule implements IRTransformRule {
    public IRNode apply(IRNode node) {
        // Cast(Add(a, b), T) → Add(Cast(a, T), Cast(b, T))
        if (node instanceof Cast cast && cast.child() instanceof Add add) {
            return new Add(
                new Cast(add.left(), cast.targetType()),
                new Cast(add.right(), cast.targetType())
            );
        }
        return node;
    }
}
```

**Potential Optimizations**:
1. **Constant Folding**: `5 + 3` → `8`
2. **Null Propagation**: `null + x` → `null`
3. **Cast Elimination**: `Cast(Cast(x, T1), T2)` → `Cast(x, T2)`
4. **Predicate Pushdown**: Move filters earlier in traversals
5. **Redundant Operation Elimination**: `x - 0` → `x`, `x * 1` → `x`

**Recommendation**: Implement a simple rule framework (Phase 4 above) and start with high-value optimizations like constant folding.

---

### Q3: How does Catalyst handle the equivalent of our function signature matching?

**Answer: Multi-Stage Process with Catalog Lookup**

**Catalyst's Approach**:

1. **Function Registry** (catalog-based):
```scala
// FunctionRegistry maps names to Expression builders
val functionRegistry: Map[String, ExpressionInfo]

// ResolveFunctions rule looks up functions
case class UnresolvedFunction(name: Seq[String], args: Seq[Expression])
  ↓ (ResolveFunctions rule)
case class Sum(child: Expression) extends AggregateExpression
```

2. **Type Checking** (separate from resolution):
```scala
abstract class Expression {
  // Each expression defines expected input types
  def checkInputDataTypes(): TypeCheckResult

  // Example:
  override def checkInputDataTypes(): TypeCheckResult = {
    if (children.head.dataType != IntegerType) {
      TypeCheckFailure("Expected integer, got " + children.head.dataType)
    } else {
      TypeCheckSuccess
    }
  }
}
```

3. **Type Coercion** (separate rules):
```scala
// ImplicitTypeCasts rule inserts casts to make types compatible
object ImplicitTypeCasts extends TypeCoercionRule {
  def apply(plan: LogicalPlan): LogicalPlan = plan.transformAllExpressions {
    case e if !e.childrenResolved => e
    case e => e.checkInputDataTypes() match {
      case TypeCheckFailure(_) =>
        // Try inserting casts to fix type mismatch
        insertCasts(e)
      case _ => e
    }
  }
}
```

**Comparison to FHIRPath**:

| Aspect | FHIRPath (Current) | Catalyst |
|--------|-------------------|----------|
| **Signature Storage** | Static SIGNATURES field per class | Central FunctionRegistry |
| **Overload Resolution** | OverloadResolver.resolveCall() | ResolveFunctions rule |
| **Type Checking** | Implicit in signature match | Explicit checkInputDataTypes() |
| **Cast Insertion** | Coupled with resolution | Separate TypeCoercion rules |
| **Timing** | During IR construction | During analysis phase |

**FHIRPath Could Adopt**:
```java
// Central registry (like Catalyst's FunctionRegistry)
class FunctionCatalog {
    private final Map<String, FunctionDefinition> functions;

    record FunctionDefinition(
        String name,
        List<FunctionSignature> signatures,
        BiFunction<FunctionSignature, List<IRNode>, IRNode> builder
    ) {}

    public FunctionDefinition lookup(String name) {
        return functions.get(name);
    }
}

// Resolution rule (like Catalyst's ResolveFunctions)
class ResolveFunctionsRule implements AnalysisRule {
    private final FunctionCatalog catalog;

    public IRNode apply(IRNode node) {
        if (node instanceof UnresolvedFunction uf) {
            FunctionDefinition def = catalog.lookup(uf.functionName());

            // Find matching signature (exact types only)
            FunctionSignature sig = def.signatures().stream()
                .filter(s -> matchesExactly(s, uf.arguments()))
                .findFirst()
                .orElse(null);

            if (sig != null) {
                return def.builder().apply(sig, uf.arguments());
            }
        }
        return node;
    }
}

// Type coercion rule (like Catalyst's ImplicitTypeCasts)
class ImplicitCastsRule implements AnalysisRule {
    private final FunctionCatalog catalog;

    public IRNode apply(IRNode node) {
        if (node instanceof UnresolvedFunction uf) {
            FunctionDefinition def = catalog.lookup(uf.functionName());

            // Find signature that works with coercion
            for (FunctionSignature sig : def.signatures()) {
                if (canCoerceArgs(uf.arguments(), sig.parameterTypes())) {
                    List<IRNode> coerced = insertCasts(uf.arguments(), sig.parameterTypes());
                    return def.builder().apply(sig, coerced);
                }
            }
        }
        return node;
    }
}
```

**Benefits**:
1. **Centralized**: All function metadata in one place
2. **Flexible**: Can modify resolution strategy without changing IR nodes
3. **Testable**: Can test resolution rules independently
4. **Extensible**: Easy to add custom functions

**Recommendation**: Move function signatures from static fields to a central registry (similar to current FunctionRegistry, but with more structure).

---

### Q4: Could we separate type information from evaluation logic using Catalyst-like patterns?

**Answer: YES - Via Interface Segregation**

**Catalyst's Separation**:
```scala
abstract class Expression extends TreeNode[Expression] {
  // Type information (logical)
  def dataType: DataType
  def nullable: Boolean

  // Evaluation (physical)
  def eval(input: InternalRow): Any
  def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode
}

// Further separation: LogicalPlan vs SparkPlan
trait LogicalPlan  // Type information, no execution
trait SparkPlan    // Execution strategy
```

**FHIRPath Could Separate**:
```java
// Level 1: Structure only (tree topology)
public interface IRNode {
    List<IRNode> children();
    boolean isResolved();
}

// Level 2: Add type information (resolved logical plan)
public interface TypedIRNode extends IRNode {
    Type getType();
    boolean isSingular();
}

// Level 3: Add SQL generation (evaluable plan)
public interface EvaluableIRNode extends TypedIRNode {
    SqlExpression toSql();
}

// Level 4: Add Spark-specific optimization (physical plan)
public interface SparkEvaluableNode extends EvaluableIRNode {
    Column toSparkColumn();  // Optimized Spark Column generation
}

// Example implementation:
record Add(IRNode left, IRNode right)
    implements SparkEvaluableNode {

    // IRNode
    @Override
    public List<IRNode> children() {
        return List.of(left, right);
    }

    @Override
    public boolean isResolved() {
        return left.isResolved() && right.isResolved();
    }

    // TypedIRNode
    @Override
    public Type getType() {
        return left.getType();
    }

    // EvaluableIRNode
    @Override
    public SqlExpression toSql() {
        return new SqlExpression.BinaryOp("+", left.toSql(), right.toSql());
    }

    // SparkEvaluableNode
    @Override
    public Column toSparkColumn() {
        // Can use Spark-specific optimizations
        return left.toSparkColumn().plus(right.toSparkColumn());
    }
}
```

**Benefits**:
1. **Flexibility**: Can work with typed IR without requiring SQL generation
2. **Testability**: Can test type resolution independently
3. **Portability**: toSql() can target multiple engines
4. **Optimization**: Spark-specific code isolated in SparkEvaluableNode

**Recommendation**: Start with three interfaces (IRNode, TypedIRNode, EvaluableIRNode) and add SparkEvaluableNode if needed for performance.

---

### Q5: How would Catalyst-style approach help us target multiple SQL dialects?

**Answer: Logical vs Physical Separation is Key**

**Catalyst's Strategy**:
```scala
// Logical Plan: Dialect-independent
case class Project(projectList: Seq[NamedExpression], child: LogicalPlan)
case class Filter(condition: Expression, child: LogicalPlan)

  ↓ (QueryPlanner)

// Physical Plan: Spark-specific execution
case class ProjectExec(projectList: Seq[NamedExpression], child: SparkPlan)
case class FilterExec(condition: Expression, child: SparkPlan)

// Code generation: Even more specific
def doConsume(ctx: CodegenContext, input: Seq[ExprCode]): String = {
  // Generate Java bytecode for this specific expression
}
```

**FHIRPath Adoption Strategy**:

**Step 1: Dialect-Independent IR**
```java
public interface EvaluableIRNode extends TypedIRNode {
    SqlExpression toSql();  // Dialect-independent representation
}

// SqlExpression is pure data (no execution)
sealed interface SqlExpression {
    record FunctionCall(String name, List<SqlExpression> args) {}
    record BinaryOp(String op, SqlExpression left, SqlExpression right) {}
    record Cast(SqlExpression expr, SqlType type) {}
    // ...
}
```

**Step 2: Dialect-Specific Rendering**
```java
public interface SqlDialect {
    // Render expression to SQL string
    String renderExpression(SqlExpression expr);

    // Map types
    SqlType mapType(Type fhirType);

    // Function name mapping
    String functionName(String standardName);

    // Capability queries
    boolean supportsFunction(String name);
    boolean supportsCast(SqlType from, SqlType to);
}

// Spark implementation
public class SparkSqlDialect implements SqlDialect {
    @Override
    public String functionName(String name) {
        return switch (name) {
            case "substring" -> "substr";  // Spark uses substr
            case "concat" -> "concat";
            case "abs" -> "abs";
            // ...
            default -> name;
        };
    }

    @Override
    public String renderExpression(SqlExpression expr) {
        return switch (expr) {
            case FunctionCall(String name, List<SqlExpression> args) ->
                functionName(name) + "(" +
                args.stream().map(this::renderExpression).collect(Collectors.joining(", ")) +
                ")";
            case BinaryOp(String op, SqlExpression left, SqlExpression right) ->
                "(" + renderExpression(left) + " " + op + " " + renderExpression(right) + ")";
            case Cast(SqlExpression e, SqlType t) ->
                "CAST(" + renderExpression(e) + " AS " + mapType(t) + ")";
            // ...
        };
    }
}

// DuckDB implementation
public class DuckDBDialect implements SqlDialect {
    @Override
    public String functionName(String name) {
        return switch (name) {
            case "substring" -> "substring";  // DuckDB uses substring (not substr)
            case "date_add" -> "date_add";
            // ... DuckDB-specific mappings
            default -> name;
        };
    }

    @Override
    public SqlType mapType(Type fhirType) {
        return switch (fhirType) {
            case PrimitiveType.QUANTITY -> new SqlType.Struct(
                List.of(
                    new SqlType.Field("value", SqlType.DECIMAL),
                    new SqlType.Field("unit", SqlType.STRING)
                )
            );
            // ... DuckDB-specific type mappings
            default -> standardTypeMapping(fhirType);
        };
    }
}

// PostgreSQL implementation
public class PostgreSqlDialect implements SqlDialect {
    @Override
    public String renderExpression(SqlExpression expr) {
        // Postgres has slightly different syntax in some cases
        if (expr instanceof Cast(SqlExpression e, SqlType t)) {
            // Postgres can use :: operator
            return renderExpression(e) + "::" + mapType(t);
        }
        return super.renderExpression(expr);  // Delegate to base
    }
}
```

**Step 3: Usage Example**
```java
// Parse FHIRPath
AstNode ast = parser.parse("name.given.substring(0, 5)");

// Analyze to IR
EvaluableIRNode ir = analyzer.analyze(ast);

// Generate dialect-independent SQL
SqlExpression sql = ir.toSql();

// Render for Spark
SparkSqlDialect sparkDialect = new SparkSqlDialect();
String sparkSql = sparkDialect.renderExpression(sql);
// Result: "substr(name.given, 0, 5)"

// Render for DuckDB
DuckDBDialect duckDialect = new DuckDBDialect();
String duckSql = duckDialect.renderExpression(sql);
// Result: "substring(name.given, 0, 5)"

// Render for Postgres
PostgreSqlDialect pgDialect = new PostgreSqlDialect();
String pgSql = pgDialect.renderExpression(sql);
// Result: "substring(name.given, 0, 5)"
```

**Dialect-Specific Optimizations**:
```java
public interface SqlDialect {
    // Override to provide dialect-specific optimizations
    default SqlExpression optimize(SqlExpression expr) {
        return expr;  // Default: no optimization
    }
}

public class SparkSqlDialect implements SqlDialect {
    @Override
    public SqlExpression optimize(SqlExpression expr) {
        // Spark-specific: Use explode_outer instead of unnest
        if (expr instanceof FunctionCall("unnest", List<SqlExpression> args)) {
            return new FunctionCall("explode_outer", args);
        }
        return expr;
    }
}

public class DuckDBDialect implements SqlDialect {
    @Override
    public SqlExpression optimize(SqlExpression expr) {
        // DuckDB has native list operations
        if (expr instanceof FunctionCall("array_contains", List<SqlExpression> args)) {
            // Use list operator syntax
            return new BinaryOp("IN", args.get(1), args.get(0));
        }
        return expr;
    }
}
```

**Benefits**:
1. **Portability**: Same IR works across SQL engines (Spark, DuckDB, Postgres, Presto, etc.)
2. **Testability**: Can test SQL generation without requiring full engine runtime
3. **Flexibility**: Can optimize for dialect capabilities
4. **Maintainability**: Dialect-specific code isolated in SqlDialect implementations
5. **Future-Proof**: Easy to add new dialects

**Real-World Example**:
```java
// QUANTITY addition differs across dialects

// FHIRPath: 5 'mg' + 3 'mg'
AstNode ast = parser.parse("5 'mg' + 3 'mg'");
EvaluableIRNode ir = analyzer.analyze(ast);
SqlExpression sql = ir.toSql();

// Spark (using struct for QUANTITY)
SparkSqlDialect sparkDialect = new SparkSqlDialect();
String sparkSql = sparkDialect.renderExpression(sql);
// "struct(struct_field1 + struct_field2, 'mg')"

// DuckDB (using struct with different syntax)
DuckDBDialect duckDialect = new DuckDBDialect();
String duckSql = duckDialect.renderExpression(sql);
// "{'value': value1 + value2, 'unit': 'mg'}"

// Postgres (using composite type)
PostgreSqlDialect pgDialect = new PostgreSqlDialect();
String pgSql = pgDialect.renderExpression(sql);
// "ROW(value1 + value2, 'mg')::quantity"
```

**Recommendation**: Implement SqlExpression abstraction (Phase 2 above) and create SparkDialect first. Once proven, add additional dialects as needed.

---

## Summary of Recommendations

### Priority 1 (High Impact, Low Risk)
1. **Add Resolution State Tracking**: Add `isResolved()` to IRNode interface
2. **Abstract SQL Generation**: Create SqlExpression and SqlDialect interfaces
3. **Centralize Function Registry**: Move signatures to FunctionCatalog

### Priority 2 (Medium Impact, Medium Effort)
4. **Separate Type Coercion**: Extract from OverloadResolver into separate concern
5. **Two-Phase Analysis**: Introduce unresolved nodes for new functionality
6. **Better Error Messages**: Use tree context in exceptions

### Priority 3 (Long-Term, High Value)
7. **Rule-Based Analyzer**: Implement transformation rule framework
8. **Optimization Passes**: Add constant folding, null propagation, etc.
9. **Multi-Dialect Support**: Full implementation for DuckDB, Postgres, etc.

---

## References

### Spark Catalyst Source Code (v3.5.6)
- **Expression.scala**: `/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/expressions/Expression.scala`
- **unresolved.scala**: `/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/unresolved.scala`
- **Analyzer.scala**: `/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala`
- **TypeCoercion.scala**: `/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/TypeCoercion.scala`
- **Rule.scala**: `/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/rules/Rule.scala`

### Spark Documentation
- Catalyst Optimizer Deep Dive: https://databricks.com/blog/2015/04/13/deep-dive-into-spark-sqls-catalyst-optimizer.html
- Expression API: https://spark.apache.org/docs/3.5.6/api/scala/org/apache/spark/sql/catalyst/expressions/Expression.html

### Design Patterns
- Visitor Pattern: For tree traversal
- Strategy Pattern: For dialect-specific rendering
- Template Method: For analysis rule framework
- Chain of Responsibility: For rule execution

---

## Conclusion

Catalyst's architecture offers valuable lessons for the FHIRPath IR system:

1. **Two-Phase Analysis** (unresolved → resolved) enables better error messages and more flexible analysis
2. **Rule-Based Transformations** provide composable, testable optimization passes
3. **Separation of Concerns** (structure, types, evaluation) improves maintainability
4. **Dialect Abstraction** enables targeting multiple SQL engines from same IR

The recommended migration strategy allows **incremental adoption** without disrupting existing functionality, starting with low-risk improvements (resolution tracking, SQL abstraction) and progressing to more sophisticated patterns (rules, optimizations) as benefits are proven.

The FHIRPath project is already well-architected with clear separation between AST, IR, and type system. Adopting Catalyst-inspired patterns would enhance this foundation and position the system for future requirements like multi-dialect support and advanced optimizations.