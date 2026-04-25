package com.example.fhirpath.operation.signature;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * A resolved signature with concrete, statically-known result shape.
 *
 * <p>This is stored in Operation nodes after type resolution is complete. All type variables have
 * been substituted, and the result shape is concrete.
 *
 * @param parameterTypes the list of concrete parameter types
 * @param resultShape the concrete result shape (type + cardinality)
 * @param minArity the minimum number of arguments required
 */
public record ResolvedSignature(
    @Nonnull List<Type> parameterTypes, @Nonnull Shape resultShape, int minArity) {
  /** Convenience constructor for minimal signatures. */
  public ResolvedSignature(
      @Nonnull final List<Type> parameterTypes, @Nonnull final Shape resultShape) {
    this(parameterTypes, resultShape, parameterTypes.size());
  }

  /**
   * Returns the result type (element type of the result shape). Convenience method for backward
   * compatibility.
   */
  @Nonnull
  public Type resultType() {
    return resultShape.elementType();
  }

  /** Returns the number of parameters in this signature. */
  public int arity() {
    return parameterTypes.size();
  }

  /**
   * Create a resolved signature from a signature definition. The result shape may be static or
   * dynamic (computed from the resolved arguments).
   *
   * @param definition the signature definition
   * @param resolvedArgs the resolved arguments (needed for dynamic type resolution)
   * @return resolved signature with concrete result shape
   */
  @Nonnull
  public static ResolvedSignature fromDefinition(
      @Nonnull final SignatureDefinition definition,
      @Nonnull final List<com.example.fhirpath.ir.IRNode> resolvedArgs) {
    // Extract parameter types (without cardinality)
    final List<Type> paramTypes = definition.parameters().stream().map(ParamSpec::type).toList();

    // Resolve result shape (static or dynamic)
    final Shape resultShape = definition.resultSpec().resolve(resolvedArgs);

    return new ResolvedSignature(paramTypes, resultShape, definition.minArity());
  }
}
