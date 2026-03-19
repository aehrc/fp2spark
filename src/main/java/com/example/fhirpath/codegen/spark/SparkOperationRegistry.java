package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.codegen.spark.ops.ArithmeticOps;
import com.example.fhirpath.codegen.spark.ops.BooleanOps;
import com.example.fhirpath.codegen.spark.ops.CollectionOps;
import com.example.fhirpath.codegen.spark.ops.CombineOps;
import com.example.fhirpath.codegen.spark.ops.ComparisonOps;
import com.example.fhirpath.codegen.spark.ops.EqualityOps;
import com.example.fhirpath.codegen.spark.ops.FhirOps;
import com.example.fhirpath.codegen.spark.ops.FilteringOps;
import com.example.fhirpath.codegen.spark.ops.MembershipOps;
import com.example.fhirpath.codegen.spark.ops.SetOps;
import com.example.fhirpath.codegen.spark.ops.TypeOps;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
import org.apache.spark.sql.Column;

/**
 * Registry mapping operation names to their Spark code generation functions.
 *
 * <p>Provides convenience methods for common patterns (binary, unary) and full-control registration
 * for complex cases. All operations are pure functions.
 */
public final class SparkOperationRegistry {

  private final Map<String, SparkOperationDef> operations = new HashMap<>();

  /** Register a simple binary operation (two Column args, ignores type/generator). */
  public void binary(@Nonnull final String name, @Nonnull final BinaryOperator<Column> fn) {
    operations.put(name, ctx -> fn.apply(ctx.arg(0), ctx.arg(1)));
  }

  /** Register a simple unary operation (one Column arg, ignores type/generator). */
  public void unary(@Nonnull final String name, @Nonnull final UnaryOperator<Column> fn) {
    operations.put(name, ctx -> fn.apply(ctx.arg(0)));
  }

  /** Register an operation with full control over arguments, types, and generator. */
  public void register(@Nonnull final String name, @Nonnull final SparkOperationDef def) {
    operations.put(name, def);
  }

  /** Register a type-dispatched operation built from a {@link TypeDispatch} builder. */
  public void register(@Nonnull final String name, @Nonnull final TypeDispatch dispatch) {
    operations.put(name, dispatch.build());
  }

  /**
   * Look up an operation by name.
   *
   * @return the operation definition, or null if not registered
   */
  @Nullable
  public SparkOperationDef get(@Nonnull final String name) {
    return operations.get(name);
  }

  /** Creates the standard registry with all built-in operations. */
  @Nonnull
  public static SparkOperationRegistry standard() {
    final SparkOperationRegistry registry = new SparkOperationRegistry();
    BooleanOps.register(registry);
    ArithmeticOps.register(registry);
    ComparisonOps.register(registry);
    EqualityOps.register(registry);
    MembershipOps.register(registry);
    CombineOps.register(registry);
    SetOps.register(registry);
    CollectionOps.register(registry);
    FilteringOps.register(registry);
    FhirOps.register(registry);
    TypeOps.register(registry);
    return registry;
  }
}
