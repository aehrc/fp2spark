package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.List;

public record FunctionSignature(List<Type> parameterTypes, Type resultType) {
    public int arity() {
        return parameterTypes.size();
    }

    /**
     * Helper to create a binary operator signature (e.g. for +, -, etc.)
     *
     * @param type the type of both parameters and the result
     * @return the function signature
     */
    @Nonnull
    public static FunctionSignature biOperator(@Nonnull Type type) {
        return new FunctionSignature(List.of(type, type), type);
    }

    @Nonnull
    public static FunctionSignature biOperator(@Nonnull final Type argumentType, @Nonnull final Type resultType) {
        return new FunctionSignature(List.of(argumentType, argumentType), resultType);
    }
}
