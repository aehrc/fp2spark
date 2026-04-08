package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.struct;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;

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

  /**
   * Number of args per variant in the "typeChoice" operation: [variantCol, namespace, name,
   * baseType]. Must match the packing in {@code Analyzer.resolveChoiceTypeFunction()}.
   */
  static final int VARIANT_GROUP_SIZE = 4;

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

    // Choice type() singular: CASE WHEN chain over pre-evaluated variant columns
    registry.register("typeChoice", TypeOps::generateChoiceType);

    // Choice type() plural: element-wise transform with CASE WHEN over variant field names
    registry.register("typeChoicePlural", TypeOps::generatePluralChoiceType);

    // Multi-variant coalesce for ofType on singular parents:
    // coalesce(variant1, variant2, ...) — first non-null wins
    registry.register("coalesce", ctx -> coalesce(ctx.args().toArray(new Column[0])));

    // Multi-variant coalesce for ofType on plural parents:
    // filter(transform(parent, x -> coalesce(x.f1, x.f2, ...)), y -> y IS NOT NULL)
    // Args: [parentArray, fieldName1, fieldName2, ...]
    registry.register("coalesceFields", TypeOps::generateCoalesceFields);
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
    for (int i = ctx.args().size() - VARIANT_GROUP_SIZE; i >= 0; i -= VARIANT_GROUP_SIZE) {
      final Column variantCol = ctx.arg(i);
      final Column ns = ctx.arg(i + 1);
      final Column nm = ctx.arg(i + 2);
      final Column bt = ctx.arg(i + 3);
      result = when(variantCol.isNotNull(), typeInfoStruct(ns, nm, bt)).otherwise(result);
    }
    return result;
  }

  /**
   * Generates type info for plural choice types using element-wise transform. Args: [parent, col1,
   * ns1, name1, bt1, col2, ...]. The parent array is transformed per-element, checking each variant
   * field for non-null.
   */
  private static Column generatePluralChoiceType(final SparkOpContext ctx) {
    final CollectionValue parent = ctx.collectionArg(0);
    return parent
        .map(
            elem -> {
              Column result = lit(null).cast(SparkTypeMapper.TYPE_INFO_TYPE);
              for (int i = ctx.args().size() - VARIANT_GROUP_SIZE;
                  i >= 1;
                  i -= VARIANT_GROUP_SIZE) {
                final String colName = (String) ctx.literalArg(i).value();
                final Column ns = ctx.arg(i + 1);
                final Column nm = ctx.arg(i + 2);
                final Column bt = ctx.arg(i + 3);
                result =
                    when(elem.getField(colName).isNotNull(), typeInfoStruct(ns, nm, bt))
                        .otherwise(result);
              }
              return result;
            })
        .column();
  }

  /**
   * Generates per-element coalesce over multiple variant fields on a plural parent.
   *
   * <p>Args: [parentArray, fieldName1, fieldName2, ...]. Produces: {@code filter(transform(parent,
   * x -> coalesce(x.f1, x.f2, ...)), y -> y IS NOT NULL)}, with empty arrays converted to null.
   */
  private static Column generateCoalesceFields(final SparkOpContext ctx) {
    final CollectionValue parent = ctx.collectionArg(0);
    final CollectionValue result =
        parent
            .map(
                elem -> {
                  final Column[] fields = new Column[ctx.args().size() - 1];
                  for (int i = 1; i < ctx.args().size(); i++) {
                    final String fieldName = (String) ctx.literalArg(i).value();
                    fields[i - 1] = elem.getField(fieldName);
                  }
                  return coalesce(fields);
                })
            .filterNulls();
    return CollectionValue.nullIfEmpty(result.column());
  }

  private static Column typeInfoStruct(
      final Column namespace, final Column name, final Column baseType) {
    return struct(namespace.as("namespace"), name.as("name"), baseType.as("baseType"));
  }
}
