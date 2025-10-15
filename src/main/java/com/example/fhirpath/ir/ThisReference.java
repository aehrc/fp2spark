package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * IR node representing $this reference in lambda expressions.
 * $this refers to the current element being evaluated in collection operations like where() and select().
 *
 * Example: name.where(use = 'official')
 * - The expression "use = 'official'" is analyzed as:
 *   Operation("equals", [Traversal(ThisReference(HumanName), "use"), Literal("official")])
 * - ThisReference holds the type of the collection element (HumanName)
 *
 * During code generation, ThisReference is substituted with the actual element variable
 * passed to the Spark lambda function.
 */
public record ThisReference(
    @Nonnull Type type
) implements IRNode {

    @Override
    @Nonnull
    public Type getType() {
        return type;
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitThisReference(this);
    }

    @Override
    public String toString() {
        return "$this : " + type;
    }
}
