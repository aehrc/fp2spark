package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.ResolvedSignature;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Generic IR node representing any FHIRPath function or operator.
 * Replaces specific operation classes (Add, Abs, etc.).
 *
 * The resolved signature is stored in the node, providing:
 * - Result type (via signature.resultType()) - statically resolved during construction
 * - Parameter types (for validation)
 * - Which overload was selected (for debugging/optimization)
 *
 * Type resolution happens exactly once during Operation construction via
 * ResolvedSignature.resolve(), converting ResultSpecs to concrete types.
 *
 * Examples:
 * - Operation("add", [leftIR, rightIR], resolvedSig)
 * - Operation("abs", [targetIR], resolvedSig)
 * - Operation("count", [collectionIR], resolvedSig) // now an Operation!
 */
public record Operation(
    @Nonnull String name,
    @Nonnull List<IRNode> args,
    @Nonnull ResolvedSignature signature
) implements IRNode {

    /**
     * Returns the result type from the resolved signature.
     * No recalculation needed - single source of truth.
     */
    @Override
    @Nonnull
    public Type getType() {
        return signature.resultType();
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
        return name + "(" + args + ") : " + getType();
    }
}
