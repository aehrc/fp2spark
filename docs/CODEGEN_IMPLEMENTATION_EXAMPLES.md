# SparkSQL Code Generator - Implementation Examples

This document provides detailed code examples illustrating the design concepts from `CODEGEN_DESIGN_SUMMARY.md`.

---

## Core Infrastructure

### CodeGenContext

```java
public class CodeGenContext {
    private final SparkSession spark;
    private final Map<String, Column> variables;  // For future variable binding

    public CodeGenContext(SparkSession spark) {
        this.spark = spark;
        this.variables = new HashMap<>();
    }

    public SparkSession getSparkSession() {
        return spark;
    }

    // For lambda evaluation
    public SparkCodeGenerator withThisColumn(Column thisColumn) {
        return new SparkCodeGenerator(thisColumn);
    }

    // For future: UDF registration, custom configuration, etc.
}
```

### CodeGenerationException

```java
public class CodeGenerationException extends RuntimeException {
    private final String operationName;
    private final Type dispatchType;
    private final List<Type> argumentTypes;

    public CodeGenerationException(
            String message,
            String operationName,
            Type dispatchType,
            List<Type> argumentTypes) {
        super(formatMessage(message, operationName, dispatchType, argumentTypes));
        this.operationName = operationName;
        this.dispatchType = dispatchType;
        this.argumentTypes = argumentTypes;
    }

    public CodeGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.operationName = null;
        this.dispatchType = null;
        this.argumentTypes = null;
    }

    private static String formatMessage(
            String message,
            String operationName,
            Type dispatchType,
            List<Type> argumentTypes) {
        return String.format("%s [operation=%s, dispatchType=%s, argTypes=%s]",
                message, operationName, dispatchType, argumentTypes);
    }

    // Getters for error context
    public String getOperationName() { return operationName; }
    public Type getDispatchType() { return dispatchType; }
    public List<Type> getArgumentTypes() { return argumentTypes; }
}
```

---

## Domain Wrappers

### Collection Wrapper

```java
public record Collection(@Nonnull Column column, boolean isSingular) {

    // Factory methods
    public static Collection of(Column col, boolean isSingular) {
        return new Collection(col, isSingular);
    }

    public static Collection singular(Column col) {
        return new Collection(col, true);
    }

    public static Collection nonSingular(Column col) {
        return new Collection(col, false);
    }

    // Core utility - dispatch based on cardinality
    @Nonnull
    public Column apply(
            Function<Column, Column> arrayFunction,
            Function<Column, Column> singleFunction) {
        return isSingular ? singleFunction.apply(column) : arrayFunction.apply(column);
    }

    // Null-safe apply
    @Nonnull
    public Column applyNonNull(
            Function<Column, Column> arrayFunction,
            Function<Column, Column> singleFunction,
            Column defaultValue) {
        return when(column.isNotNull(), apply(arrayFunction, singleFunction))
                .otherwise(defaultValue);
    }

    // Convert to array
    @Nonnull
    public Column asArray() {
        return applyNonNull(
                Function.identity(),      // Already array
                functions::array,         // Wrap singular in array
                functions.array()         // Empty array for NULL
        );
    }

    // Collection operations
    @Nonnull
    public Column count() {
        return applyNonNull(
                functions::size,          // Array: size(array)
                col -> lit(1),            // Singular: 1
                lit(0)                    // NULL: 0 (not NULL!)
        );
    }

    @Nonnull
    public Column first() {
        return apply(
                col -> col.getItem(0),    // Array: first element
                Function.identity()       // Singular: the value
        );
    }

    @Nonnull
    public Column last() {
        return apply(
                col -> functions.element_at(col, -1),  // Array: last element
                Function.identity()                     // Singular: the value
        );
    }

    @Nonnull
    public Column isEmpty() {
        return applyNonNull(
                col -> functions.size(col).equalTo(0),  // Array: size == 0
                col -> lit(false),                      // Singular: never empty
                lit(true)                               // NULL: empty
        );
    }

    // Transformations
    @Nonnull
    public Collection filter(Function<Column, Column> predicate) {
        Column filtered = apply(
                col -> functions.filter(col, predicate::apply),
                col -> when(predicate.apply(col), col).otherwise(lit(null))
        );
        return Collection.nonSingular(filtered);  // Filter always returns array
    }

    @Nonnull
    public Collection map(Function<Column, Column> mapper) {
        Column mapped = apply(
                col -> functions.transform(col, mapper::apply),
                mapper
        );
        return new Collection(mapped, isSingular);
    }

    @Nonnull
    public Column all(Function<Column, Column> predicate) {
        return apply(
                col -> functions.forall(col, predicate::apply),
                predicate
        );
    }

    @Nonnull
    public Column any(Function<Column, Column> predicate) {
        return apply(
                col -> functions.exists(col, predicate::apply),
                predicate
        );
    }

    // Unboxing
    @Nonnull
    public Column toColumn() {
        return column;
    }
}
```

