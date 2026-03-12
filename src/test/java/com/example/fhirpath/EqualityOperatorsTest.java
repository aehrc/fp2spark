package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath equality operators ({@code =} and {@code !=}).
 *
 * <p>Based on FHIRPath specification section 6.1: Equality
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Equality and not-equals for all System types (String, Integer, Decimal, Boolean)
 *   <li>Cross-type numeric coercion (Integer/Decimal)
 *   <li>Empty collection propagation
 *   <li>Incompatible type handling
 *   <li>Collection equality (element-by-element, order-sensitive)
 *   <li>Compound expressions combining equality with boolean operators
 * </ul>
 */
class EqualityOperatorsTest extends FhirPathTestBase {

  // ========== String equality ==========

  @TestFactory
  Stream<DynamicTest> testStringEquality() {
    return builder()
        .group("String equality")
        .testTrue("'abc' = 'abc'", "Equal strings")
        .testFalse("'abc' = 'def'", "Different strings")
        .testFalse("'abc' != 'abc'", "Not-equals equal strings")
        .testTrue("'abc' != 'def'", "Not-equals different strings")
        .build();
  }

  // ========== Integer equality ==========

  @TestFactory
  Stream<DynamicTest> testIntegerEquality() {
    return builder()
        .group("Integer equality")
        .testTrue("1 = 1", "Equal integers")
        .testFalse("1 = 2", "Different integers")
        .testFalse("1 != 1", "Not-equals equal integers")
        .testTrue("1 != 2", "Not-equals different integers")
        .build();
  }

  // ========== Decimal equality ==========

  @TestFactory
  Stream<DynamicTest> testDecimalEquality() {
    return builder()
        .group("Decimal equality")
        .testTrue("1.0 = 1.0", "Equal decimals")
        .testFalse("1.0 = 2.0", "Different decimals")
        .testTrue("1.0 = 1.00", "Trailing zeros are equal")
        .testFalse("1.0 != 1.0", "Not-equals equal decimals")
        .testTrue("1.0 != 2.0", "Not-equals different decimals")
        .build();
  }

  // ========== Boolean equality ==========

  @TestFactory
  Stream<DynamicTest> testBooleanEquality() {
    return builder()
        .group("Boolean equality")
        .testTrue("true = true", "Equal booleans true")
        .testTrue("false = false", "Equal booleans false")
        .testFalse("true = false", "Different booleans")
        .testFalse("true != true", "Not-equals equal booleans")
        .testTrue("true != false", "Not-equals different booleans")
        .build();
  }

  // ========== Cross-type numeric equality ==========

  @TestFactory
  Stream<DynamicTest> testCrossTypeNumericEquality() {
    return builder()
        .group("Integer/Decimal cross-type equality")
        .testTrue("1 = 1.0", "Integer equals decimal")
        .testFalse("1 = 1.5", "Integer not equals decimal")
        .testFalse("1 != 1.0", "Not-equals integer and equal decimal")
        .testTrue("1 != 1.5", "Not-equals integer and different decimal")
        .build();
  }

  // ========== Empty collection semantics ==========

  @TestFactory
  Stream<DynamicTest> testEmptyCollectionSemantics() {
    return builder()
        .group("Empty collection semantics")
        .testEmpty("{} = 1", "Empty equals value")
        .testEmpty("1 = {}", "Value equals empty")
        .testEmpty("{} = {}", "Both empty")
        .testEmpty("{} != 1", "Empty not-equals value")
        .testEmpty("1 != {}", "Value not-equals empty")
        .testEmpty("{} != {}", "Both empty not-equals")
        .build();
  }

  // ========== Incompatible types ==========

  @TestFactory
  Stream<DynamicTest> testIncompatibleTypes() {
    return builder()
        .group("Incompatible types")
        .testFalse("1 = 'hello'", "Integer vs String")
        .testFalse("true = 1", "Boolean vs Integer")
        .testFalse("'abc' = true", "String vs Boolean")
        .testTrue("1 != 'hello'", "Not-equals Integer vs String")
        .testTrue("true != 1", "Not-equals Boolean vs Integer")
        .testTrue("'abc' != true", "Not-equals String vs Boolean")
        .build();
  }

  // ========== Compound expressions ==========

