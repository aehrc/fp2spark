package com.example.fhirpath.ast;

import jakarta.annotation.Nonnull;

/**
 * Represents iteration variables in lambda expressions.
 * These are special variables that refer to the current iteration context:
 * - $this: current item being evaluated
 * - $index: position of current item in collection (0-based)
 * - $total: accumulated value in aggregate functions
 *
 * Unlike environment variables (%), iteration variables ($) are bound
 * within the scope of specific functions like where(), select(), aggregate().
 *
 * FHIRPath Spec: "These expressions may refer to the special $this and $index elements,
 * which represent the item from the input collection currently under evaluation,
 * and its index in the collection, respectively."
 */
public record AstIterationVariable(@Nonnull String name) implements AstNode {

    public static final String THIS = "$this";
    public static final String INDEX = "$index";
    public static final String TOTAL = "$total";

    public AstIterationVariable {
        if (!name.startsWith("$")) {
            throw new IllegalArgumentException("Iteration variable name must start with $: " + name);
        }
        // Validate it's one of the known iteration variables
        if (!name.equals(THIS) && !name.equals(INDEX) && !name.equals(TOTAL)) {
            throw new IllegalArgumentException("Unknown iteration variable: " + name +
                ". Valid variables are: $this, $index, $total");
        }
    }

    @Nonnull
    public static AstIterationVariable thisVariable() {
        return new AstIterationVariable(THIS);
    }

    @Nonnull
    public static AstIterationVariable indexVariable() {
        return new AstIterationVariable(INDEX);
    }

    @Nonnull
    public static AstIterationVariable totalVariable() {
        return new AstIterationVariable(TOTAL);
    }
}
