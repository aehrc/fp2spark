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
import java.util.Map;

/**
 * Encapsulates resource test data as a Map-based structure.
 *
 * <p>This class holds:
 *
 * <ul>
 *   <li>Resource type name (e.g., "Patient")
 *   <li>Test data as a {@code Map<String, Object>} structure
 * </ul>
 *
 * <p>The Map data can contain:
 *
 * <ul>
 *   <li>Primitives (String, Integer, Double, Boolean)
 *   <li>Lists (for array fields)
 *   <li>Nested Maps (for complex type fields)
 * </ul>
 *
 * <p><b>Usage example:</b>
 *
 * <pre>{@code
 * ResourceTestData patient = ResourceTestData.of("Patient",
 *     new ResourceDataBuilder()
 *         .string("id", "patient-1")
 *         .integer("age", 30)
 *         .element("name", n -> n.string("family", "Smith"))
 *         .build()
 * );
 * }</pre>
 */
public class ResourceTestData {

  private final String resourceTypeName;
  private final Map<String, Object> data;
  private final ResourceType explicitResourceType;

  /**
   * Create resource test data.
   *
   * @param resourceTypeName The name of the resource type (e.g., "Patient")
   * @param data The test data as a Map structure
   * @param explicitResourceType Optional explicit ResourceType (null for inferred)
   */
  private ResourceTestData(
      @Nonnull final String resourceTypeName,
      @Nonnull final Map<String, Object> data,
      @jakarta.annotation.Nullable final ResourceType explicitResourceType) {
    this.resourceTypeName = resourceTypeName;
    this.data = data;
    this.explicitResourceType = explicitResourceType;
  }

  /**
   * Create resource test data from resource type name and Map data.
   *
   * @param resourceTypeName The name of the resource type
   * @param data The test data as a Map structure
   * @return ResourceTestData instance
   */
  @Nonnull
  public static ResourceTestData of(
      @Nonnull final String resourceTypeName, @Nonnull final Map<String, Object> data) {
    return new ResourceTestData(resourceTypeName, data, null);
  }

  /**
   * Create resource test data with an explicit ResourceType.
   *
   * @param resourceType The explicit resource type definition
   * @param data The test data as a Map structure
   * @return ResourceTestData instance
   */
  @Nonnull
  public static ResourceTestData of(
      @Nonnull final ResourceType resourceType, @Nonnull final Map<String, Object> data) {
    return new ResourceTestData(resourceType.getResourceName(), data, resourceType);
  }

  /**
   * Get the resource type name.
   *
   * @return The resource type name
   */
  @Nonnull
  public String getResourceTypeName() {
    return resourceTypeName;
  }

  /**
   * Get the test data as a Map.
   *
   * @return The Map containing test data
   */
  @Nonnull
  public Map<String, Object> getData() {
    return data;
  }

  /**
   * Returns the ResourceType — either the explicit one if provided, or inferred from data.
   *
   * @return The ResourceType
   */
  @Nonnull
  public ResourceType inferResourceType() {
    if (explicitResourceType != null) {
      return explicitResourceType;
    }
    return ResourceTypeInference.infer(resourceTypeName, data);
  }
}
