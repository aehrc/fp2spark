package com.example.fhirpath.ast;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Stream;

public record AstFunctionCall(String functionName, @Nullable AstNode target,
                              List<AstNode> arguments) implements AstNode {
    // Constructor for function calls without a target (traditional function calls)
    public AstFunctionCall(String functionName, List<AstNode> arguments) {
        this(functionName, null, arguments);
    }

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
    public AstFunctionCall withTarget(@Nonnull AstNode newTarget) {
        return new AstFunctionCall(this.functionName, newTarget, this.arguments);
    }
}
