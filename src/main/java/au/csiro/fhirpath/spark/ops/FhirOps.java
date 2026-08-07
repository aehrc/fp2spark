/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.spark.ops;

import static au.csiro.fhirpath.spark.SparkDefs.unary;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.regexp_extract;
import static org.apache.spark.sql.functions.when;

import au.csiro.fhirpath.ir.Resource;
import au.csiro.fhirpath.spark.CollectionValue;
import au.csiro.fhirpath.spark.SparkOperationRegistry;
import org.apache.spark.sql.Column;

/**
 * FHIR-specific function registrations (getValue, hasValue, getResourceKey, getReferenceKey,
 * resolve).
 *
 * <p>These functions are defined in the FHIR-specific FHIRPath binding and the SQL on FHIR v2
 * specification.
 */
public final class FhirOps {

  /** Pathling flat schema: resource logical id column. */
  private static final String RESOURCE_ID_COLUMN = "id";

  /**
   * Regex pattern for extracting resource type from reference strings. Matches resource types in
   * relative ("Patient/123"), absolute ("http://example.org/fhir/Patient/123"), and canonical
   * ("http://hl7.org/fhir/ValueSet/my-valueset|1.0") reference formats.
   */
  private static final String REFERENCE_TYPE_PATTERN = "(?:^|/)([A-Z][a-zA-Z]+)(?:/|$|\\|)";

  /** Regex pattern for validating FHIR resource type names. */
  private static final String FHIR_TYPE_NAME_PATTERN = "^[A-Z][a-zA-Z]+$";

  private FhirOps() {}

  /**
   * Registers all FHIR-specific functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    // getValue() — returns the value of a FHIR primitive (identity for our encoding)
    registry.register("getValue", unary(c -> c));

    // hasValue() — returns true if the element has a value (non-null check)
    registry.register("hasValue", unary(c -> c.isNotNull()));

    // getResourceKey() — returns "ResourceType/id" for SQL on FHIR joins
    registry.register(
        "getResourceKey",
        ctx -> {
          final Resource resource = ctx.resourceArg(0);
          final String resourceName = resource.type().getResourceName();
          return concat(lit(resourceName + "/"), col(RESOURCE_ID_COLUMN));
        });

    // getReferenceKey([type]) — returns reference string, optionally filtered by type
    registry.register(
        "getReferenceKey",
        ctx -> {
          final CollectionValue ref = ctx.collectionArg(0);
          if (ctx.args().size() <= 1) {
            return ref.map(r -> r.getField("reference")).column();
          }
          // With type filter: extract reference only if it matches "Type/..."
          final String typePrefix = ctx.literalArg(1).value() + "/";
          return ref.map(
                  r -> {
                    final org.apache.spark.sql.Column refField = r.getField("reference");
                    return when(refField.startsWith(typePrefix), refField);
                  })
              .filterNulls()
              .column();
        });

    // resolve() — extracts type information from Reference elements
    registry.register(
        "resolve",
        ctx -> {
          final CollectionValue ref = ctx.collectionArg(0);
          return ref.map(FhirOps::extractTypeFromReference).filterNulls().column();
        });
  }

  /**
   * Extracts resource type from a Reference struct element.
   *
   * <p>Uses {@code coalesce(Reference.type, regexp_extract(Reference.reference, pattern))} with
   * validation that the result is a valid FHIR resource type name. Following Pathling's approach.
   *
   * @param refStruct a column representing a Reference struct element
   * @return a column containing the extracted type string, or null if unresolvable
   */
  private static Column extractTypeFromReference(final Column refStruct) {
    final Column referenceField = refStruct.getField("reference");
    final Column typeField = refStruct.getField("type");
    // regexp_extract returns "" on no-match (not null); normalize to null before coalescing
    final Column parsedType = regexp_extract(referenceField, REFERENCE_TYPE_PATTERN, 1);
    final Column parsedTypeOrNull = when(parsedType.notEqual(lit("")), parsedType);
    final Column extractedType = coalesce(typeField, parsedTypeOrNull);
    // Validate type field values (parsedType is already constrained by the regex capture group)
    final Column isValid =
        extractedType.isNotNull().and(extractedType.rlike(FHIR_TYPE_NAME_PATTERN));
    return when(isValid, extractedType).otherwise(lit(null));
  }
}
