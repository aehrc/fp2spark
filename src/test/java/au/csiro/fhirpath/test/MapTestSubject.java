package au.csiro.fhirpath.test;

import au.csiro.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * Test subject backed by Map-based test data.
 *
 * <p>Wraps existing {@link ResourceTestData} and {@link ResourceDatasetConverter} infrastructure,
 * preserving all existing test behavior.
 */
record MapTestSubject(@Nonnull ResourceTestData resourceTestData) implements TestSubject {

  @Override
  @Nonnull
  public String getResourceTypeName() {
    return resourceTestData.getResourceTypeName();
  }

  @Override
  @Nonnull
  public ResourceType getResourceType() {
    return resourceTestData.inferResourceType();
  }

  @Override
  @Nonnull
  public Dataset<Row> toDataset(@Nonnull final SparkSession spark) {
    return ResourceDatasetConverter.toDataset(spark, resourceTestData);
  }
}
