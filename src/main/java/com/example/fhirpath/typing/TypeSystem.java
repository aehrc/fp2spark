package com.example.fhirpath.typing;

import com.example.fhirpath.typing.fhir.FhirType;

import javax.annotation.Nonnull;
import java.util.stream.Stream;

public final class TypeSystem {
    private TypeSystem() {
    }

    public static boolean canCast(Type from, Type to) {
        if (from instanceof FhirType ft) {
            return canCast(ft.systemType(), to);
        }
        if (from == to) return true;
        if (from == Type.NULL) return true;

        // Primitive casts
        if (from == Type.INTEGER && to == Type.DECIMAL) return true;
        if (from == Type.DECIMAL && to == Type.QUANTITY) return true;
        if (from == Type.DATE && to == Type.DATE_TIME) return true;

        // Collection element type compatibility
        if (from instanceof CollectionType cf && to instanceof CollectionType ct) {
            return canCast(cf.elementType(), ct.elementType());
        }

        return false;
    }

    @Nonnull
    public static Stream<Type> allTypes() {
        return Stream.of(PrimitiveType.values());
    }

    @Nonnull
    public static Stream<Type> definedTypes() {
        return Stream.of(PrimitiveType.values())
                .map(Type.class::cast)
                .filter(t -> t != PrimitiveType.UNKNOWN);
    }
}

