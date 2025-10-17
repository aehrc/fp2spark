package com.example.fhirpath.analyzer;

import com.example.fhirpath.analyzer.adapters.*;
import com.example.fhirpath.ir.Literal;
import com.example.fhirpath.typing.PrimitiveType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AdaptationEngine and TypeAdapter system.
 */
class AdaptationEngineTest {

    private final AdaptationEngine engine = new AdaptationEngine(List.of(
        new ExactMatchAdapter(),
        new PrimitiveCastAdapter(),
        new FhirSystemCastAdapter(),
        new CollectionPromotionAdapter(),
        new WildcardAdapter(),
        new NullAdapter()
    ));

    @Test
    void testDirectAdaptation_IntegerToDecimal() {
        // Given: INTEGER literal
        Literal intNode = new Literal(42, PrimitiveType.INTEGER);

        // When: Adapt to DECIMAL
        AdaptationResult result = engine.adaptTo(intNode, PrimitiveType.DECIMAL);

        // Then: Should succeed with cost 1
        assertNotNull(result);
        assertEquals(PrimitiveType.DECIMAL, result.type());
        assertEquals(1, result.cost());
    }

    @Test
    void testDirectAdaptation_IntegerToQuantity() {
        // Given: INTEGER literal
        Literal intNode = new Literal(42, PrimitiveType.INTEGER);

        // When: Adapt to QUANTITY (requires two steps: INT->DEC->QTY)
        AdaptationResult result = engine.adaptTo(intNode, PrimitiveType.QUANTITY);

        // Then: Should succeed with cost 2
        assertNotNull(result);
        assertEquals(PrimitiveType.QUANTITY, result.type());
        assertEquals(2, result.cost());
    }

    @Test
    void testAdaptationClosure_Integer() {
        // Given: INTEGER literal
        Literal intNode = new Literal(42, PrimitiveType.INTEGER);

        // When: Compute closure
        Set<AdaptationResult> closure = engine.computeClosure(intNode);

        // Then: Should contain INTEGER, DECIMAL, QUANTITY, ANY, and their collection versions
        assertTrue(closure.stream().anyMatch(r -> r.type() == PrimitiveType.INTEGER && r.cost() == 0));
        assertTrue(closure.stream().anyMatch(r -> r.type() == PrimitiveType.DECIMAL && r.cost() == 1));
        assertTrue(closure.stream().anyMatch(r -> r.type() == PrimitiveType.QUANTITY && r.cost() == 2));
    }

    @Test
    void testFindCommonType_IntegerAndDecimal() {
        // Given: INTEGER and DECIMAL literals
        Literal intNode = new Literal(42, PrimitiveType.INTEGER);
        Literal decNode = new Literal(3.14, PrimitiveType.DECIMAL);

        // When: Find common type
        AdaptationEngine.CommonTypeResult result = engine.findCommonType(intNode, decNode);

        // Then: Should be DECIMAL (cheaper than QUANTITY)
        assertNotNull(result);
        assertEquals(PrimitiveType.DECIMAL, result.commonType());
        assertEquals(1, result.totalCost()); // INT->DEC costs 1, DEC->DEC costs 0
    }

    @Test
    void testNoAdaptationPossible() {
        // Given: STRING literal
        Literal strNode = new Literal("hello", PrimitiveType.STRING);

        // When: Try to adapt to INTEGER (impossible)
        AdaptationResult result = engine.adaptTo(strNode, PrimitiveType.INTEGER);

        // Then: Should fail
        assertNull(result);
    }

    @Test
    void testIdentityAdaptation() {
        // Given: INTEGER literal
        Literal intNode = new Literal(42, PrimitiveType.INTEGER);

        // When: Adapt to same type
        AdaptationResult result = engine.adaptTo(intNode, PrimitiveType.INTEGER);

        // Then: Should succeed with cost 0
        assertNotNull(result);
        assertEquals(PrimitiveType.INTEGER, result.type());
        assertEquals(0, result.cost());
        assertSame(intNode, result.node()); // Same node, no adaptation needed
    }
}

