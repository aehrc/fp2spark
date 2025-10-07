package com.example.fhirpath.ir.builder;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class IRClassBuilder implements IRNodeBuilder {
    private final Class<? extends IRNode> irClass;

    public IRClassBuilder(Class<? extends IRNode> irClass) {
        this.irClass = irClass;
    }

    @Override
    @Nonnull
    public IRNode build(@Nonnull IRNode... children) {
        try {
            return (IRNode) irClass.getDeclaredConstructors()[0].newInstance((Object[]) children);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    @Nonnull
    public List<FunctionSignature> getSignatures() {
        try {
            Field signatures = irClass.getField("SIGNATURES");
            // make sure it's static and then get value
            if ((signatures.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0) {
                throw new IllegalArgumentException("SIGNATURES field must be static");
            }
            //noinspection unchecked
            return (List<FunctionSignature>) signatures.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return List.of();
        }
    }
}
