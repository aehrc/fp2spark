package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.struct;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.typing.ResolvedReferenceType;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Spark code generation for type testing and reflection operations.
 *
 * <p>The {@code is} operator has three forms, dispatched by argument count and input type:
 *
 * <ul>
 *   <li><b>Unary (choice type):</b> checks whether a variant column is non-null.
 *   <li><b>Binary with static result (non-choice):</b> null-propagating static type match. The
 *       second argument is a boolean literal indicating the compile-time match result.
 *   <li><b>Binary with resolved reference:</b> runtime type comparison. The second argument is a
 *       string literal type name, compared against the extracted type string.
 * </ul>
 *
 * <p>The {@code as} and {@code ofType} operators are normally resolved to {@link
 * com.example.fhirpath.ir.Traversal} nodes by the Analyzer (choice types) or static literals
 * (non-choice types). For non-choice {@code ofType} on plural collections, a 1-arg {@code ofType}
 * operation is emitted to strip null elements. For resolved references, a 2-arg variant filters by
 * type name string comparison.
 *
 * <p>The {@code type} function (non-choice) takes 4 args: [target, namespace, name, baseType] and
 * returns a TypeInfo struct for each element (including null elements of primitive collections,
 * whose declared type is statically known).
 *
 * <p>The {@code typeChoice} function takes 4n args in groups: [variantCol, namespace, name,
 * baseType, ...] and builds a CASE WHEN chain checking which variant is non-null.
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
          if (ctx.args().size() == 1) {
            // Choice type: variant column null check
            return ctx.arg(0).isNotNull();
          }
          if (ctx.argType(0) instanceof ResolvedReferenceType) {
            // Resolved reference: runtime type string comparison
            return when(ctx.arg(0).isNotNull(), ctx.arg(0).equalTo(ctx.arg(1)));
          }
          // Non-choice: CASE WHEN value IS NOT NULL THEN match_result ELSE NULL END
          return when(ctx.arg(0).isNotNull(), ctx.arg(1));
        });

    // Resolved reference as: returns typeString if it matches, null otherwise
    registry.register("as", ctx -> when(ctx.arg(0).equalTo(ctx.arg(1)), ctx.arg(0)));

    // ofType: two forms dispatched by argument count.
    //  - 1 arg: filter null elements from a plural collection (static type match; see
    //    Analyzer.resolveNonChoiceTypeOperation). Singular inputs pass through unchanged.
    //  - 2 args: resolved-reference runtime filtering, keeping elements whose type string matches.
    registry.register(
        "ofType",
        ctx -> {
          if (ctx.args().size() == 1) {
            final CollectionValue coll = ctx.collectionArg(0);
            return coll.isSingular()
                ? coll.column()
                : CollectionValue.nullIfEmpty(coll.filterNulls().column());
          }
          final CollectionValue coll = ctx.collectionArg(0);
          final Column typeName = ctx.arg(1);
          return coll.apply(
              arr -> CollectionValue.nullIfEmpty(functions.filter(arr, t -> t.equalTo(typeName))),
              col -> when(col.equalTo(typeName), col));
        });

    // Non-choice type(): static type info. For arrays, emit a TypeInfo struct unconditionally per
    // element — null-valued primitives still have a declared type (#182). For singular inputs, the
    // null-check preserves empty-collection propagation.
    registry.register(
        "type",
        ctx -> {
          final CollectionValue target = ctx.collectionArg(0);
          final Column typeInfoStruct = typeInfoStruct(ctx.arg(1), ctx.arg(2), ctx.arg(3));
          return target.apply(
              arr -> functions.transform(arr, elem -> typeInfoStruct),
              col -> when(col.isNotNull(), typeInfoStruct));
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
