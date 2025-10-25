package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static com.example.fhirpath.test.FhirPathTestBuilder.context;

/**
 * Tests for FHIRPath expressions that operate on resource data.
 *
 * <p>Ported from {@link FhirPathIntegrationTest#testFhirPathExpressionsWithResource()}.
 * Tests complex resource structures including nested elements and arrays.
 */
class ResourceExpressionsTest extends FhirPathTestBase {

    @TestFactory
    Stream<DynamicTest> testSimpleResourceFields() {
        return builder()
                .group("Simple resource fields")
                .withSubject("Patient", p -> p
                        .string("id", "id1")
                        .integer("age", 55)
                        .string("gender", "male")
                        .string("value", "344.1000")
                        .elementArray("name",
                                n -> n
                                        .string("family", "Szul")
                                        .stringArray("given", "Piotr", "Jaroslaw")
                                        .string("use", "official"),
                                n -> n
                                        .string("family", "Brown")
                                        .stringArray("given", "John", "Mark")
                                        .string("use", "alias"),
                                n -> {}  // Empty name element
                        )
                )
                .testEquals("id1", "id")
                .testEquals(55, "age")
                .testEquals("male", "gender")
                .testEquals("344.1000", "value")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNestedArrayFields() {
        return builder()
                .group("Nested array fields")
                .withSubject("Patient", p -> p
                        .string("id", "id1")
                        .elementArray("name",
                                n -> n
                                        .string("family", "Szul")
                                        .stringArray("given", "Piotr", "Jaroslaw")
                                        .string("use", "official"),
                                n -> n
                                        .string("family", "Brown")
                                        .stringArray("given", "John", "Mark")
                                        .string("use", "alias"),
                                n -> {}  // Empty name element
                        )
                )
                .testEquals("Szul", "name.first().family")
                // TODO: Port "%context.name.use" test from original (line 230)
                // Requires implicit resource-as-context support
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testResourceWithContext() {
        return builder()
                .group("Resource with context")
                .withSubject("Patient", p -> p
                        .string("gender", "male")
                        .elementArray("name",
                                n -> n.string("use", "official"),
                                n -> n.string("use", "alias")
                        )
                )
                .testEquals(1, "%resource.count()")
                .testTrue("exists()")
                // TODO: Add support for implicit resource-as-context (line 230, 233 from original test)
                // The original test uses %context to refer to the Patient resource itself
                // Currently our harness requires explicit context() which doesn't match this pattern
                // .testTrue("%context.gender = %resource.gender")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testArithmeticOnResourceFields() {
        return builder()
                .group("Arithmetic on resource fields")
                .withSubject("Patient", p -> p
                        .integer("age", 55)
                )
                .testEquals(65, "age + 10")
                .testEquals(65.3, "10.3 + age")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testComparisonOnResourceFields() {
        return builder()
                .group("Comparison on resource fields")
                .withSubject("Patient", p -> p
                        .string("gender", "male")
                )
                .testTrue("gender = 'male'")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testWhereOnResourceFields() {
        return builder()
                .group("where() on resource fields")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n
                                        .string("family", "Szul")
                                        .stringArray("given", "Piotr", "Jaroslaw")
                                        .string("use", "official"),
                                n -> n
                                        .string("family", "Brown")
                                        .stringArray("given", "John", "Mark")
                                        .string("use", "alias"),
                                n -> {}  // Empty name element
                        )
                )
                // Basic filtering with equality
                .testEquals("Szul", "name.where(use = 'official').family.first()")
                .testEquals("Brown", "name.where(use = 'alias').family.first()")
                // Filtering that returns empty when no match
                .testFalse("name.where(use = 'nonexistent').exists()")
                .testEquals(0, "name.where(family = 'Unknown').count()")
                // where() with implicit $this in criteria
                .testEquals("Piotr", "name.where(family = 'Szul').given.first().first()")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testWhereWithExplicitThis() {
        return builder()
                .group("where() with explicit $this")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n.stringArray("given", "Piotr", "Jaroslaw"),
                                n -> n.stringArray("given", "John", "Mark")
                        )
                )
                .testEquals("John", "name.given.where($this = 'John').first()")
                .testEquals("Piotr", "name.given.where($this > 'K').first()")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testChainedWhere() {
        return builder()
                .group("Chained where() clauses")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n
                                        .string("family", "Szul")
                                        .string("use", "official"),
                                n -> n
                                        .string("family", "Brown")
                                        .string("use", "alias")
                        )
                )
                .testTrue("name.where(use = 'official').where(family = 'Szul').exists()")
                .testFalse("name.where(use = 'official').where(family = 'Brown').exists()")
                // where() on empty input collection returns empty
                .testEquals(0, "name.where(family = 'Unknown').where(use = 'official').count()")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testWhereWithNestedFieldAccess() {
        return builder()
                .group("where() with nested field access")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n
                                        .string("family", "Szul")
                                        .stringArray("given", "Piotr", "Jaroslaw"),
                                n -> n
                                        .string("family", "Brown")
                                        .stringArray("given", "John", "Mark")
                        )
                )
                .testEquals(2, "name.where(given.exists()).count()")
                .testEquals("Szul", "name.where(given.count() > 1).family.first()")
                // Nested where() with implicit $this in both levels
                .testEquals("Szul", "name.where(given.where($this = 'Piotr').exists()).family.first()")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testWherePreservesCollectionStructure() {
        return builder()
                .group("where() preserves collection structure")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n.string("use", "official"),
                                n -> n.string("use", "alias")
                        )
                )
                .testEquals(1, "name.where(use = 'official').count()")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testWhereWithComparisonOperators() {
        return builder()
                .group("where() with comparison operators")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n.stringArray("given", "Piotr", "Jaroslaw"),
                                n -> n.stringArray("given", "John", "Mark")
                        )
                )
                .testEquals("Jaroslaw", "name.given.where($this < 'K').first()")
                .testEquals(3, "name.given.where($this >= 'John').count()")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testExistsWithCriteria() {
        return builder()
                .group("exists(criteria) function")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n
                                        .string("family", "Szul")
                                        .stringArray("given", "Piotr", "Jaroslaw")
                                        .string("use", "official"),
                                n -> n
                                        .string("family", "Brown")
                                        .string("use", "alias")
                        )
                )
                // exists(criteria) with equality
                .testTrue("name.exists(use = 'official')")
                .testFalse("name.exists(use = 'nonexistent')")
                .testTrue("name.exists(family = 'Szul')")
                .testFalse("name.exists(family = 'Unknown')")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testExistsWithComparisonOperators() {
        return builder()
                .group("exists(criteria) with comparison operators")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n.stringArray("given", "Piotr", "Jaroslaw"),
                                n -> n.stringArray("given", "John", "Mark")
                        )
                )
                .testTrue("name.given.exists($this > 'K')")
                .testFalse("name.given.exists($this > 'Z')")
                .testTrue("name.given.exists($this = 'Piotr')")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testExistsOnEmptyCollection() {
        return builder()
                .group("exists(criteria) on empty collection")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n.string("family", "Szul")
                        )
                )
                .testFalse("name.where(family = 'Unknown').exists(use = 'official')")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNestedExists() {
        return builder()
                .group("Nested exists(criteria)")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n.stringArray("given", "Piotr", "Jaroslaw"),
                                n -> n.stringArray("given", "John", "Mark")
                        )
                )
                .testTrue("name.exists(given.exists())")
                .testTrue("name.exists(given.count() > 1)")
                .testTrue("name.given.exists($this = 'John')")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFirstOnResourceFields() {
        return builder()
                .group("first() on resource fields")
                .withSubject("Patient", p -> p
                        .elementArray("name",
                                n -> n
                                        .string("family", "Szul")
                                        .stringArray("given", "Piotr", "Jaroslaw"),
                                n -> n
                                        .string("family", "Brown")
                                        .stringArray("given", "John", "Mark")
                        )
                )
                .testEquals("Szul", "name.first().family")
                // Chaining first() on nested collections
                .testEquals("Piotr", "name.first().given.first()")
                .build();
    }
}
