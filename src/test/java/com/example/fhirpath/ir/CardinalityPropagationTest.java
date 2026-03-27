package com.example.fhirpath.ir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.fhirpath.analyzer.Analyzer;
import com.example.fhirpath.parser.ParserFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests that where(), skip(), take(), tail(), and distinct() preserve input cardinality (the α
 * variable in TYPE_SYSTEM.md) in the compiled IR shape.
 *
 * <p>Singular input (e.g., a literal) should produce a singular result; plural input (e.g., a
 * combine expression) should produce a plural result.
 */
class CardinalityPropagationTest {

  private static IRNode compile(String expr) {
    return new Analyzer().analyze(ParserFacade.parse(expr));
  }

  @Nested
  @DisplayName("where()")
  class Where {

    @Test
    @DisplayName("singular input → singular result")
    void singularInput() {
      assertTrue(compile("2.where($this > 1)").isSingular());
    }

    @Test
    @DisplayName("plural input → plural result")
    void pluralInput() {
      assertFalse(compile("(1 ; 2 ; 3).where($this > 1)").isSingular());
    }
  }

  @Nested
  @DisplayName("skip()")
  class Skip {

    @Test
    @DisplayName("singular input → singular result")
    void singularInput() {
      assertTrue(compile("5.skip(0)").isSingular());
    }

    @Test
    @DisplayName("plural input → plural result")
    void pluralInput() {
      assertFalse(compile("(1 ; 2 ; 3).skip(1)").isSingular());
    }
  }

  @Nested
  @DisplayName("take()")
  class Take {

    @Test
    @DisplayName("singular input → singular result")
    void singularInput() {
      assertTrue(compile("5.take(1)").isSingular());
    }

    @Test
    @DisplayName("plural input → plural result")
    void pluralInput() {
      assertFalse(compile("(1 ; 2 ; 3).take(2)").isSingular());
    }
  }

  @Nested
  @DisplayName("tail()")
  class Tail {

    @Test
    @DisplayName("singular input → singular result")
    void singularInput() {
      assertTrue(compile("5.tail()").isSingular());
    }

    @Test
    @DisplayName("plural input → plural result")
    void pluralInput() {
      assertFalse(compile("(1 ; 2 ; 3).tail()").isSingular());
    }
  }

  @Nested
  @DisplayName("distinct()")
  class Distinct {

    @Test
    @DisplayName("singular input → singular result")
    void singularInput() {
      assertTrue(compile("5.distinct()").isSingular());
    }

    @Test
    @DisplayName("plural input → plural result")
    void pluralInput() {
      assertFalse(compile("(1 ; 2 ; 3).distinct()").isSingular());
    }
  }
}
