package com.example.fhirpath.typing;

import com.example.fhirpath.typing.fhir.FhirType;

import jakarta.annotation.Nonnull;
import java.util.stream.Stream;

/**
 * Utilities for working with the FHIRPath type system.
 */
public final class TypeSystem {
    private TypeSystem() {
    }

    /**
     * Checks if a value of type {@code from} can be adapted/cast to type {@code to}.
     *
     * <p>Implements the adaptation rules from the FHIRPath type system:
     * <ul>
     *   <li>INTEGER → DECIMAL</li>
     *   <li>DECIMAL → QUANTITY</li>
     *   <li>DATE → DATE_TIME</li>
     *   <li>FhirType[Prim] → Prim</li>
     *   <li>NULL → any type</li>
     * </ul>
     */
    public static boolean canCast(Type from, Type to) {
        if (from instanceof FhirType ft) {
            return canCast(ft.systemType(), to);
        }
        if (from == to) return true;
        if (from == PrimitiveType.NULL) return true;

        // Primitive casts (adaptation rules)
        if (from == PrimitiveType.INTEGER && to == PrimitiveType.DECIMAL) return true;
        if (from == PrimitiveType.DECIMAL && to == PrimitiveType.QUANTITY) return true;
        if (from == PrimitiveType.DATE && to == PrimitiveType.DATE_TIME) return true;

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
                .filter(t -> t != PrimitiveType.ANY);
    }
}

