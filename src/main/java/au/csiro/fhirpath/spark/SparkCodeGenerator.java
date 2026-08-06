package au.csiro.fhirpath.spark;

import static au.csiro.fhirpath.spark.SparkTypeMapper.toSparkDataType;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

import au.csiro.fhirpath.ir.Cast;
import au.csiro.fhirpath.ir.IRNodeVisitor;
import au.csiro.fhirpath.ir.Lambda;
import au.csiro.fhirpath.ir.Literal;
import au.csiro.fhirpath.ir.Operation;
import au.csiro.fhirpath.ir.Resource;
import au.csiro.fhirpath.ir.ThisReference;
import au.csiro.fhirpath.ir.Traversal;
import au.csiro.fhirpath.typing.CodingValue;
import au.csiro.fhirpath.typing.FhirPrimitiveType;
import au.csiro.fhirpath.typing.InlineResourceType;
import au.csiro.fhirpath.typing.QuantityValue;
import au.csiro.fhirpath.typing.TemporalValue;
import au.csiro.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

/**
 * Generates Spark Column expressions from FHIRPath IR trees.
 *
 * <p>This visitor implements target-specific code generation for Apache Spark SQL. Each visit
 * method transforms an IR node into a Spark Column that can be executed by the Spark SQL engine.
 *
 * <p>All operations are dispatched through a {@link SparkOperationRegistry} — a simple map from
 * operation name to pure function.
 *
 * <p>Immutable: each instance may have a bound $this column for lambda evaluation.
 */
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

  /** Pathling flat schema: resource-level map from _fid to extension arrays. */
  private static final String EXTENSION_MAP_COLUMN = "_extension";

  /** Pathling flat schema: integer identity field present on every composite struct. */
  private static final String FID_COLUMN = "_fid";

  /** FHIR instant ISO-8601 UTC format, matching Pathling's {@code SqlFunctions.toFhirInstant}. */
  private static final String FHIR_INSTANT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  @Nullable private final Column thisColumn;

  @Nullable private final Column rootColumn;

  @Nonnull private final SparkOperationRegistry registry;

  /**
   * Creates a code generator with the given operation registry.
   *
   * @param registry the operation registry for dispatch
   */
  public SparkCodeGenerator(@Nonnull final SparkOperationRegistry registry) {
    this(null, null, registry);
  }

  /** Private constructor for creating instances with full configuration. */
  private SparkCodeGenerator(
      @Nullable final Column thisColumn,
      @Nullable final Column rootColumn,
      @Nonnull final SparkOperationRegistry registry) {
    this.thisColumn = thisColumn;
    this.rootColumn = rootColumn;
    this.registry = registry;
  }

  /**
   * Creates a new SparkCodeGenerator with {@code $this} bound to the specified column. Used during
   * lambda body evaluation to bind the current element.
   *
   * <p>Package-private because only {@link SparkOpContext#evaluateLambda} should call this — ops
   * classes use that higher-level method instead.
   *
   * @param thisColumn the column to bind as {@code $this}
   * @return a new generator with the given {@code $this} binding
   */
  @Nonnull
  SparkCodeGenerator withThisColumn(@Nonnull final Column thisColumn) {
    return new SparkCodeGenerator(thisColumn, this.rootColumn, this.registry);
  }

  /**
   * Creates a new SparkCodeGenerator with the root column bound to the specified column. When set,
   * resource-level field access uses {@code rootColumn.getField(fieldName)} instead of {@code
   * col(fieldName)}, enabling evaluation relative to a sub-element (e.g., a Spark lambda
   * parameter).
   *
   * @param root the column to use as the root for field access
   * @return a new generator with the root column bound
   */
  @Nonnull
  public SparkCodeGenerator withRootColumn(@Nullable final Column root) {
    return new SparkCodeGenerator(this.thisColumn, root, this.registry);
  }

  @Override
  @Nonnull
  public Column visitOperation(@Nonnull final Operation op) {
    // Recursively visit child arguments to generate their columns
    // Special case: don't evaluate Lambda nodes - they need to be handled specially
    final List<Column> argColumns =
        op.args().stream()
            .map(
                arg -> {
                  if (arg == null) return lit(null);
                  if (arg instanceof Lambda)
                    return null; // Lambda is passed as IR node, not evaluated
                  return arg.accept(this);
                })
            .toList();

    // Dispatch through registry
    final SparkOperationDef def = registry.get(op.name());
    if (def == null) {
      throw new UnsupportedOperationException(
          "Unknown operation: " + op.name() + " with result type: " + op.getType());
    }
    return def.generate(new SparkOpContext(argColumns, op.args(), op, this));
  }

  // ========== Infrastructure Nodes ==========

  @Override
  @Nonnull
  public Column visitLiteral(@Nonnull final Literal lit) {
    // Empty literal {} should be NULL, not an empty array.
    // When the type is known (e.g., NULL promoted to QUANTITY by overload resolver),
    // produce a typed null so struct field access works correctly downstream.
    if (lit.value() == null) {
      if (lit.type() == Types.NULL) {
        return lit(null);
      }
      final DataType sparkType = toSparkDataType(lit.getShape());
      return lit(null).cast(sparkType);
    }
    if (lit.value() instanceof QuantityValue qv) {
      return quantityStruct(
          lit(qv.value()).cast(SparkTypeMapper.DECIMAL_TYPE), qv.unit(), qv.system(), qv.code());
    }
    if (lit.value() instanceof CodingValue cv) {
      return codingStruct(cv);
    }
    final Object rawValue = lit.value() instanceof TemporalValue tv ? tv.value() : lit.value();
    final DataType sparkType = toSparkDataType(lit.getShape());
    return lit(rawValue).cast(sparkType);
  }

  @Override
  @Nonnull
  public Column visitTraversal(@Nonnull final Traversal trav) {
    // Special handling: extension field uses _extension[_fid] map lookup
    if ("extension".equals(trav.fieldSpec().getName())) {
      return visitExtensionTraversal(trav);
    }

    // Resolve the raw field access. For a resource-rooted traversal this is a top-level column
    // (or a getField on the current lambda root); otherwise it is getField on the target column.
    Column result;
    if (trav.target() instanceof Resource) {
      result =
          rootColumn != null
              ? rootColumn.getField(trav.fieldSpec().getName())
              : col(trav.fieldSpec().getName());
    } else {
      result = trav.target().accept(this).getField(trav.fieldSpec().getName());
      // Plural target: filter outer-array nulls, then flatten when the field is itself plural
      // (giving an array-of-arrays that must be concatenated). Mirrors Pathling's
      // DefaultRepresentation.traverse() → removeNulls().flatten().
      if (!trav.target().isSingular()) {
        result = functions.filter(result, Column::isNotNull);
        if (!trav.fieldSpec().isSingular()) {
          result = functions.flatten(result);
        }
      }
    }
    // Plural field: filter JSON positional-null gaps (FHIRPath collections cannot contain null,
    // per spec "Null and empty"). See issue #192. Combined with nullIfEmpty so an all-null input
    // collapses to null (FHIRPath empty = null in Spark); this also absorbs the post-flatten
    // null-if-empty for the plural-target case above.
    if (!trav.fieldSpec().isSingular()) {
      result = CollectionValue.nullIfEmpty(functions.filter(result, Column::isNotNull));
    } else if (!trav.target().isSingular()) {
      // Plural target, singular field: preserve the pre-#192 behaviour of collapsing an
      // empty filtered array to null.
      result = CollectionValue.nullIfEmpty(result);
    }

    // FHIR.instant is encoded by the Pathling encoder as Spark TimestampType, but fp2sql
    // represents DateTime values as ISO-8601 strings (see SparkTypeMapper). Bridge the encoding
    // by formatting Timestamp columns to an ISO-8601 UTC string at read time so downstream ops
    // (comparison, equality, conversion) operate on a consistent representation. Mirrors
    // Pathling's SqlFunctions.toFhirInstant / DateTimeCollection.asStringPath.
    result = coerceInstantToString(trav, result);
    return result;
  }

  /**
   * Converts a Spark TimestampType column (from FHIR.instant encoding) to an ISO-8601 UTC string.
   * Always produces UTC since Spark TimestampType does not preserve the original timezone.
   *
   * <p>For many-cardinality traversals, the column is an array so conversion is applied via {@code
   * transform}. Singular columns are converted directly.
   */
  @Nonnull
  private static Column coerceInstantToString(
      @Nonnull final Traversal trav, @Nonnull final Column column) {
    if (!(trav.fieldSpec().getType() instanceof FhirPrimitiveType fpt)
        || !"instant".equals(fpt.getFhirName())) {
      return column;
    }
    return trav.isSingular()
        ? formatInstant(column)
        : functions.transform(column, SparkCodeGenerator::formatInstant);
  }

  @Nonnull
  private static Column formatInstant(@Nonnull final Column timestamp) {
    return functions.date_format(
        functions.to_utc_timestamp(timestamp, functions.current_timezone()), FHIR_INSTANT_FORMAT);
  }

  /**
   * Generates code for extension traversal using the _extension[_fid] map lookup.
   *
   * <p>In Pathling's flat schema, extensions are stored in a resource-level {@code _extension} map
   * ({@code Map<Integer, Array<Extension>>}), keyed by {@code _fid} (an integer identity on every
   * composite struct). Accessing extensions requires {@code element_at(_extension, element._fid)}
   * rather than a normal field access.
   */
  @Nonnull
  private Column visitExtensionTraversal(@Nonnull final Traversal trav) {
    // _extension is always a resource-level column in Pathling's flat schema, not a field
    // of the target element. It is a Map<Integer, Array<Extension>> keyed by _fid.
    final Column extensionMap = col(EXTENSION_MAP_COLUMN);

    if (trav.target() instanceof Resource) {
      // When rootColumn is set, _fid access is relative to the root column. The extension map
      // is always resource-level (col(_extension)), regardless of root binding.
      final Column fid = rootColumn != null ? rootColumn.getField(FID_COLUMN) : col(FID_COLUMN);
      return functions.element_at(extensionMap, fid);
    }

    final Column target = trav.target().accept(this);
    return new CollectionValue(target, trav.target().isSingular())
        .map(elem -> functions.element_at(extensionMap, elem.getField(FID_COLUMN)))
        .filterNulls()
        .flatten()
        .column();
  }

  @Override
  @Nonnull
  public Column visitCast(@Nonnull final Cast cast) {
    final Column childColumn = cast.child().accept(this);
    // FhirPrimitiveType → System type cast is identity (same Spark storage format)
    if (cast.child().getType() instanceof FhirPrimitiveType) {
      return childColumn;
    }
    if (cast.targetType() == Types.QUANTITY) {
      // INTEGER/DECIMAL → QUANTITY: wrap in struct with default unit '1'
      return quantityStruct(
          childColumn.cast(SparkTypeMapper.DECIMAL_TYPE),
          QuantityValue.DEFAULT_UNIT,
          QuantityValue.UCUM_SYSTEM,
          QuantityValue.DEFAULT_UNIT);
    }
    final DataType sparkType = toSparkDataType(cast.getShape());
    return childColumn.cast(sparkType);
  }

  @Override
  @Nonnull
  public Column visitResource(@Nonnull final Resource res) {
    // Sentinel: lit(true) represents resource existence (always non-null for known types),
    // making count()/exists() work naturally. Matches Pathling's ResourceRepresentation.
    return res.type() != InlineResourceType.EMPTY ? lit(true) : lit(null);
  }

  @Override
  @Nonnull
  public Column visitLambda(@Nonnull final Lambda lambda) {
    throw new UnsupportedOperationException(
        "Lambdas cannot be evaluated directly - they must be inlined at their call site");
  }

  @Override
  @Nonnull
  public Column visitThisReference(@Nonnull final ThisReference thisRef) {
    if (thisColumn == null) {
      throw new UnsupportedOperationException(
          "$this cannot be evaluated outside of a lambda context");
    }
    return thisColumn;
  }

  /** Builds a Quantity struct column with named fields matching {@code QUANTITY_TYPE} schema. */
  @Nonnull
  private static Column quantityStruct(
      @Nonnull final Column value,
      @Nonnull final String unit,
      @Nonnull final String system,
      @Nonnull final String code) {
    return functions.struct(
        value.as("value"), lit(unit).as("unit"), lit(system).as("system"), lit(code).as("code"));
  }

  /** Builds a Coding struct column with named fields matching {@code CODING_TYPE} schema. */
  @Nonnull
  private static Column codingStruct(@Nonnull final CodingValue cv) {
    return functions.struct(
        lit(cv.system()).as("system"),
        lit(cv.code()).as("code"),
        lit(cv.version()).cast(DataTypes.StringType).as("version"),
        lit(cv.display()).cast(DataTypes.StringType).as("display"),
        lit(cv.userSelected()).cast(DataTypes.BooleanType).as("userSelected"));
  }
}
