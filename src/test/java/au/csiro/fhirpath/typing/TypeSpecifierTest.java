package au.csiro.fhirpath.typing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TypeSpecifier} — namespace resolution, type matching, and FHIR variant mapping.
 */
class TypeSpecifierTest {

  @Nested
  class FromExpression {

    @Test
    void qualifiedFhirPrimitive() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.string");
      assertTrue(ts.isFhirType());
      assertEquals("string", ts.getTypeName());
    }

    @Test
    void qualifiedFhirComplex() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.Quantity");
      assertTrue(ts.isFhirType());
      assertEquals("Quantity", ts.getTypeName());
    }

    @Test
    void qualifiedFhirResource() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.Patient");
      assertTrue(ts.isFhirType());
      assertEquals("Patient", ts.getTypeName());
    }

    @Test
    void qualifiedSystemType() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.String");
      assertTrue(ts.isSystemType());
      assertEquals("String", ts.getTypeName());
    }

    @Test
    void qualifiedSystemDateTime() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.DateTime");
      assertTrue(ts.isSystemType());
      assertEquals("DateTime", ts.getTypeName());
    }

    @Test
    void unqualifiedCapitalizedResolvesToSystem() {
      // "String" is a valid System type. Per fhirpath.js (#188), bare names resolve to System
      // when possible before falling through to FHIR.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("String");
      assertTrue(ts.isSystemType());
      assertEquals("String", ts.getTypeName());
    }

    @Test
    void unqualifiedLowercaseResolvesToFhir() {
      // "string" is not a valid System type (System uses capitalized names), so the search
      // falls through to FHIR.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("string");
      assertTrue(ts.isFhirType());
      assertEquals("string", ts.getTypeName());
    }

    @Test
    void unqualifiedQuantityResolvesToSystem() {
      // "Quantity" exists in both System and FHIR. Per fhirpath.js (#188), bare names default
      // to System.* — (1 year).is(Quantity) is true because bare Quantity ≡ System.Quantity.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("Quantity");
      assertTrue(ts.isSystemType());
      assertEquals("Quantity", ts.getTypeName());
    }

    @Test
    void unqualifiedCodingResolvesToSystem() {
      // "Coding" also exists in both namespaces; System wins for bare names.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("Coding");
      assertTrue(ts.isSystemType());
      assertEquals("Coding", ts.getTypeName());
    }

    @Test
    void unqualifiedComplexTypeResolvesToFhir() {
      // "HumanName" is not a System type, so it resolves to FHIR.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("HumanName");
      assertTrue(ts.isFhirType());
      assertEquals("HumanName", ts.getTypeName());
    }

    @Test
    void invalidSystemTypeThrows() {
      assertThrows(
          IllegalArgumentException.class, () -> TypeSpecifier.fromExpression("System.HumanName"));
    }

    @Test
    void invalidFhirTypeThrows() {
      assertThrows(
          IllegalArgumentException.class, () -> TypeSpecifier.fromExpression("FHIR.NotAType"));
    }

    @Test
    void unknownUnqualifiedTypeThrows() {
      assertThrows(IllegalArgumentException.class, () -> TypeSpecifier.fromExpression("NotAType"));
    }

    @Test
    void invalidNamespaceThrows() {
      assertThrows(
          IllegalArgumentException.class, () -> TypeSpecifier.fromExpression("Unknown.String"));
    }
  }

  @Nested
  class MatchesType {

    @Test
    void systemStringMatchesPrimitiveString() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.String");
      assertTrue(ts.matchesType(SystemType.STRING));
    }

    @Test
    void systemIntegerDoesNotMatchPrimitiveString() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.Integer");
      assertFalse(ts.matchesType(SystemType.STRING));
    }

    @Test
    void systemStringDoesNotMatchFhirPrimitiveString() {
      // Per fhirpath.js (#188), System.* and FHIR.* are disjoint — strict namespace match.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.String");
      assertFalse(ts.matchesType(FhirPrimitiveType.of("string")));
    }

    @Test
    void systemStringDoesNotMatchFhirPrimitiveUri() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.String");
      assertFalse(ts.matchesType(FhirPrimitiveType.of("uri")));
    }

    @Test
    void fhirStringMatchesFhirPrimitiveString() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.string");
      assertTrue(ts.matchesType(FhirPrimitiveType.of("string")));
    }

    @Test
    void fhirStringDoesNotMatchFhirPrimitiveBoolean() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.string");
      assertFalse(ts.matchesType(FhirPrimitiveType.of("boolean")));
    }

    @Test
    void fhirBooleanDoesNotMatchSystemBoolean() {
      // Per fhirpath.js (#188), FHIR.boolean ≠ SystemType.BOOLEAN. Namespaces are disjoint.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.boolean");
      assertFalse(ts.matchesType(SystemType.BOOLEAN));
    }

    @Test
    void fhirDecimalDoesNotMatchSystemDecimal() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.decimal");
      assertFalse(ts.matchesType(SystemType.DECIMAL));
    }

    @Test
    void fhirCodingDoesNotMatchSystemCoding() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.Coding");
      assertFalse(ts.matchesType(SystemType.CODING));
    }

    @Test
    void fhirQuantityDoesNotMatchSystemQuantity() {
      // Per fhirpath.js (#188), (1 year).is(FHIR.Quantity) is false — System.Quantity ≠
      // FHIR.Quantity.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.Quantity");
      assertFalse(ts.matchesType(SystemType.QUANTITY));
    }

    @Test
    void systemCodingMatchesSystemCoding() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.Coding");
      assertTrue(ts.matchesType(SystemType.CODING));
    }

    @Test
    void systemQuantityDoesNotMatchFhirPrimitiveDecimal() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.Quantity");
      assertFalse(ts.matchesType(FhirPrimitiveType.of("decimal")));
    }

    @Test
    void systemBooleanDoesNotMatchFhirPrimitiveBoolean() {
      // FHIR.boolean values (e.g., Patient.active) do NOT match System.Boolean specifier.
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.Boolean");
      assertFalse(ts.matchesType(FhirPrimitiveType.of("boolean")));
    }

    @Test
    void systemTypeDoesNotMatchComplexType() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("System.String");
      final InlineComplexType ct = new InlineComplexType("HumanName");
      assertFalse(ts.matchesType(ct));
    }

    @Test
    void fhirComplexTypeMatchesInlineComplexType() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.HumanName");
      final InlineComplexType ct = new InlineComplexType("HumanName");
      assertTrue(ts.matchesType(ct));
    }

    @Test
    void fhirComplexTypeDoesNotMatchDifferentComplexType() {
      final TypeSpecifier ts = TypeSpecifier.fromExpression("FHIR.Address");
      final InlineComplexType ct = new InlineComplexType("HumanName");
      assertFalse(ts.matchesType(ct));
    }
  }

  @Nested
  class ToFhirVariantName {

    @Test
    void fhirTypeReturnsTypeNameDirectly() {
      assertEquals("string", TypeSpecifier.fromExpression("FHIR.string").toFhirVariantName());
      assertEquals("Quantity", TypeSpecifier.fromExpression("FHIR.Quantity").toFhirVariantName());
      assertEquals("boolean", TypeSpecifier.fromExpression("FHIR.boolean").toFhirVariantName());
    }

    @Test
    void systemTypeReverseMapsToFhirName() {
      assertEquals("string", TypeSpecifier.fromExpression("System.String").toFhirVariantName());
      assertEquals("integer", TypeSpecifier.fromExpression("System.Integer").toFhirVariantName());
      assertEquals("boolean", TypeSpecifier.fromExpression("System.Boolean").toFhirVariantName());
      assertEquals("dateTime", TypeSpecifier.fromExpression("System.DateTime").toFhirVariantName());
      assertEquals("Quantity", TypeSpecifier.fromExpression("System.Quantity").toFhirVariantName());
      assertEquals("Coding", TypeSpecifier.fromExpression("System.Coding").toFhirVariantName());
    }
  }

  @Nested
  class EqualsAndHashCode {

    @Test
    void sameSpecifiersAreEqual() {
      final TypeSpecifier a = TypeSpecifier.fromExpression("System.String");
      final TypeSpecifier b = TypeSpecifier.fromExpression("System.String");
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentNamespacesAreNotEqual() {
      final TypeSpecifier fhir = TypeSpecifier.fromExpression("FHIR.Quantity");
      final TypeSpecifier system = TypeSpecifier.fromExpression("System.Quantity");
      assertFalse(fhir.equals(system));
    }
  }
}