  @TestFactory
  Stream<DynamicTest> testCompoundExpressions() {
    return builder()
        .group("Compound expressions with equality")
        .testTrue("(1 = 1) and (2 = 2)", "Both equalities true")
        .testFalse("(1 = 1) and (2 = 3)", "One equality false")
        .testTrue("(1 != 2) or (3 = 4)", "Not-equals true or equals false")
        .testTrue("(1 = 2) or (3 = 3)", "Equals false or equals true")
        .build();
  }

  // ========== Collection equality (using combine operator) ==========

  @TestFactory
  Stream<DynamicTest> testCollectionEquality() {
    return builder()
        .group("Equal collections")
        .testTrue("(1;2;3) = (1;2;3)", "Equal integer collections")
        .testTrue("('a';'b') = ('a';'b')", "Equal string collections")
        .testFalse("(1;2;3) = (1;2;4)", "Different values")
        .testFalse("('a';'b') = ('a';'c')", "Different string values")
        .testFalse("(1;2;3) = (1;2)", "Different sizes (left longer)")
        .testFalse("(1;2) = (1;2;3)", "Different sizes (right longer)")
        .testFalse("(1;2;3) = (3;2;1)", "Order matters")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCollectionNotEquals() {
    return builder()
        .group("Collection not-equals")
        .testTrue("(1;2) != (1;3)", "Different values")
        .testFalse("(1;2) != (1;2)", "Equal collections")
        .testTrue("(1;2;3) != (1;2)", "Different sizes")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCollectionEmptyEquality() {
    return builder()
        .group("Empty vs collection equality")
        .testEmpty("{} = (1;2)", "Empty equals collection")
        .testEmpty("(1;2) = {}", "Collection equals empty")
        .testEmpty("{} != (1;2)", "Empty not-equals collection")
        .testEmpty("(1;2) != {}", "Collection not-equals empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCollectionCrossTypeEquality() {
    return builder()
        .group("Collection cross-type equality (Integer/Decimal)")
        .testTrue("(1;2;3) = (1.0;2.0;3.0)", "Integer and decimal collections equal")
        .testFalse("(1;2) != (1.0;2.0)", "Integer and decimal collections not-equals")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCollectionIncompatibleTypes() {
    return builder()
        .group("Collection incompatible types")
        .testFalse("(1;2;3) = ('a';'b';'c')", "Integer vs String collections")
        .testFalse("(true;false) = (1;0)", "Boolean vs Integer collections")
        .testTrue("(1;2) != ('a';'b')", "Not-equals Integer vs String collections")
        .build();
  }

  // ========== Singular value vs array field (resource) ==========

  @TestFactory
  Stream<DynamicTest> testSingularFieldEquality() {
    return builder()
        .group("Singular resource field equality")
        .withSubject(
            "Patient",
            p ->
                p.string("id", "p1")
                    .elementArray(
                        "name", n -> n.string("family", "Smith").stringArray("given", "John")))
        // Singular field vs singular literal — both scalars
        .testTrue("id = 'p1'", "Singular field equals matching literal")
        .testFalse("id = 'other'", "Singular field equals different literal")
        .testTrue("id != 'other'", "Singular field not-equals different literal")
        .testFalse("id != 'p1'", "Singular field not-equals matching literal")
        // Singular field vs singular field
        .testTrue("name.family = 'Smith'", "Nested singular field equals literal")
        // Single-element array field vs singular literal
        .testTrue("name.given = ('John')", "Single-element array vs singular literal")
        .testFalse("name.given != ('John')", "Single-element array not-equals matching")
        .testTrue("name.given != ('Other')", "Single-element array not-equals different")
        .build();
  }

  // ========== Resource field collection equality ==========

  @TestFactory
  Stream<DynamicTest> testResourceFieldCollectionEquality() {
    return builder()
        .group("Resource field collection equality")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.string("family", "Smith").stringArray("given", "Piotr", "Jaroslaw")))
        .testTrue("name.given = ('Piotr';'Jaroslaw')", "Array field equals matching collection")
        .testFalse("name.given != ('Piotr';'Jaroslaw')", "Array field not-equals matching")
        .testTrue("name.given != ('Other';'Name')", "Array field not-equals different collection")
        .testFalse("name.given = ('Other';'Name')", "Array field equals different collection")
        .build();
  }
}