### Quantity Wrapper

```java
public record Quantity(@Nonnull Column column) {

    public static Quantity of(Column col) {
        return new Quantity(col);
    }

    // Field accessors
    public Column value() {
        return column.getField("value");
    }

    public Column unit() {
        return column.getField("unit");
    }

    public Column system() {
        return column.getField("system");
    }

    public Column code() {
        return column.getField("code");
    }

    // Struct builder
    public static Column build(Column value, Column unit, Column system, Column code) {
        return functions.struct(
                value.as("value"),
                unit.as("unit"),
                system.as("system"),
                code.as("code")
        );
    }

    // Transform value while preserving other fields
    public Quantity mapValue(Function<Column, Column> fn) {
        return new Quantity(
                build(fn.apply(value()), unit(), system(), code())
        );
    }

    // Unboxing
    public Column toColumn() {
        return column;
    }
}
```

### LambdaExpression Wrapper

```java
public class LambdaExpression {
    private final Lambda lambdaNode;
    private final CodeGenContext context;

    public LambdaExpression(Lambda lambdaNode, CodeGenContext context) {
        this.lambdaNode = lambdaNode;
        this.context = context;
    }

    /**
     * Evaluate lambda with $this bound to element.
     */
    public Column apply(Column thisElement) {
        SparkCodeGenerator lambdaGen = context.withThisColumn(thisElement);
        return lambdaNode.body().accept(lambdaGen);
    }
}
```

---

## Handler Examples

### Collection Operation Handler (Generic)

```java
public class CollectionOperationHandler extends AnnotatedOperationHandler {

    private static final Logger log = LoggerFactory.getLogger(CollectionOperationHandler.class);

    public CollectionOperationHandler(
            String operation,
            Type dispatchType,
            CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    @Operation("count")
    public Column count(Collection input) {
        if (log.isTraceEnabled()) {
            log.trace("count() on collection (singular={})", input.isSingular());
        }
        return input.count();
    }

    @Operation("first")
    public Column first(Collection input) {
        return input.first();
    }

    @Operation("last")
    public Column last(Collection input) {
        return input.last();
    }

    @Operation("isEmpty")
    public Column isEmpty(Collection input) {
        return input.isEmpty();
    }

    @Operation("exists")
    public Column exists(Collection input) {
        return input.isEmpty().not();
    }

    @Operation("where")
    public Collection where(Collection input, LambdaExpression predicate) {
        if (log.isTraceEnabled()) {
            log.trace("where() filtering collection");
        }
        return input.filter(predicate::apply);
    }

    @Operation("select")
    public Collection select(Collection input, LambdaExpression projection) {
        return input.map(projection::apply);
    }

    @Operation("all")
    public Column all(Collection input, LambdaExpression predicate) {
        return input.all(predicate::apply);
    }

    @Operation("any")
    public Column any(Collection input, LambdaExpression predicate) {
        return input.any(predicate::apply);
    }
}
```

### Comparison Operation Handler (Operation-Based)

```java
public class ComparisonOperationHandler extends AnnotatedOperationHandler {

    private static final Logger log = LoggerFactory.getLogger(ComparisonOperationHandler.class);

    public ComparisonOperationHandler(
            String operation,
            Type dispatchType,
            CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    @Operation("eq")
    public Column equals(Collection left, Collection right) {
        if (log.isTraceEnabled()) {
            log.trace("Comparing {} == {} for type {}",
                    left.isSingular() ? "singular" : "array",
                    right.isSingular() ? "singular" : "array",
                    dispatchType);
        }

        // Collections handle singular vs array automatically
        Column result = left.toColumn().equalTo(right.toColumn());

        if (log.isTraceEnabled()) {
            log.trace("Comparison SQL: {}", result.expr().sql());
        }

        return result;
    }

    @Operation("ne")
    public Column notEquals(Collection left, Collection right) {
        return left.toColumn().notEqual(right.toColumn());
    }

    @Operation("gt")
    public Column greaterThan(Collection left, Collection right) {
        return left.toColumn().gt(right.toColumn());
    }

    @Operation("gte")
    public Column greaterThanOrEqual(Collection left, Collection right) {
        return left.toColumn().geq(right.toColumn());
    }

    @Operation("lt")
    public Column lessThan(Collection left, Collection right) {
        return left.toColumn().lt(right.toColumn());
    }

    @Operation("lte")
    public Column lessThanOrEqual(Collection left, Collection right) {
        return left.toColumn().leq(right.toColumn());
    }
}
```

