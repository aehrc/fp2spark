package com.example.fhirpath.test;

import com.example.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;

import java.util.Map;

/**
 * Encapsulates resource test data as a Map-based structure.
 *
 * <p>This class holds:
 * <ul>
 *   <li>Resource type name (e.g., "Patient")</li>
 *   <li>Test data as a {@code Map<String, Object>} structure</li>
 * </ul>
 *
 * <p>The Map data can contain:
 * <ul>
 *   <li>Primitives (String, Integer, Double, Boolean)</li>
 *   <li>Lists (for array fields)</li>
 *   <li>Nested Maps (for complex type fields)</li>
 * </ul>
 *
 * <p><b>Usage example:</b>
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

    /**
     * Create resource test data.
     *
     * @param resourceTypeName The name of the resource type (e.g., "Patient")
     * @param data             The test data as a Map structure
     */
    private ResourceTestData(
            @Nonnull final String resourceTypeName,
            @Nonnull final Map<String, Object> data
    ) {
        this.resourceTypeName = resourceTypeName;
        this.data = data;
    }

    /**
     * Create resource test data from resource type name and Map data.
     *
     * @param resourceTypeName The name of the resource type
     * @param data             The test data as a Map structure
     * @return ResourceTestData instance
     */
    @Nonnull
    public static ResourceTestData of(
            @Nonnull final String resourceTypeName,
            @Nonnull final Map<String, Object> data
    ) {
        return new ResourceTestData(resourceTypeName, data);
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
     * Infer and return the ResourceType from the data structure.
     *
     * <p>This method uses {@link ResourceTypeInference} to analyze the Map data
     * and derive field specifications (name, type, cardinality) to construct
     * a complete ResourceType definition.
     *
     * @return The inferred ResourceType
     */
    @Nonnull
    public ResourceType inferResourceType() {
        return ResourceTypeInference.infer(resourceTypeName, data);
    }
}
