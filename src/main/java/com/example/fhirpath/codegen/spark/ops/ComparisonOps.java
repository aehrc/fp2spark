package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkDefs.binary;
import static com.example.fhirpath.codegen.spark.SparkDefs.byArgType;
import static com.example.fhirpath.codegen.spark.SparkDefs.types;
import static com.example.fhirpath.typing.SystemType.DATE;
import static com.example.fhirpath.typing.SystemType.DATE_TIME;
import static com.example.fhirpath.typing.SystemType.DECIMAL;
import static com.example.fhirpath.typing.SystemType.INTEGER;
import static com.example.fhirpath.typing.SystemType.QUANTITY;
import static com.example.fhirpath.typing.SystemType.STRING;
import static com.example.fhirpath.typing.SystemType.TIME;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import jakarta.annotation.Nonnull;
import java.util.function.BinaryOperator;
import org.apache.spark.sql.Column;

/**
 * Comparison operator registrations.
 *
 * <p>Uses input type from first argument (not result type, which is always Boolean).
 */
public final class ComparisonOps {

  private ComparisonOps() {}

  /**
   * Registers all comparison operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    comparison(registry, "gt", Column::gt);
    comparison(registry, "lt", Column::lt);
    comparison(registry, "geq", Column::geq);
    comparison(registry, "leq", Column::leq);
  }

  private static void comparison(
      @Nonnull final SparkOperationRegistry registry,
      @Nonnull final String name,
      @Nonnull final BinaryOperator<Column> op) {
    registry.register(
        name,
        byArgType(0)
            .when(types(INTEGER, DECIMAL, STRING), binary(op))
            .when(types(QUANTITY), binary(QuantitySupport.quantityComparator(op)))
            .when(types(DATE, DATE_TIME, TIME), binary(TemporalSupport.temporalComparator(op))));
  }
}
