package com.example.fhirpath.typing;

public record CollectionType(Type elementType) implements Type {

    // Constructor with flattening
    public CollectionType(Type elementType) {
        // Flatten: Collection<Collection<T>> becomes Collection<T>
        if (elementType instanceof CollectionType ct) {
            this.elementType = ct.elementType();
        } else {
            this.elementType = elementType;
        }
    }

    @Override
    public String getName() {
        return "Collection<" + elementType.getName() + ">";
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isComplex() {
        return false;
    }

    @Override
    public boolean isCollection() {
        return true;
    }

    @Override
    public Type effectiveType() {
        return elementType;
    }
}
