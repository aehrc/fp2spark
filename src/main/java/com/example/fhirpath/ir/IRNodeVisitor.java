package com.example.fhirpath.ir;

import javax.annotation.Nonnull;

/**
 * Visitor interface for traversing and transforming IR trees.
 *
 * Different visitor implementations enable different target backends:
 * - SparkCodeGenerator: Generates Spark Column expressions
 * - SqlServerCodeGenerator: Generates SQL Server T-SQL strings
 * - PostgreSqlCodeGenerator: Generates PostgreSQL SQL strings
 * - DebugVisitor: Generates human-readable string representation
 * - ValidationVisitor: Validates IR tree correctness
 *
 * @param <T> The return type of visit methods (e.g., Column, String, etc.)
 */
public interface IRNodeVisitor<T> {

    /**
     * Visit a generic operation node (function call or operator).
     */
    @Nonnull
    T visitOperation(@Nonnull Operation node);

    /**
     * Visit a literal constant value.
     */
    @Nonnull
    T visitLiteral(@Nonnull Literal node);

    /**
     * Visit a field traversal (e.g., Patient.name).
     */
    @Nonnull
    T visitTraversal(@Nonnull Traversal node);

    /**
     * Visit a type cast operation.
     */
    @Nonnull
    T visitCast(@Nonnull Cast node);

    /**
     * Visit a resource root reference.
     */
    @Nonnull
    T visitResource(@Nonnull Resource node);

    /**
     * Visit a getValue() operation (extract value from FHIR type).
     */
    @Nonnull
    T visitCastToSystem(@Nonnull CastToSystem node);

    /**
     * Visit a union operation (collection union).
     */
    @Nonnull
    T visitUnion(@Nonnull Union node);

    /**
     * Visit an equals comparison operation.
     */
    @Nonnull
    T visitEquals(@Nonnull Equals node);
}
