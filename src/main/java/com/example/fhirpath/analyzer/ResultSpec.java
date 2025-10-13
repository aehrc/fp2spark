package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.fhir.FhirType;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Specifies how to determine the result type of a FHIRPath operation.
 *
 * This is a sealed hierarchy with exactly four implementations corresponding
 * to the four type resolution patterns in FHIRPath.
 */
public sealed interface ResultSpec
    permits ResultSpec.Static,
            ResultSpec.InputType,
            ResultSpec.EffectiveInputType,
            ResultSpec.FhirSystemType {

    /**
     * Resolve the actual result type given resolved argument nodes.
     * Called during Operation construction to produce statically-typed IR.
     */
    @Nonnull
    Type resolve(@Nonnull List<IRNode> resolvedArgs);

    /**
     * Result type is statically known at signature definition time.
     * This is the most common case.
     *
     * Examples: abs(Integer) → Integer, substring(String, Integer) → String
     */
    record Static(@Nonnull Type type) implements ResultSpec {
        @Override
        @Nonnull
        public Type resolve(@Nonnull List<IRNode> resolvedArgs) {
            return type;
        }
    }

    /**
     * Result type is the same as the input type (first argument).
     * Used for operations that preserve structure.
     *
     * Examples: Collection<T>.where(...) → Collection<T>
     */
    record InputType() implements ResultSpec {
        public static final InputType INSTANCE = new InputType();

        @Override
        @Nonnull
        public Type resolve(@Nonnull List<IRNode> resolvedArgs) {
            if (resolvedArgs.isEmpty()) {
                throw new IllegalArgumentException(
                    "InputType requires at least one argument");
            }
            return resolvedArgs.get(0).getType();
        }
    }

    /**
     * Result type is the effective type (element type) of the input collection.
     * Used for operations that extract elements from collections.
     *
     * Examples: Collection<T>.first() → T, Collection<T>.item[n] → T
     */
    record EffectiveInputType() implements ResultSpec {
        public static final EffectiveInputType INSTANCE = new EffectiveInputType();

        @Override
        @Nonnull
        public Type resolve(@Nonnull List<IRNode> resolvedArgs) {
            if (resolvedArgs.isEmpty()) {
                throw new IllegalArgumentException(
                    "EffectiveInputType requires at least one argument");
            }
            return resolvedArgs.get(0).getType().effectiveType();
        }
    }

    /**
     * Result type is the system type corresponding to a FHIR type.
     * Used exclusively by getValue().
     *
     * Examples: FhirType(STRING).getValue() → String
     */
    record FhirSystemType() implements ResultSpec {
        public static final FhirSystemType INSTANCE = new FhirSystemType();

        @Override
        @Nonnull
        public Type resolve(@Nonnull List<IRNode> resolvedArgs) {
            if (resolvedArgs.isEmpty()) {
                throw new IllegalArgumentException(
                    "FhirSystemType requires at least one argument");
            }
            Type inputType = resolvedArgs.get(0).getType();
            if (!(inputType instanceof FhirType fhirType)) {
                throw new IllegalArgumentException(
                    "FhirSystemType requires FhirType input, got: " + inputType);
            }
            return fhirType.systemType();
        }
    }
}
