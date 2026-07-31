package com.example.fhirpath.codegen.spark.udf;

import com.example.fhirpath.terminology.TerminologyService;
import com.example.fhirpath.terminology.TerminologyServiceFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.Serial;
import org.apache.spark.sql.api.java.UDF4;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;
import org.hl7.fhir.r4.model.Coding;

/**
 * Spark UDF backing the FHIRPath {@code memberOf()} function for a single coding.
 *
 * <p>The coding is passed as its constituent {@code system}, {@code code} and {@code version}
 * strings rather than as a struct, so that the UDF makes no assumptions about the storage schema
 * and needs no Row decoding. Iterating the codings of a {@code CodeableConcept} is left to Spark's
 * {@code transform}/{@code exists} — see {@code TerminologyOps}.
 *
 * <p>The result is deliberately three-valued: {@code null} means the value set could not be
 * resolved, which the FHIR FHIRPath specification maps to an empty result.
 */
public final class MemberOf implements UDF4<String, String, String, String, Boolean> {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * The terminology service factory. Serialized to executors, where {@link
   * TerminologyServiceFactory#build()} yields a per-JVM service.
   */
  @Nonnull private final TerminologyServiceFactory terminologyServiceFactory;

  private MemberOf(@Nonnull final TerminologyServiceFactory terminologyServiceFactory) {
    this.terminologyServiceFactory = terminologyServiceFactory;
  }

  /**
   * Builds a Spark UDF that tests a single coding for membership of a value set.
   *
   * <p>The UDF takes {@code (system, code, version, valueSetUrl)} and returns a nullable Boolean.
   *
   * @param terminologyServiceFactory the factory used to obtain a terminology service on executors
   * @return a user-defined function usable in Spark expressions
   */
  @Nonnull
  public static UserDefinedFunction udf(
      @Nonnull final TerminologyServiceFactory terminologyServiceFactory) {
    return functions.udf(new MemberOf(terminologyServiceFactory), DataTypes.BooleanType);
  }

  @Nullable
  @Override
  public Boolean call(
      @Nullable final String system,
      @Nullable final String code,
      @Nullable final String version,
      @Nullable final String valueSetUrl) {
    if (valueSetUrl == null) {
      // An empty value set argument yields an empty result.
      return null;
    }
    if (system == null || code == null) {
      // A coding that does not identify a concept cannot be a member of anything. Matches
      // Pathling, which filters incomplete codings out before testing membership.
      return false;
    }
    final TerminologyService terminologyService = terminologyServiceFactory.build();
    return terminologyService.validateCode(
        valueSetUrl, new Coding().setSystem(system).setCode(code).setVersion(version));
  }
}
