package com.example.fhirpath.typing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for equals/hashCode on complex type implementations. */
class ComplexTypeEqualityTest {

  private static final FhirContext CTX = FhirContext.forR4Cached();

  // --- FhirComplexType ---

  @Test
  void sameDefinitionYieldsEqualTypes() {
    final FhirComplexType a = humanNameType();
    final FhirComplexType b = humanNameType();
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void differentDefinitionsAreNotEqual() {
    assertNotEquals(humanNameType(), addressType());
  }

  @Test
  @SuppressWarnings("java:S5863") // deliberate reflexivity check on equals
  void reflexivity() {
    final FhirComplexType a = humanNameType();
    assertEquals(a, a);
  }

  @Test
  void nullSafety() {
    assertNotEquals(humanNameType(), null);
  }

  @Test
  void crossClassNotEqual() {
    assertNotEquals(humanNameType(), "HumanName");
  }

  // --- FhirResourceType ---

  @Test
  void sameResourceTypeEqual() {
    final FhirResourceType a = resourceType("Patient");
    final FhirResourceType b = resourceType("Patient");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void differentResourceTypesNotEqual() {
    assertNotEquals(resourceType("Patient"), resourceType("Observation"));
  }

  @Test
  void resourceTypeNotEqualToComplexType() {
    // Even if names could overlap, getClass() check prevents cross-hierarchy equality
    final FhirResourceType resource = resourceType("Patient");
    final FhirComplexType complex = humanNameType();
    assertNotEquals(resource, complex);
    assertNotEquals(complex, resource);
  }

  // --- InlineComplexType ---

  @Test
  void sameNameInlineTypesEqual() {
    final InlineComplexType a = new InlineComplexType("TestType");
    final InlineComplexType b = new InlineComplexType("TestType");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void differentNameInlineTypesNotEqual() {
    assertNotEquals(new InlineComplexType("TypeA"), new InlineComplexType("TypeB"));
  }

  @Test
  void inlineResourceTypeNotEqualToInlineComplexType() {
    final InlineComplexType complex = new InlineComplexType("Foo");
    final InlineResourceType resource = new InlineResourceType("Foo", List.of());
    assertNotEquals(complex, resource);
    assertNotEquals(resource, complex);
  }

  // --- Helpers ---

  private static FhirComplexType humanNameType() {
    final RuntimeResourceDefinition patientDef = CTX.getResourceDefinition("Patient");
    final FhirResourceType patient = new FhirResourceType(patientDef);
    return (FhirComplexType) patient.resolveField("name").orElseThrow().getType();
  }

  private static FhirComplexType addressType() {
    final RuntimeResourceDefinition patientDef = CTX.getResourceDefinition("Patient");
    final FhirResourceType patient = new FhirResourceType(patientDef);
    return (FhirComplexType) patient.resolveField("address").orElseThrow().getType();
  }

  private static FhirResourceType resourceType(final String name) {
    return new FhirResourceType(CTX.getResourceDefinition(name));
  }
}
