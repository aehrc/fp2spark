package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.typing.SystemType;
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
 * Factory methods and type-dispatch builder for {@link SparkOperationDef} instances.
 *
 * <p>Provides reusable factories for common operation patterns ({@link #unary}, {@link #binary},
 * {@link #ternary}, {@link #collectionUnary}) and a declarative type-dispatch builder ({@link
 * #byArgType}, {@link #byResultType}) that selects a delegate based on argument or result type.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * import static com.example.fhirpath.codegen.spark.SparkDefs.*;
 *
 * registry.register("gt", byArgType(0)
 *     .when(types(INTEGER, DECIMAL, STRING), binary(Column::gt))
 *     .when(types(DATE, DATE_TIME, TIME), ctx -> TemporalSupport.temporalCompare(...)));
 *
 * registry.register("first", collectionUnary(c -> get(c, lit(0)), UnaryOperator.identity()));
 * }</pre>
 */
public final class SparkDefs {

  private final Function<SparkOpContext, Type> typeExtractor;
  private final List<Case> cases = new ArrayList<>();

  private SparkDefs(@Nonnull final Function<SparkOpContext, Type> typeExtractor) {
    this.typeExtractor = typeExtractor;
  }

  /** Dispatches on the type of the argument at the given index. */
  @Nonnull
  public static SparkDefs byArgType(final int index) {
    return new SparkDefs(ctx -> ctx.argType(index));
  }

  /** Dispatches on the result type of the operation. */
  @Nonnull
  public static SparkDefs byResultType() {
    return new SparkDefs(SparkOpContext::resultType);
  }

  /**
   * Registers a case: when the dispatch type matches any of the given types, delegate to the given
   * definition.
   */
  @Nonnull
  public SparkDefs when(
      @Nonnull final Set<SystemType> types, @Nonnull final SparkOperationDef def) {
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
  public static Set<SystemType> types(@Nonnull final SystemType... types) {
    return Set.of(types);
  }

  /** Creates a unary operation definition from a {@link UnaryOperator} on columns. */
  @Nonnull
  public static SparkOperationDef unary(@Nonnull final UnaryOperator<Column> op) {
    return ctx -> op.apply(ctx.arg(0));
  }

  /** Creates a binary operation definition from a {@link BinaryOperator} on columns. */
  @Nonnull
  public static SparkOperationDef binary(@Nonnull final BinaryOperator<Column> op) {
    return ctx -> op.apply(ctx.arg(0), ctx.arg(1));
  }

  /** Creates a ternary operation definition from a {@link TernaryOperator} on columns. */
  @Nonnull
  public static SparkOperationDef ternary(@Nonnull final TernaryOperator<Column> op) {
    return ctx -> op.apply(ctx.arg(0), ctx.arg(1), ctx.arg(2));
  }

  /**
   * A function that accepts three arguments of the same type and produces a result.
   *
   * @param <T> the type of the operands and result
   */
  @FunctionalInterface
  public interface TernaryOperator<T> {
    /** Applies this operator to the given operands. */
    T apply(T a, T b, T c);
  }

  /**
   * Creates a collection operation definition that dispatches on the cardinality of the first
   * argument.
   *
   * <p>Equivalent to {@code ctx.collectionArg(0).apply(arrayFn, singleFn)}. Use this for functions
   * that operate on a single collection argument and behave differently on arrays vs singular
   * values (e.g., {@code first}, {@code last}, {@code tail}).
   *
   * @param arrayFn transformation applied when the argument is an array
   * @param singleFn transformation applied when the argument is singular
   */
  @Nonnull
  public static SparkOperationDef collectionUnary(
      @Nonnull final UnaryOperator<Column> arrayFn, @Nonnull final UnaryOperator<Column> singleFn) {
    return ctx -> ctx.collectionArg(0).apply(arrayFn, singleFn);
  }

  /**
   * Creates a collection operation definition that dispatches on the cardinality of the first
   * argument, with a default value for null (empty collection).
   *
   * <p>Equivalent to {@code ctx.collectionArg(0).applyNonNull(arrayFn, singleFn, nullDefault)}. Use
   * this for aggregate functions that return a defined value for empty collections (e.g., {@code
   * count} returns 0).
   *
   * @param arrayFn transformation applied when the argument is a non-null array
   * @param singleFn transformation applied when the argument is a non-null singular value
   * @param nullDefault the column expression returned when the argument is null (empty collection)
   */
  @Nonnull
  public static SparkOperationDef collectionUnary(
      @Nonnull final UnaryOperator<Column> arrayFn,
      @Nonnull final UnaryOperator<Column> singleFn,
      @Nonnull final Column nullDefault) {
    return ctx -> ctx.collectionArg(0).applyNonNull(arrayFn, singleFn, nullDefault);
  }

  private record Case(@Nonnull Set<SystemType> types, @Nonnull SparkOperationDef def) {}
}
