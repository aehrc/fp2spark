package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Boolean operator registrations for FHIRPath three-valued logic.
 *
 * <p>All operators follow FHIRPath specification section 6.5 where operands can be true, false, or
 * empty (NULL).
 */
public final class BooleanOps {

  private BooleanOps() {}

  /**
   * Registers all boolean operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    // Spark's SQL-92 AND/OR match FHIRPath three-valued logic exactly
    registry.binary("and", Column::and);
    registry.binary("or", Column::or);

    // implies: NOT left OR right
    registry.binary("implies", (l, r) -> functions.not(l).or(r));

    // xor: null if either operand is null, otherwise not-equal
    registry.binary(
        "xor", (l, r) -> when(l.isNull().or(r.isNull()), lit(null)).otherwise(l.notEqual(r)));

    // not: Spark's NOT handles three-valued logic correctly
    registry.unary("not", functions::not);
  }
}