### Shared Operations Pattern

```java
public final class CommonOperations {

    public static Stream<NamedMapping> arithmeticOps() {
        return Stream.of(
                binary("add", (a, b) -> a.plus(b)),
                binary("sub", (a, b) -> a.minus(b)),
                binary("multiply", (a, b) -> a.multiply(b)),
                binary("divide", (a, b) -> a.divide(b)),
                binary("mod", (a, b) -> a.mod(b))
        );
    }

    public static Stream<NamedMapping> comparisonOps() {
        return Stream.of(
                binary("eq", (a, b) -> a.equalTo(b)),
                binary("ne", (a, b) -> a.notEqual(b)),
                binary("gt", (a, b) -> a.gt(b)),
                binary("gte", (a, b) -> a.geq(b)),
                binary("lt", (a, b) -> a.lt(b)),
                binary("lte", (a, b) -> a.leq(b))
        );
    }

    public static Stream<NamedMapping> mathOps() {
        return Stream.of(
                unary("abs", functions::abs),
                unary("ceiling", functions::ceil),
                unary("floor", functions::floor),
                unary("round", functions::round)
        );
    }

    public static Stream<NamedMapping> allNumericOps() {
        return Stream.of(
                arithmeticOps(),
                comparisonOps(),
                mathOps()
        ).flatMap(Function.identity());
    }

    // Helper factories
    private static NamedMapping unary(
            String name,
            Function<Column, Column> fn) {
        return new NamedMapping(name, new DirectMapping(fn));
    }

    private static NamedMapping binary(
            String name,
            BiFunction<Column, Column, Column> fn) {
        return new NamedMapping(name, new BinaryMapping(fn));
    }
}

// Handler using shared operations
public class IntegerHandler extends AnnotatedOperationHandler {

    @OperationMappings
    public Stream<NamedMapping> operations() {
        return CommonOperations.allNumericOps();  // Reuse all numeric ops
    }

    // Type-specific operations only
    @Operation("toDecimal")
    public Column toDecimal(Collection input) {
        return input.toColumn().cast(DataTypes.createDecimalType());
    }
}
```

### Quantity Handler (Type-Based)

```java
public class QuantityHandler extends AnnotatedOperationHandler {

    public QuantityHandler(
            String operation,
            Type dispatchType,
            CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    @Operation("add")
    public Quantity add(Quantity left, Quantity right) {
        // Add values, preserve left's unit
        Column resultValue = left.value().plus(right.value());
        return new Quantity(
                Quantity.build(resultValue, left.unit(), left.system(), left.code())
        );
    }

    @Operation("multiply")
    public Quantity multiply(Quantity quantity, Column scalar) {
        // Multiply value by scalar, preserve unit
        Column resultValue = quantity.value().multiply(scalar);
        return new Quantity(
                Quantity.build(resultValue, quantity.unit(), quantity.system(), quantity.code())
        );
    }

    @Operation("abs")
    public Quantity abs(Quantity quantity) {
        return quantity.mapValue(functions::abs);
    }

    @Operation("getValue")
    public Column getValue(Quantity quantity) {
        return quantity.value();
    }

    @Operation("getUnit")
    public Column getUnit(Quantity quantity) {
        return quantity.unit();
    }

    @OperationMappings
    public Stream<NamedMapping> mathOps() {
        return Stream.of(
                unary("ceiling", q -> Quantity.of(q).mapValue(functions::ceil).toColumn()),
                unary("floor", q -> Quantity.of(q).mapValue(functions::floor).toColumn())
        );
    }

    private static NamedMapping unary(String name, Function<Column, Column> fn) {
        return new NamedMapping(name, new DirectMapping(fn));
    }
}
```

---

## InvocationBinder

