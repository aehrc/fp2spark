package com.example.fhirpath.analyzer.adapters;

import com.example.fhirpath.analyzer.AdaptationResult;
import com.example.fhirpath.analyzer.TypeAdapter;
import com.example.fhirpath.typing.Types;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * Adapter for wildcard matching (T → ANY).
 *
 * <p>Per the type system specification, any OrdinaryType can match ANY with cost 0.
 * This is a free adaptation that doesn't require IR node transformation.
 *
 * <p><b>Examples:</b>
 * <ul>
 *   <li>INTEGER → ANY (cost: 0)
 *   <li>[STRING] → ANY (cost: 0)
 *   <li>ComplexType → ANY (cost: 0)
 * </ul>
 */
public final class WildcardAdapter implements TypeAdapter {

    @Nonnull
    @Override
    public Set<AdaptationResult> reachableAdaptations(@Nonnull AdaptationResult start) {
        // Any OrdinaryType can adapt to ANY with cost 0
        // (We don't modify the IR node - it's just a type system concept)
        if (!start.hasType(Types.ANY)) {
            return Set.of(start);
        }

        return Set.of();
    }
}

