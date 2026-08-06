package au.csiro.fhirpath.ast;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.stream.Stream;

/**
 * Represents a function call in a FHIRPath expression (e.g., {@code name.where(use = 'official')}).
 *
 * @param functionName the name of the function being called
 * @param target the target expression (receiver), or null for top-level calls
 * @param arguments the list of argument expressions
 */
public record AstFunctionCall(
    String functionName, @Nullable AstNode target, List<AstNode> arguments)
    implements WithTarget<AstFunctionCall> {
  /**
   * Constructor for function calls without a target (traditional function calls).
   *
   * @param functionName the function name
   * @param arguments the argument list
   */
  public AstFunctionCall(final String functionName, final List<AstNode> arguments) {
    this(functionName, null, arguments);
  }

  /** Returns a stream of all child nodes (target followed by arguments). */
  @Nonnull
  public Stream<AstNode> children() {
    Stream<AstNode> argsStream = arguments.stream();
    if (target != null) {
      argsStream = Stream.concat(Stream.of(target), argsStream);
    }
    return argsStream;
  }

  /**
   * Create a new AstFunctionCall with a different target.
   *
   * @param newTarget the new target node
   * @return a new AstFunctionCall instance with the updated target
   */
  @Nonnull
  @Override
  public AstFunctionCall withTarget(@Nonnull final AstNode newTarget) {
    return new AstFunctionCall(this.functionName, newTarget, this.arguments);
  }
}