```java
public class InvocationBinder {

    public Column invoke(
            OperationHandler handler,
            Method method,
            List<Column> args,
            List<IRNode> argNodes) {

        // Box arguments
        Object[] boxedArgs = boxArguments(method, args, argNodes);

        // Invoke handler method
        Object result = invokeMethod(method, handler, boxedArgs);

        // Unbox result
        return unboxResult(result);
    }

    private Object[] boxArguments(
            Method method,
            List<Column> args,
            List<IRNode> argNodes) {

        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] boxedArgs = new Object[paramTypes.length];

        for (int i = 0; i < args.size(); i++) {
            boxedArgs[i] = boxArgument(args.get(i), argNodes.get(i), paramTypes[i]);
        }

        return boxedArgs;
    }

    private Object boxArgument(Column col, IRNode argNode, Class<?> targetType) {
        // Column - pass through
        if (targetType == Column.class) {
            return col;
        }

        // Collection - box with cardinality
        if (targetType == Collection.class) {
            boolean isSingular = argNode.getType().getCardinality().isSingular();
            return Collection.of(col, isSingular);
        }

        // Quantity - box
        if (targetType == Quantity.class) {
            return Quantity.of(col);
        }

        // LambdaExpression - box from IR Lambda node
        if (targetType == LambdaExpression.class && argNode instanceof Lambda lambda) {
            CodeGenContext context = getContextFromHandler();
            return new LambdaExpression(lambda, context);
        }

        // Unknown - error
        throw new CodeGenerationException(
                "Unknown parameter type for boxing: " + targetType.getName()
        );
    }

    private Column unboxResult(Object result) {
        if (result instanceof Column col) {
            return col;
        }

        if (result instanceof Collection coll) {
            return coll.toColumn();
        }

        if (result instanceof Quantity qty) {
            return qty.toColumn();
        }

        throw new CodeGenerationException(
                "Handler returned unexpected type: " + result.getClass().getName()
        );
    }

    private Object invokeMethod(Method method, Object target, Object[] args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new CodeGenerationException(
                    "Failed to invoke handler method: " + method.getName(),
                    e
            );
        }
    }
}
```

---

## Handler Registry

```java
public class HandlerRegistry {

    private final Map<String, OperationHandlerFactory> operationHandlers = new HashMap<>();
    private final Map<Type, TypeHandlerFactory> typeHandlers = new HashMap<>();
    private final List<GenericHandlerFactory> genericHandlers = new ArrayList<>();

    public static HandlerRegistry standard() {
        HandlerRegistry registry = new HandlerRegistry();

        // Operation-based handlers
        registry.registerOperationHandlerFactory("comparison",
                ComparisonOperationHandler::new);
        registry.registerOperationHandlerFactory("arithmetic",
                ArithmeticOperationHandler::new);

        // Type-based handlers
        registry.registerTypeHandlerFactory(Shape.STRING, StringHandler::new);
        registry.registerTypeHandlerFactory(Shape.QUANTITY, QuantityHandler::new);

        // Generic handlers
        registry.registerGenericHandlerFactory(CollectionOperationHandler::new);

        return registry;
    }

    public OperationHandler createHandler(
            String operation,
            Type dispatchType,
            CodeGenContext context) {

        // Try operation-based first
        OperationHandlerFactory opFactory = operationHandlers.get(operation);
        if (opFactory != null) {
            return opFactory.create(operation, dispatchType, context);
        }

        // Try type-based
        TypeHandlerFactory typeFactory = typeHandlers.get(dispatchType);
        if (typeFactory != null) {
            return typeFactory.create(operation, dispatchType, context);
        }

        // Try generic handlers
        for (GenericHandlerFactory genericFactory : genericHandlers) {
            OperationHandler handler = genericFactory.create(operation, dispatchType, context);
            if (handler.canHandle(operation)) {
                return handler;
            }
        }

        // Not found - error
        throw new CodeGenerationException(
                "No handler registered for operation",
                operation, dispatchType, List.of()
        );
    }

    // Registration methods
    void registerOperationHandlerFactory(String key, OperationHandlerFactory factory) {
        operationHandlers.put(key, factory);
    }

    void registerTypeHandlerFactory(Type type, TypeHandlerFactory factory) {
        typeHandlers.put(type, factory);
    }

    void registerGenericHandlerFactory(GenericHandlerFactory factory) {
        genericHandlers.add(factory);
    }
}

// Factory interfaces
@FunctionalInterface
interface OperationHandlerFactory {
    OperationHandler create(String operation, Type dispatchType, CodeGenContext context);
}

@FunctionalInterface
interface TypeHandlerFactory {
    OperationHandler create(String operation, Type dispatchType, CodeGenContext context);
}

@FunctionalInterface
interface GenericHandlerFactory {
    OperationHandler create(String operation, Type dispatchType, CodeGenContext context);
}
```

---

## SparkCodeGenerator Integration

