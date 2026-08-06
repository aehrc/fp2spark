package au.csiro.fhirpath;

import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath boolean collection functions: allTrue(), anyTrue(), allFalse(), anyFalse(),
 * all(criteria).
 *
 * <p>Based on FHIRPath specification section 5.6.1.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Core semantics for each function (all/any/none matching)
 *   <li>Empty collection behavior (spec-defined per function)
 *   <li>Singular value input (single boolean element)
 * </ul>
 */
public class BooleanCollectionFunctionsTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testAllTrue() {
    return builder()
        .group("allTrue() core semantics")
        .testTrue("(true ; true).allTrue()")
        .testFalse("(true ; false).allTrue()")
        .testFalse("(false ; false).allTrue()")
        .testTrue("true.allTrue()", "Single true")
        .testFalse("false.allTrue()", "Single false")
        .group("allTrue() empty collection")
        .testTrue("{}.allTrue()", "Empty returns true per spec")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testAnyTrue() {
    return builder()
        .group("anyTrue() core semantics")
        .testTrue("(true ; true).anyTrue()")
        .testTrue("(false ; true).anyTrue()")
        .testFalse("(false ; false).anyTrue()")
        .testTrue("true.anyTrue()", "Single true")
        .testFalse("false.anyTrue()", "Single false")
        .group("anyTrue() empty collection")
        .testFalse("{}.anyTrue()", "Empty returns false per spec")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testAllFalse() {
    return builder()
        .group("allFalse() core semantics")
        .testTrue("(false ; false).allFalse()")
        .testFalse("(false ; true).allFalse()")
        .testFalse("(true ; true).allFalse()")
        .testTrue("false.allFalse()", "Single false")
        .testFalse("true.allFalse()", "Single true")
        .group("allFalse() empty collection")
        .testTrue("{}.allFalse()", "Empty returns true per spec")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testAnyFalse() {
    return builder()
        .group("anyFalse() core semantics")
        .testTrue("(true ; false).anyFalse()")
        .testTrue("(false ; false).anyFalse()")
        .testFalse("(true ; true).anyFalse()")
        .testTrue("false.anyFalse()", "Single false")
        .testFalse("true.anyFalse()", "Single true")
        .group("anyFalse() empty collection")
        .testFalse("{}.anyFalse()", "Empty returns false per spec")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testAll() {
    return builder()
        .group("all() core semantics")
        .testTrue("(1 ; 2 ; 3).all($this > 0)", "All match")
        .testFalse("(1 ; 2 ; 3).all($this > 1)", "Some don't match")
        .testFalse("(1 ; 2 ; 3).all($this > 5)", "None match")
        .testTrue("1.all($this > 0)", "Single match")
        .testFalse("1.all($this > 5)", "Single no match")
        .group("all() empty collection")
        .testTrue("{}.all($this > 0)", "Empty returns true per spec")
        .build();
  }
}
