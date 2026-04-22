package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkDefs.binary;
import static com.example.fhirpath.codegen.spark.SparkDefs.byResultType;
import static com.example.fhirpath.codegen.spark.SparkDefs.types;
import static com.example.fhirpath.codegen.spark.SparkDefs.unary;
import static com.example.fhirpath.typing.SystemType.DATE;
import static com.example.fhirpath.typing.SystemType.DATE_TIME;
import static com.example.fhirpath.typing.SystemType.DECIMAL;
import static com.example.fhirpath.typing.SystemType.INTEGER;
import static com.example.fhirpath.typing.SystemType.QUANTITY;
import static com.example.fhirpath.typing.SystemType.STRING;
import static com.example.fhirpath.typing.SystemType.TIME;
import static org.apache.spark.sql.functions.lit;

import com.example.fhirpath.codegen.spark.SparkOperationDef;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.codegen.spark.udf.QuantityArithmetic;
import com.example.fhirpath.codegen.spark.udf.TemporalArithmetic;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Arithmetic operator registrations.
 *
 * <p>Uses full registration for type-dispatched operations (add uses concat for strings). Division
 * by zero returns empty (null) per FHIRPath spec.
 */
public final class ArithmeticOps {

  private ArithmeticOps() {}

  /**
   * Creates a quantity arithmetic dispatch that delegates to the {@link QuantityArithmetic} UDF.
   */
  @Nonnull
  private static SparkOperationDef quantityOp(@Nonnull final String opCode) {
    return ctx -> QuantityArithmetic.UDF.apply(ctx.arg(0), ctx.arg(1), lit(opCode));
  }

  /**
   * Creates a temporal arithmetic dispatch that delegates to the {@link TemporalArithmetic} UDF.
   */
  @Nonnull
  private static SparkOperationDef temporalOp(@Nonnull final String opCode) {
    return ctx -> TemporalArithmetic.UDF.apply(ctx.arg(0), ctx.arg(1), lit(opCode));
  }

  /**
   * Registers all arithmetic operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register(
        "add",
        byResultType()
            .when(types(INTEGER, DECIMAL), binary(Column::plus))
            .when(types(STRING), binary(functions::concat))
            .when(types(QUANTITY), quantityOp(QuantityArithmetic.OP_ADD))
            .when(types(DATE, DATE_TIME, TIME), temporalOp(TemporalArithmetic.OP_ADD)));

    registry.register(
        "sub",
        byResultType()
            .when(types(INTEGER, DECIMAL), binary(Column::minus))
            .when(types(QUANTITY), quantityOp(QuantityArithmetic.OP_SUB))
            .when(types(DATE, DATE_TIME, TIME), temporalOp(TemporalArithmetic.OP_SUB)));

    registry.register(
        "multiply",
        byResultType()
            .when(types(INTEGER, DECIMAL), binary(Column::multiply))
            .when(types(QUANTITY), quantityOp(QuantityArithmetic.OP_MUL)));

    registry.register(
        "divide",
        byResultType()
            .when(types(INTEGER, DECIMAL), binary(NumericalSupport::division))
            .when(types(QUANTITY), quantityOp(QuantityArithmetic.OP_DIV)));

    registry.register("mod", binary(NumericalSupport::modulo));

    registry.register(
        "div",
        byResultType()
            .when(types(INTEGER), binary(NumericalSupport::integerDivision))
            .when(types(DECIMAL), binary(NumericalSupport::decimalDivision)));

    registry.register("stringConcat", binary(StringSupport::stringConcat));

    // Unary plus: identity operation (same for numeric and Quantity inputs).
    registry.register("unaryPlus", unary(col -> col));

    // Unary minus: negation.
    // Numeric: multiply by -1. Quantity: negate the value field, preserve unit/system/code.
    registry.register(
        "unaryMinus",
        byResultType()
            .when(types(INTEGER, DECIMAL), unary(col -> col.multiply(lit(-1))))
            .when(types(QUANTITY), unary(ArithmeticOps::quantityNegate)));
  }

  /**
   * Unary minus for Quantity: negates the numeric value field, preserving unit, system, and code.
   * Null (empty) input propagates naturally because Spark arithmetic on null yields null. Uses
   * {@code withField} so the implementation is resilient to Quantity schema changes.
   */
  @Nonnull
  private static Column quantityNegate(@Nonnull final Column quantity) {
    return quantity.withField("value", quantity.getField("value").multiply(lit(-1)));
  }
}
