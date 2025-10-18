package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.ResolvedSignature;
import com.example.fhirpath.typing.Shape;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Generic IR node representing any FHIRPath function or operator.
 * Replaces specific operation classes (Add, Abs, etc.).
 *
 * <p>The resolved signature is stored in the node, providing:
 * <ul>
 *   <li>Result shape (via signature.resultShape()) - statically resolved during construction</li>
 *   <li>Parameter types (for validation)</li>
 *   <li>Which overload was selected (for debugging/optimization)</li>
 * </ul>
 *
 * <p>Type resolution happens exactly once during Operation construction via
 * ResolvedSignature.resolve(), converting ResultSpecs to concrete shapes.
 *
 * <p>Examples:
 * <ul>
 *   <li>Operation("add", [leftIR, rightIR], resolvedSig)</li>
 *   <li>Operation("abs", [targetIR], resolvedSig)</li>
 *   <li>Operation("count", [collectionIR], resolvedSig)</li>
 * </ul>
 */
public record Operation(
    @Nonnull String name,
    @Nonnull List<IRNode> args,
    @Nonnull ResolvedSignature signature
) implements IRNode {

    /**
     * Returns the result shape from the resolved signature.
     * No recalculation needed - single source of truth.
     */
    @Override
    @Nonnull
    public Shape getShape() {
        return signature.resultShape();
    }

    /**
     * Accepts a visitor for target-specific code generation.
     */
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitOperation(this);
    }

    /**
     * Convenience method to get argument count.
     */
    public int arity() {
        return args.size();
    }

    @Override
    public String toString() {
        return name + "(" + args + ") : " + getShape();
    }
}
