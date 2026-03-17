package com.example.fhirpath.test;

import au.csiro.pathling.encoders.FhirEncoders;
import ca.uhn.fhir.context.FhirContext;
import com.example.fhirpath.typing.FhirResourceType;
import com.example.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;
import java.util.List;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Test subject backed by a HAPI FHIR resource object.
 *
 * <p>Uses Pathling's {@link FhirEncoders} to create a Spark Dataset with a complete FHIR schema
 * (all fields present, even when null). Uses {@link FhirResourceType} for type resolution.
 */
record HapiTestSubject(@Nonnull IBaseResource resource) implements TestSubject {

  private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();
  private static final FhirEncoders FHIR_ENCODERS = FhirEncoders.forR4().getOrCreate();

  @Override
  @Nonnull
  public String getResourceTypeName() {
    return FHIR_CONTEXT.getResourceDefinition(resource).getName();
  }

  @Override
  @Nonnull
  public ResourceType getResourceType() {
    return new FhirResourceType(FHIR_CONTEXT.getResourceDefinition(resource));
  }

  @Override
  @Nonnull
  public Dataset<Row> toDataset(@Nonnull final SparkSession spark) {
    // Use Pathling encoders for complete schema (all fields present, nulls for absent)
    // Safe cast: resource.getClass() is always a concrete IBaseResource subtype
    @SuppressWarnings("unchecked")
    final var encoder = FHIR_ENCODERS.of((Class<IBaseResource>) resource.getClass());
    final Dataset<Row> flat = spark.createDataset(List.of(resource), encoder).toDF();

    // Flat schema: return dataset directly (Pathling encoders already produce flat columns)
    return flat;
  }
}
