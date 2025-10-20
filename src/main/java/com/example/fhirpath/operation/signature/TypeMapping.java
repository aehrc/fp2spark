package com.example.fhirpath.operation.signature;

import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Type group that applies a signature-generating function over a set of types.
 *
 * This enables the pattern: forTypes(NUMERIC).define(Signatures::unaryOp)
 * which expands to one signature per type in the set.
 *
 * Example:
 * <pre>
 * forTypes(TypeSets.NUMERIC).define(Signatures::unaryOp)
 * // Expands to:
 * // abs(Integer) → Integer
 * // abs(Decimal) → Decimal
 * </pre>
 */
public record TypeMapping(
    @Nonnull Set<Type> types,
    @Nonnull Function<Type, SignatureDefinition> mapper
) implements TypeGroup {

    /**
     * Expands this type mapping by applying the mapper function to each type.
     */
    @Nonnull
    @Override
    public Stream<SignatureDefinition> expand() {
        return types.stream().map(mapper);
    }
}
