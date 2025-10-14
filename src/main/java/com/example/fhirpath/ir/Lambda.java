package com.example.fhirpath.ir;

import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

import java.util.List;

/**
 * IR node representing a lambda expression (closure).
 * Used by functions like where(), select(), repeat() that take criteria/projection expressions.
 *
 * The lambda body can reference special variables like $this (current element) and $index (position).
 *
 * Example: name.where(use = 'official')
 * - parameters: ["$this"]
 * - body: Operation("equals", [Traversal($this, "use"), Literal("official")])
 * - parameterType: HumanName
 */
public record Lambda(
    @Nonnull List<String> parameters,
    @Nonnull IRNode body,
    @Nonnull Type parameterType
) implements IRNode {

    public Lambda {
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException("Lambda must have at least one parameter");
        }
    }

    /**
     * Returns the lambda's type signature (returnType only).
     * The parameter type is implicit and determined by the collection element type.
     * This allows the OverloadResolver to match against LambdaType signatures.
     */
    @Override
    @Nonnull
    public Type getType() {
        return new LambdaType(body.getType());
    }

    /**
     * Lambdas are always singular - they represent a function, not a collection.
     */
    @Override
    public boolean isSingular() {
        return true;
    }

    @Override
    @Nonnull
    public Column eval() {
        throw new UnsupportedOperationException(
            "Lambdas cannot be evaluated directly - they must be inlined at call site"
        );
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitLambda(this);
    }

    @Override
    public String toString() {
        return "Lambda(" + String.join(", ", parameters) + " -> " + body + ")";
    }
}
