package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.apache.spark.sql.Column;

/**
 * Declarative type-dispatch builder for Spark operation definitions.
 *
 * <p>Produces a {@link SparkOperationDef} that selects a delegate based on a dispatched type
 * (argument type or result type). Eliminates repetitive switch-on-type patterns.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * import static com.example.fhirpath.codegen.spark.TypeDispatch.*;
 *
 * registry.register("gt", byArgType(0)
 *     .when(types(INTEGER, DECIMAL, STRING), binary(Column::gt))
 *     .when(types(DATE, DATE_TIME, TIME), ctx -> TemporalOps.temporalCompare(...)));
 * }</pre>
 */
public final class TypeDispatch {

  private final Function<SparkOpContext, Type> typeExtractor;
  private final List<Case> cases = new ArrayList<>();

  private TypeDispatch(@Nonnull final Function<SparkOpContext, Type> typeExtractor) {
    this.typeExtractor = typeExtractor;
  }

  /** Dispatches on the type of the argument at the given index. */
  @Nonnull
  public static TypeDispatch byArgType(final int index) {
    return new TypeDispatch(ctx -> ctx.argType(index));
  }

  /** Dispatches on the result type of the operation. */
  @Nonnull
  public static TypeDispatch byResultType() {
    return new TypeDispatch(SparkOpContext::resultType);
  }

  /**
   * Registers a case: when the dispatch type matches any of the given types, delegate to the given
   * definition.
   */
  @Nonnull
  public TypeDispatch when(
      @Nonnull final Set<PrimitiveType> types, @Nonnull final SparkOperationDef def) {
    cases.add(new Case(types, def));
    return this;
  }

  /** Builds the dispatch into a {@link SparkOperationDef}. */
  @Nonnull
  public SparkOperationDef build() {
    final var snapshot = List.copyOf(cases);
    final var extractor = typeExtractor;
    return ctx -> {
      final Type type = extractor.apply(ctx);
      for (final Case c : snapshot) {
        if (c.types.contains(type)) {
          return c.def.generate(ctx);
        }
      }
      throw new IllegalArgumentException("Unsupported type for " + ctx.name() + ": " + type);
    };
  }

  /** Creates an immutable set of primitive types for use with {@link #when}. */
  @Nonnull
  public static Set<PrimitiveType> types(@Nonnull final PrimitiveType... types) {
    return Set.of(types);
  }

  /** Creates a binary operation definition from a {@link BinaryOperator} on columns. */
  @Nonnull
  public static SparkOperationDef binary(@Nonnull final BinaryOperator<Column> op) {
    return ctx -> op.apply(ctx.arg(0), ctx.arg(1));
  }

  /** Creates a unary operation definition from a {@link UnaryOperator} on columns. */
  @Nonnull
  public static SparkOperationDef unary(@Nonnull final UnaryOperator<Column> op) {
    return ctx -> op.apply(ctx.arg(0));
  }

  private record Case(@Nonnull Set<PrimitiveType> types, @Nonnull SparkOperationDef def) {}
}
