package com.example.fhirpath.test;

import com.example.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Test subject backed by a HAPI FHIR resource object. Delegates encoder / definition work to {@link
 * FhirTestEncoders} so the YAML reference suite can share the same HAPI state.
 */
record HapiTestSubject(@Nonnull IBaseResource resource) implements TestSubject {

  @Override
  @Nonnull
  public String getResourceTypeName() {
    return FhirTestEncoders.definitionOf(resource).getName();
  }

  @Override
  @Nonnull
  public ResourceType getResourceType() {
    return FhirTestEncoders.resourceTypeOf(resource);
  }

  @Override
  @Nonnull
  public Dataset<Row> toDataset(@Nonnull final SparkSession spark) {
    return FhirTestEncoders.toDataset(spark, resource);
  }
}
