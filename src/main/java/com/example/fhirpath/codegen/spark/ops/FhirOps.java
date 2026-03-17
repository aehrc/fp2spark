package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.ir.Literal;
import com.example.fhirpath.ir.Resource;

/**
 * FHIR-specific function registrations (getValue, hasValue, getResourceKey, getReferenceKey).
 *
 * <p>These functions are defined in the FHIR-specific FHIRPath binding and the SQL on FHIR v2
 * specification.
 */
public final class FhirOps {

  private FhirOps() {}

  /**
   * Registers all FHIR-specific functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    // getValue() — returns the value of a FHIR primitive (identity for our encoding)
    registry.unary("getValue", c -> c);

    // hasValue() — returns true if the element has a value (non-null check)
    registry.unary("hasValue", c -> c.isNotNull());

    // getResourceKey() — returns "ResourceType/id" for SQL on FHIR joins
    registry.register(
        "getResourceKey",
        ctx -> {
          final Resource resource = (Resource) ctx.argNode(0);
          final String resourceName = resource.type().getResourceName();
          return concat(lit(resourceName + "/"), col("id"));
        });

    // getReferenceKey([type]) — returns reference string, optionally filtered by type
    registry.register(
        "getReferenceKey",
        ctx -> {
          final CollectionValue ref = ctx.collectionArg(0);
          if (ctx.args().size() <= 1 || ctx.argNode(1) instanceof Literal l && l.value() == null) {
            // No type filter: extract the reference field
            return ref.map(r -> r.getField("reference")).column();
          }
          // With type filter: extract reference only if it matches "Type/..."
          final String typeSpec = (String) ((Literal) ctx.argNode(1)).value();
          return ref.map(
                  r -> {
                    final org.apache.spark.sql.Column refField = r.getField("reference");
                    return when(refField.startsWith(typeSpec + "/"), refField);
                  })
              .filterNulls()
              .column();
        });
  }
}
