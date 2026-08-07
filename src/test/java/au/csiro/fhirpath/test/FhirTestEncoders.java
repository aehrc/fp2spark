package au.csiro.fhirpath.test;

import au.csiro.fhirpath.typing.FhirResourceType;
import au.csiro.pathling.encoders.FhirEncoders;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import jakarta.annotation.Nonnull;
import java.util.List;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Shared HAPI / Pathling-encoder state used by test subjects backed by real FHIR resources.
 *
 * <p>Both {@link HapiTestSubject} and the YAML reference-suite subject factory need the same
 * singleton {@link FhirContext} / {@link FhirEncoders} to produce a Spark row from a HAPI resource
 * — extensions + standard open types are enabled so tests can reach {@code _extension} / {@code
 * _fid} columns and {@code value[x]} choice fields.
 */
public final class FhirTestEncoders {

  public static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();

  public static final FhirEncoders ENCODERS =
      FhirEncoders.forR4()
          .withExtensionsEnabled(true)
          .withOpenTypes(FhirEncoders.STANDARD_OPEN_TYPES)
          .getOrCreate();

  private FhirTestEncoders() {}

  /** Builds a single-row Spark dataset for {@code resource} using the shared encoder. */
  @Nonnull
  public static Dataset<Row> toDataset(
      @Nonnull final SparkSession spark, @Nonnull final IBaseResource resource) {
    @SuppressWarnings("unchecked")
    final var encoder = ENCODERS.of((Class<IBaseResource>) resource.getClass());
    return spark.createDataset(List.of(resource), encoder).toDF();
  }

  /** Returns the HAPI runtime definition for {@code resource}. */
  @Nonnull
  public static RuntimeResourceDefinition definitionOf(@Nonnull final IBaseResource resource) {
    return FHIR_CONTEXT.getResourceDefinition(resource);
  }

  /** Returns a {@link FhirResourceType} wrapping the HAPI runtime definition. */
  @Nonnull
  public static FhirResourceType resourceTypeOf(@Nonnull final IBaseResource resource) {
    return new FhirResourceType(definitionOf(resource));
  }
}
