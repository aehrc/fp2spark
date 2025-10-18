package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * IR node representing $this reference in lambda expressions.
 * $this refers to the current element being evaluated in collection operations like where() and select().
 *
 * <p>Example: name.where(use = 'official')
 * <pre>
 * The expression "use = 'official'" is analyzed as:
 *   Operation("equals", [Traversal(ThisReference(HumanName), "use"), Literal("official")])
 * ThisReference holds the type of the collection element (HumanName)
 * </pre>
 *
 * <p>During code generation, ThisReference is substituted with the actual element variable
 * passed to the Spark lambda function.
 *
 * <p>$this always has single cardinality (refers to one element at a time).
 */
public record ThisReference(
    @Nonnull Type type
) implements IRNode {

    @Override
    @Nonnull
    public Shape getShape() {
        // $this always refers to a single element in the iteration
        return Shape.single(type);
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
