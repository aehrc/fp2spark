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
package au.csiro.fhirpath.test;

import au.csiro.fhirpath.typing.ResourceType;
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
