package com.example.fhirpath.operation.signature;

import jakarta.annotation.Nonnull;

import java.util.stream.Stream;

/**
 * Represents a group of signature definitions that can be expanded.
 *
 * This interface enables uniform handling of both single signatures
 * and collections of related signatures. Key implementations:
 *
 * - SignatureDefinition: A single signature expands to itself
 * - TypeMapping: Applies a function over multiple types to generate signatures
 *
 * This design eliminates wrapper overhead for single signatures while
 * enabling elegant composition of multi-type patterns.
 */
public sealed interface TypeGroup permits SignatureDefinition, TypeMapping {

    /**
     * Expands this type group into a stream of signature definitions.
     *
     * For single signatures, returns a stream containing just the signature.
     * For type mappings, applies the mapping function to each type.
     *
     * @return stream of signature definitions
     */
    @Nonnull
    Stream<SignatureDefinition> expand();
}
