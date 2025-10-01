package com.example.fhirpath.typing;

public final class TypeSystem {
    private TypeSystem() {}

    public static boolean canCast(Type from, Type to) {
        if (from == to) return true;
        if (from == Type.INTEGER && to == Type.DECIMAL) return true;
        if (from == Type.DECIMAL && to == Type.QUANTITY) return true;
        if (from == Type.DATE && to == Type.DATE_TIME) return true;
        return false;
    }
}

