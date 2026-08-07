package au.csiro.fhirpath.spark;

import au.csiro.fhirpath.spark.ops.ArithmeticOps;
import au.csiro.fhirpath.spark.ops.BooleanOps;
import au.csiro.fhirpath.spark.ops.CollectionOps;
import au.csiro.fhirpath.spark.ops.CombineOps;
import au.csiro.fhirpath.spark.ops.ComparisonOps;
import au.csiro.fhirpath.spark.ops.ConversionOps;
import au.csiro.fhirpath.spark.ops.EqualityOps;
import au.csiro.fhirpath.spark.ops.FhirOps;
import au.csiro.fhirpath.spark.ops.FilteringOps;
import au.csiro.fhirpath.spark.ops.MathOps;
import au.csiro.fhirpath.spark.ops.MembershipOps;
import au.csiro.fhirpath.spark.ops.SetOps;
import au.csiro.fhirpath.spark.ops.StringOps;
import au.csiro.fhirpath.spark.ops.TerminologyOps;
import au.csiro.fhirpath.spark.ops.TypeOps;
import au.csiro.fhirpath.spark.ops.UtilityOps;
import au.csiro.fhirpath.terminology.NoTerminologyService;
import au.csiro.fhirpath.terminology.TerminologyServiceFactory;
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

  /**
   * Creates the standard registry with all built-in operations, without terminology server access.
   *
   * <p>Terminology functions still compile and evaluate, but every value set is reported as
   * unresolvable and therefore yields an empty result — see {@link NoTerminologyService}. Compiling
   * a {@code memberOf()} call against this registry logs a warning, because an empty result is
   * falsy inside {@code where()} and so silently excludes every element. Use {@link
   * #standard(TerminologyServiceFactory)} to get real answers.
   */
  @Nonnull
  public static SparkOperationRegistry standard() {
    return standard(NoTerminologyService.INSTANCE);
  }

  /**
   * Creates the standard registry with all built-in operations, resolving terminology functions
   * against the given terminology service.
   *
   * @param terminologyServiceFactory the factory used to reach a terminology server on executors
   * @return a registry with all built-in operations registered
   */
  @Nonnull
  public static SparkOperationRegistry standard(
      @Nonnull final TerminologyServiceFactory terminologyServiceFactory) {
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
    TerminologyOps.register(registry, terminologyServiceFactory);
    return registry;
  }
}
