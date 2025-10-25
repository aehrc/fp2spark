package com.example.fhirpath;

import com.example.fhirpath.analyzer.CardinalityMismatchException;
import com.example.fhirpath.test.FhirPathTestBase;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static com.example.fhirpath.test.FhirPathTestBuilder.context;

/**
 * Demo test showcasing the new description format:
 * expression [with context] => expected [: description] [group]
 */
public class DescriptionFormatDemoTest extends FhirPathTestBase {

    @TestFactory
    Stream<DynamicTest> demonstrateDescriptionFormats() {
        return builder()
                // Minimal: no context, no description, no group
                .testEquals(15, "5 + 10")

                // With group only
                .group("Basic arithmetic")
                .testEquals(15, "5 + 10")
                .testEquals(25, "5 * 5")

                // With group and description
                .testEquals(5.0, "10 / 2", "Division always returns decimal")
                .testTrue("5 < 10", "Less than comparison")

                // With context (no description)
                .group("Context operations")
                .testEquals(15, "5 + %context", context("10"))
                .testEquals(1, "%context.count()", context("'x'"))

                // With context and description
                .testEquals(15, "5 + %context", context("10"), "Add to context value")
                .testTrue("exists()", context("'x'"), "Implicit exists on context")

                // Error tests
                .group("Cardinality errors")
                .testError(CardinalityMismatchException.class, "(1 ; 2) + 2", "MANY left operand")

                // Empty tests
                .group("Empty results")
                .testEmpty("{} + 10", "Empty collection + Integer")

                // List results
                .group("Collection results")
                .testEquals(List.of(2, 3), "(1 ; 2 ; 3).where($this > 1)", "Filter collection")

                .build();
    }
}