```java
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

    private static final Logger log = LoggerFactory.getLogger(SparkCodeGenerator.class);

    private final HandlerRegistry handlerRegistry;
    private final CodeGenContext context;

    public SparkCodeGenerator(HandlerRegistry handlerRegistry, SparkSession spark) {
        this.handlerRegistry = handlerRegistry;
        this.context = new CodeGenContext(spark);
    }

    @Override
    public Column visitOperation(Operation op) {
        if (log.isDebugEnabled()) {
            log.debug("Generating SQL for IR: {}", op);  // Includes Shape
        }

        // Evaluate arguments
        List<Column> argColumns = op.args().stream()
                .map(this::visit)
                .toList();

        // Determine dispatch type
        Type dispatchType = determineDispatchType(op);

        // Create handler
        OperationHandler handler = handlerRegistry.createHandler(
                op.name(),
                dispatchType,
                context
        );

        // Invoke handler with boxing/unboxing
        InvocationBinder binder = new InvocationBinder();
        Column result = binder.invoke(handler, findHandlerMethod(handler, op.name()),
                argColumns, op.args());

        if (log.isDebugEnabled()) {
            log.debug("Generated SQL: {}", result.expr().sql());
        }

        return result;
    }

    private Type determineDispatchType(Operation op) {
        // Use dispatch strategy from signature
        DispatchStrategy strategy = op.signature().definition().dispatchStrategy();

        return switch (strategy) {
            case RESULT_TYPE -> op.getType();
            case FIRST_ARG_TYPE -> op.args().get(0).getType();
            case GENERIC -> Shape.ANY;  // Or similar
        };
    }

    // Other visitor methods...
}
```

---

## Testing Examples

### Handler Method Test

```java
@ExtendWith(SparkExtension.class)
class CollectionOperationHandlerTest {

    private CollectionOperationHandler handler;
    private CodeGenContext context;

    @BeforeEach
    void setUp(SparkSession spark) {
        this.context = new CodeGenContext(spark);
        this.handler = new CollectionOperationHandler("count", Shape.ANY, context);
    }

    @Test
    void count_onSingularCollection_shouldReturnOne() {
        // Given: Singular collection with value
        Dataset<Row> df = spark.createDataFrame(
                List.of(new Row(5)),
                RowSchema.of("value")
        );
        Collection input = Collection.singular(df.col("value"));

        // When: Count
        Column result = handler.count(input);

        // Then: Should return 1
        assertEquals(1, df.select(result.as("count")).first().getInt(0));
    }

    @Test
    void count_onArrayCollection_shouldReturnSize() {
        // Given: Array collection [1, 2, 3]
        Dataset<Row> df = spark.createDataFrame(
                List.of(new Row(new int[]{1, 2, 3})),
                RowSchema.of("array")
        );
        Collection input = Collection.nonSingular(df.col("array"));

        // When: Count
        Column result = handler.count(input);

        // Then: Should return 3
        assertEquals(3, df.select(result.as("count")).first().getInt(0));
    }

    @Test
    void count_onNullCollection_shouldReturnZero() {
        // Given: NULL (empty collection)
        Dataset<Row> df = spark.createDataFrame(
                List.of(new Row((Object) null)),
                RowSchema.of("value")
        );
        Collection input = Collection.nonSingular(df.col("value"));

        // When: Count
        Column result = handler.count(input);

        // Then: Should return 0 (not NULL!)
        assertEquals(0, df.select(result.as("count")).first().getInt(0));
    }
}
```

### Integration Test

```java
@ExtendWith(SparkExtension.class)
class IntegrationTest {

    private SparkCodeGenerator codeGen;

    @BeforeEach
    void setUp(SparkSession spark) {
        HandlerRegistry registry = HandlerRegistry.standard();
        this.codeGen = new SparkCodeGenerator(registry, spark);
    }

    @Test
    void arithmeticOperation_shouldGenerateCorrectSQL() {
        // Given: IR for "5 + 3"
        IRNode five = Literal.of(5, Shape.INTEGER);
        IRNode three = Literal.of(3, Shape.INTEGER);
        ResolvedSignature sig = resolveSignature("add", List.of(five, three));
        IRNode addOp = new Operation("add", List.of(five, three), sig);

        // When: Generate SQL
        Column result = codeGen.visit(addOp);

        // Then: Should produce correct result
        Dataset<Row> df = spark.range(1).select(result.as("result"));
        assertEquals(8, df.first().getInt(0));
    }
}
```

---

This document provides concrete implementation examples for all major components of the design.
