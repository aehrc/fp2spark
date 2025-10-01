package com.example.fhirpath.typing;

import javax.annotation.Nonnull;
import java.util.stream.Stream;

public final class TypeSystem {
    private TypeSystem() {
    }

    public static boolean canCast(Type from, Type to) {
        if (from == to) return true;
        if (from == Type.INTEGER && to == Type.DECIMAL) return true;
        if (from == Type.DECIMAL && to == Type.QUANTITY) return true;
        if (from == Type.DATE && to == Type.DATE_TIME) return true;
        return false;
    }

    @Nonnull
    public static Stream<Type> allTypes() {
        return Stream.of(Type.values());
    }


    @Nonnull
    public static Stream<Type> definedTypes() {
        return Stream.of(Type.values()).filter(t -> t != Type.UNKNOWN);
    }
}

