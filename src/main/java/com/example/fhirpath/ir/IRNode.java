package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import jakarta.annotation.Nonnull;

/**
 * Base interface for all IR nodes in the FHIRPath expression tree.
 * IRNodes are target-agnostic and represent typed FHIRPath expressions.
 *
 * Code generation is delegated to target-specific visitors via the accept() method.
 */
public sealed interface IRNode
    permits Operation, Literal, Traversal, Cast, Resource, CastToSystem,
            Union, Equals {

    /**
     * Returns the FHIRPath type of this expression.
     * For operations, this is the result type from the resolved signature.
     */
    @Nonnull
    Type getType();

    /**
     * Accepts a visitor for target-specific code generation.
     *
     * @param visitor The visitor to accept
     * @param <T> The return type of the visitor (e.g., Column for Spark, String for SQL)
     * @return The result of visiting this node
     */
    @Nonnull
    <T> T accept(@Nonnull IRNodeVisitor<T> visitor);

    /**
     * Returns whether this expression is singular (not a collection).
     * Default implementation delegates to type.
     */
    default boolean isSingular() {
        return !getType().isCollection();
    }

    /**
     * Legacy eval method for backward compatibility.
     * Delegates to SparkCodeGenerator visitor.
     * @deprecated Use accept(visitor) instead
     */
    @Deprecated
    default Column eval() {
        return accept(new com.example.fhirpath.codegen.SparkCodeGenerator());
    }
}

