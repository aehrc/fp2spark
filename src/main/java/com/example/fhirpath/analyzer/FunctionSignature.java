package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;

import java.util.List;

public record FunctionSignature(List<Type> parameterTypes, Type resultType) {
    public int arity() {
        return parameterTypes.size();
    }
}
