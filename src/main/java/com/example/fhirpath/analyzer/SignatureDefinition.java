package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.stream.Stream;

/**
 * Phase 1 signature definition: parameters and result with explicit cardinality.
 *
 * <p>In Phase 1, we enumerate types explicitly without type variables.
 * Each parameter has a concrete type + cardinality via ParamSpec.
 * Result type + cardinality is specified via ResultTypeSpec.
 *
 * <p>ResultTypeSpec supports both static and dynamic type resolution:
 * <ul>
 *   <li>Static: {@code ResultTypeSpec.single(INTEGER)} for fixed types
 *   <li>Dynamic: {@code ResultTypeSpec.inputType(MANY)} to preserve input type
 * </ul>
 *
 * <p>Phase 2 will add support for type variables and constraints.
 *
 * <p>Implements TypeGroup to enable zero-overhead usage in registry:
 * a SignatureDefinition IS a TypeGroup that expands to itself.
 */
public record SignatureDefinition(
    @Nonnull List<ParamSpec> parameters,
    @Nonnull ResultTypeSpec resultSpec,
    int minArity,
    @Nullable LambdaBindingStrategy lambdaBinding
) implements TypeGroup {
    /**
     * Constructor for non-lambda signatures with fixed arity.
     */
    public SignatureDefinition(
        @Nonnull final List<ParamSpec> parameters,
        @Nonnull final ResultTypeSpec resultSpec
    ) {
        this(parameters, resultSpec, parameters.size(), null);
    }

    /**
     * Constructor for non-lambda signatures with variable arity.
     */
    public SignatureDefinition(
        @Nonnull final List<ParamSpec> parameters,
        @Nonnull final ResultTypeSpec resultSpec,
        final int minArity
    ) {
        this(parameters, resultSpec, minArity, null);
    }

    public int arity() {
        return parameters.size();
    }

    /**
     * TypeGroup implementation: a signature expands to itself.
     * This enables zero-overhead usage in registry - no wrapper needed.
     */
    @Nonnull
    @Override
    public Stream<SignatureDefinition> expand() {
        return Stream.of(this);
    }

    /**
     * Returns true if any parameter type is a LambdaType.
     */
    public boolean hasLambdaParameters() {
        return parameters.stream().anyMatch(p -> p.type() instanceof LambdaType);
    }

    /**
     * Returns true if this signature can be applied to the given number of arguments.
     * A signature matches if argCount is within [minArity, arity].
     */
    public boolean canApplyToArgumentCount(final int argCount) {
        return argCount >= minArity && argCount <= arity();
    }

    /**
     * Gets the parameter spec at the given index.
     */
    @Nonnull
    public ParamSpec parameter(int index) {
        return parameters.get(index);
    }

    /**
     * Gets all parameter types (without cardinality).
     * Convenience method for backward compatibility.
     */
    @Nonnull
    public List<Type> parameterTypes() {
        return parameters.stream()
            .map(ParamSpec::type)
            .toList();
    }

    /**
     * Gets the result type (element type without cardinality) for static signatures.
     * For dynamic signatures, this returns the type from the Static wrapper.
     *
     * @deprecated Use resultSpec.resolve() for accurate type information
     */
    @Nonnull
    @Deprecated
    public Type resultType() {
        if (resultSpec instanceof ResultTypeSpec.Static staticSpec) {
            return staticSpec.type();
        }
        throw new UnsupportedOperationException(
            "Cannot get static result type from dynamic ResultTypeSpec. Use resolve() instead."
        );
    }

    /**
     * Gets the result cardinality for static signatures.
     * For dynamic signatures, this returns the cardinality from the wrapper.
     *
     * @deprecated Use resultSpec.resolve() for accurate cardinality information
     */
    @Nonnull
    @Deprecated
    public Cardinality resultCardinality() {
        if (resultSpec instanceof ResultTypeSpec.Static staticSpec) {
            return staticSpec.cardinality();
        } else if (resultSpec instanceof ResultTypeSpec.InputType inputType) {
            return inputType.cardinality();
        } else if (resultSpec instanceof ResultTypeSpec.EffectiveInputType effectiveType) {
            return effectiveType.cardinality();
        } else if (resultSpec instanceof ResultTypeSpec.LambdaBodyType) {
            // LambdaBodyType extracts cardinality from lambda body at resolution time
            throw new UnsupportedOperationException(
                "Cannot get static cardinality from LambdaBodyType. Use resolve() instead."
            );
        }
        throw new UnsupportedOperationException(
            "Unknown ResultTypeSpec type: " + resultSpec.getClass()
        );
    }

    @Override
    public String toString() {
        return parameters.stream()
            .map(ParamSpec::toString)
            .reduce((a, b) -> a + ", " + b)
            .map(params -> "(" + params + ") → " + resultSpec)
            .orElse("() → " + resultSpec);
    }
}
