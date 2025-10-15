package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * IR node representing a lambda expression (closure).
 * Used by functions like where(), select(), repeat() that take criteria/projection expressions.
 * <p>
 * The lambda body can reference special variables like $this (current element) and $index (position).
 * Parameter binding is handled dynamically during code generation - the Lambda doesn't store parameter
 * names or types, only the body expression.
 * <p>
 * Example: name.where(use = 'official')
 * - body: Operation("equals", [Traversal($this, "use"), Literal("official")])
 */
public record Lambda(
        @Nonnull IRNode body
) implements IRNode {

    /**
     * Returns the type of the lambda body (what it evaluates to).
     * Does NOT return LambdaType - that's only used in signatures for matching.
     * The OverloadResolver uses instanceof Lambda to detect lambda nodes.
     */
    @Override
    @Nonnull
    public Type getType() {
        return body.getType();
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitLambda(this);
    }

    @Override
    public String toString() {
        return "Lambda($this -> " + body + ")";
    }
}
