package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.codegen.spark.ops.ArithmeticOps;
import com.example.fhirpath.codegen.spark.ops.BooleanOps;
import com.example.fhirpath.codegen.spark.ops.CollectionOps;
import com.example.fhirpath.codegen.spark.ops.CombineOps;
import com.example.fhirpath.codegen.spark.ops.ComparisonOps;
import com.example.fhirpath.codegen.spark.ops.ConversionOps;
import com.example.fhirpath.codegen.spark.ops.EqualityOps;
import com.example.fhirpath.codegen.spark.ops.FhirOps;
import com.example.fhirpath.codegen.spark.ops.FilteringOps;
import com.example.fhirpath.codegen.spark.ops.MathOps;
import com.example.fhirpath.codegen.spark.ops.MembershipOps;
import com.example.fhirpath.codegen.spark.ops.SetOps;
import com.example.fhirpath.codegen.spark.ops.StringOps;
import com.example.fhirpath.codegen.spark.ops.TypeOps;
import com.example.fhirpath.codegen.spark.ops.UtilityOps;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry mapping operation names to their Spark code generation functions.
 *
 * <p>Use {@link SparkDefs} factory methods ({@code binary}, {@code unary}, {@code collectionUnary},
 * etc.) to create {@link SparkOperationDef} instances, then register them here. All operations are
 * pure functions.
 */
public final class SparkOperationRegistry {

  private final Map<String, SparkOperationDef> operations = new HashMap<>();

  /** Register an operation with full control over arguments, types, and generator. */
  public void register(@Nonnull final String name, @Nonnull final SparkOperationDef def) {
    operations.put(name, def);
  }

  /** Register a type-dispatched operation built from a {@link SparkDefs} builder. */
  public void register(@Nonnull final String name, @Nonnull final SparkDefs dispatch) {
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
    StringOps.register(registry);
    MathOps.register(registry);
    ConversionOps.register(registry);
    UtilityOps.register(registry);
    return registry;
  }
}
