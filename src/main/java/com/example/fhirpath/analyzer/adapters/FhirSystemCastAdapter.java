package com.example.fhirpath.analyzer.adapters;

import com.example.fhirpath.analyzer.AdaptationResult;
import com.example.fhirpath.analyzer.TypeAdapter;
import com.example.fhirpath.ir.CastToSystem;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.fhir.FhirType;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * Adapter for unwrapping FHIR types to their system types.
 *
 * <p>Implements the FHIR system cast rule:
 * <pre>
 * FhirType(T) → T  (cost: 1)
 * </pre>
 *
 * <p>This requires inserting a {@link CastToSystem} IR node that performs
 * the runtime conversion (equivalent to FHIRPath's getValue() function).
 *
 * <p><b>Examples:</b>
 * <ul>
 *   <li>FhirType(STRING) → STRING (cost: 1)
 *   <li>FhirType(INTEGER) → INTEGER (cost: 1)
 *   <li>FhirType(DECIMAL) → DECIMAL (cost: 1)
 * </ul>
 *
 * <p>Further adaptations (e.g., FhirType(INTEGER) → DECIMAL) are handled by
 * applying this adapter followed by {@link PrimitiveCastAdapter} in subsequent
 * graph traversal steps.
 */
public final class FhirSystemCastAdapter implements TypeAdapter {

    @Nonnull
    @Override
    public Set<AdaptationResult> reachableAdaptations(@Nonnull AdaptationResult start) {
        Type sourceType = start.type();

        if (sourceType instanceof FhirType fhirType) {
            Type systemType = fhirType.systemType();
            CastToSystem castNode = new CastToSystem(start.node());
            return Set.of(start.withAdaptation(castNode, systemType, 1));
        }

        return Set.of();
    }
}

