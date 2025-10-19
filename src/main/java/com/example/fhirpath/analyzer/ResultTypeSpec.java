package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * Specification for result type + cardinality of an operation.
 *
 * <p>This is a unified abstraction that supports both:
 * <ul>
 *   <li><b>Static</b> result types - known at signature definition time
 *   <li><b>Dynamic</b> result types - computed from argument types
 * </ul>
 *
 * <p>Phase 1 uses both static and dynamic resolution:
 * <ul>
 *   <li>Static: {@code add(?INTEGER, ?INTEGER) → ?INTEGER}
 *   <li>Dynamic: {@code where(*T, Lambda) → *T} (preserves input type)
 * </ul>
 *
 * <p>Phase 2 will add type variables to eliminate need for dynamic resolution.
 */
public sealed interface ResultTypeSpec
        permits ResultTypeSpec.Static,
                ResultTypeSpec.InputType,
                ResultTypeSpec.EffectiveInputType {

    /**
     * Resolve the result shape from analyzed arguments.
     *
     * @param resolvedArgs the IR nodes for resolved arguments
     * @return the result shape (type + cardinality)
     */
    @Nonnull
    Shape resolve(@Nonnull List<IRNode> resolvedArgs);

    /**
     * Creates a static result spec for SINGLE cardinality (?T).
     */
    @Nonnull
    static ResultTypeSpec single(@Nonnull Type type) {
        return new Static(type, Cardinality.SINGLE);
    }

    /**
     * Creates a static result spec for MANY cardinality (*T).
     */
    @Nonnull
    static ResultTypeSpec many(@Nonnull Type type) {
        return new Static(type, Cardinality.MANY);
    }

    /**
     * Creates a dynamic result spec that preserves input element type.
     * Result type = first argument's type.
     *
     * <p>Used for operations like {@code where()} that preserve input type.
     *
     * @param cardinality the result cardinality
     * @return dynamic result spec
     */
    @Nonnull
    static ResultTypeSpec inputType(@Nonnull Cardinality cardinality) {
        return new InputType(cardinality);
    }

    /**
     * Creates a dynamic result spec that extracts element type from input.
     * Result type = element type of first argument.
     *
     * <p>Used for operations like {@code first()} that extract elements.
     *
     * @param cardinality the result cardinality (usually SINGLE)
     * @return dynamic result spec
     */
    @Nonnull
    static ResultTypeSpec effectiveInputType(@Nonnull Cardinality cardinality) {
        return new EffectiveInputType(cardinality);
    }

    /**
     * Static result type - known at signature definition time.
     *
     * <p>Example: {@code add(?INTEGER, ?INTEGER) → ?INTEGER}
     */
    record Static(@Nonnull Type type, @Nonnull Cardinality cardinality) implements ResultTypeSpec {
        @Override
        @Nonnull
        public Shape resolve(@Nonnull List<IRNode> resolvedArgs) {
            return Shape.of(type, cardinality);
        }

        /**
         * Legacy method for backward compatibility.
         * Use {@link #resolve(List)} instead.
         */
        @Nonnull
        public Shape toShape() {
            return Shape.of(type, cardinality);
        }

        @Override
        public String toString() {
            return (cardinality == Cardinality.SINGLE ? "?" : "*") + type.getName();
        }
    }

    /**
     * Dynamic result type - preserves input element type.
     *
     * <p>Result type = first argument's type (element type).
     *
     * <p>Example: {@code where(*ComplexType, Lambda) → *ComplexType}
     */
    record InputType(@Nonnull Cardinality cardinality) implements ResultTypeSpec {
        @Override
        @Nonnull
        public Shape resolve(@Nonnull List<IRNode> resolvedArgs) {
            if (resolvedArgs.isEmpty()) {
                throw new IllegalArgumentException(
                        "InputType requires at least one argument");
            }
            // Get element type from first argument
            Type inputType = resolvedArgs.get(0).getType();
            return Shape.of(inputType, cardinality);
        }

        @Override
        public String toString() {
            return (cardinality == Cardinality.SINGLE ? "?" : "*") + "T (input type)";
        }
    }

    /**
     * Dynamic result type - extracts element type from input collection.
     *
     * <p>Result type = element type of first argument's type.
     *
     * <p>In Phase 1 (no collection wrapper types), this behaves the same as InputType.
     * In Phase 2, this would unwrap collection types.
     *
     * <p>Example: {@code first(*T) → ?T}
     */
    record EffectiveInputType(@Nonnull Cardinality cardinality) implements ResultTypeSpec {
        @Override
        @Nonnull
        public Shape resolve(@Nonnull List<IRNode> resolvedArgs) {
            if (resolvedArgs.isEmpty()) {
                throw new IllegalArgumentException(
                        "EffectiveInputType requires at least one argument");
            }
            // In Phase 1: Type is always the element type (no unwrapping needed)
            Type elementType = resolvedArgs.get(0).getType();
            return Shape.of(elementType, cardinality);
        }

        @Override
        public String toString() {
            return (cardinality == Cardinality.SINGLE ? "?" : "*") + "T (effective type)";
        }
    }
}
