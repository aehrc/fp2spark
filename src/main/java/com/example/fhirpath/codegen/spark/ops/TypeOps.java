package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.struct;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.types.DataTypes;

/**
 * Spark code generation for type testing and reflection operations.
 *
 * <p>The {@code is} operator has two forms:
 *
 * <ul>
 *   <li><b>Unary (choice type):</b> checks whether a variant column is non-null. Created by {@code
 *       Analyzer.resolveChoiceTypeOperation()}.
 *   <li><b>Binary (non-choice type):</b> null-propagating static type match. The first argument is
 *       the value, the second is a boolean literal indicating the static match result. Created by
 *       {@code Analyzer.resolveNonChoiceTypeOperation()}.
 * </ul>
 *
 * <p>The {@code type} function (non-choice) takes 4 args: [target, namespace, name, baseType] and
 * returns a constant TypeInfo struct for each non-null element.
 *
 * <p>The {@code typeChoice} function takes 4n args in groups: [variantCol, namespace, name,
 * baseType, ...] and builds a CASE WHEN chain checking which variant is non-null.
 *
 * <p>The {@code ofType} and {@code as} operators are resolved to {@link
 * com.example.fhirpath.ir.Traversal} nodes by the Analyzer, so they use existing traversal code
 * generation and don't need Spark operation registrations.
 */
public final class TypeOps {

  private TypeOps() {}

  /**
   * Registers type testing and reflection operations into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register(
        "is",
        ctx -> {
          if (ctx.args().size() == 2) {
            // Non-choice: CASE WHEN value IS NOT NULL THEN match_result ELSE NULL END
            return when(ctx.arg(0).isNotNull(), ctx.arg(1));
          }
          // Choice type: variant column null check
          return ctx.arg(0).isNotNull();
        });

    // Non-choice type(): static type info applied to each non-null element
    registry.register(
        "type",
        ctx -> {
          final CollectionValue target = ctx.collectionArg(0);
          final Column typeInfoStruct = typeInfoStruct(ctx.arg(1), ctx.arg(2), ctx.arg(3));
          return target.map(elem -> when(elem.isNotNull(), typeInfoStruct)).column();
        });

    // Choice type(): CASE WHEN chain over variant columns
    registry.register("typeChoice", TypeOps::generateChoiceType);
  }

  /**
   * Generates type info for choice types using a CASE WHEN chain. Each variant's pre-evaluated
   * column is checked for non-null, and the corresponding type info struct is returned.
   *
   * <p>Args come in groups of 4: [variantCol, namespace, name, baseType, ...].
   */
  private static Column generateChoiceType(final SparkOpContext ctx) {
    Column result = lit(null).cast(SparkTypeMapper.TYPE_INFO_TYPE);
    // Iterate in reverse so the first matching variant wins
    for (int i = ctx.args().size() - 4; i >= 0; i -= 4) {
      final Column variantCol = ctx.arg(i);
      final Column ns = ctx.arg(i + 1);
      final Column nm = ctx.arg(i + 2);
      final Column bt = ctx.arg(i + 3);
      result = when(variantCol.isNotNull(), typeInfoStruct(ns, nm, bt)).otherwise(result);
    }
    return result;
  }

  /** Creates a TypeInfo struct column from namespace, name, and baseType columns. */
  private static Column typeInfoStruct(
      final Column namespace, final Column name, final Column baseType) {
    return struct(
        namespace.cast(DataTypes.StringType).as("namespace"),
        name.cast(DataTypes.StringType).as("name"),
        baseType.cast(DataTypes.StringType).as("baseType"));
  }
}
