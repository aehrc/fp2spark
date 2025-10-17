package com.example.fhirpath.analyzer.adapters;

import com.example.fhirpath.analyzer.AdaptationResult;
import com.example.fhirpath.analyzer.TypeAdapter;
import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * Adapter for singleton promotion (T → [T]).
 *
 * <p>Per FHIRPath semantics, scalar values can be used where collections are expected
 * (singleton promotion). This is a <b>free</b> adaptation with cost 0.
 *
 * <p><b>Examples:</b>
 * <ul>
 *   <li>INTEGER → [INTEGER] (cost: 0)
 *   <li>STRING → [STRING] (cost: 0)
 *   <li>ComplexType → [ComplexType] (cost: 0)
 * </ul>
 *
 * <p>This adapter does NOT modify the IR node. The singleton-to-collection
 * conversion is handled at runtime by the code generator.
 *
 * <p><b>Note:</b> Collections cannot be promoted to nested collections
 * ([T] → [[T]] is illegal), so this adapter only applies to non-collection types.
 */
public final class CollectionPromotionAdapter implements TypeAdapter {

    @Nonnull
    @Override
    public Set<AdaptationResult> reachableAdaptations(@Nonnull AdaptationResult start) {
        Type sourceType = start.type();

        // Only non-collection types can be promoted
        if (!sourceType.isCollection()) {
            CollectionType collectionType = new CollectionType(sourceType);
            // Free adaptation (cost 0) - no IR transformation needed
            return Set.of(start.withAdaptation(start.node(), collectionType, 0));
        }

        return Set.of();
    }
}

