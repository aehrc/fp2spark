package com.example.fhirpath.operation.signature;

import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Factory for creating type mappings with a fluent DSL.
 *
 * <p>Enables the pattern: forTypes(set1, set2).define(Signatures::binaryOp)
 *
 * <p>This class is intentionally minimal - all pattern knowledge lives in the Signatures class, not
 * here.
 */
public final class TypeGroups {

  private TypeGroups() {
    throw new AssertionError("No instances");
  }

  /**
   * Start building a type mapping for the given type sets. Multiple sets are combined into a single
   * set.
   *
   * @param typeSets one or more type sets to map over
   * @return builder for defining the mapping function
   */
  @SafeVarargs
  @Nonnull
  public static ForTypesBuilder forTypes(@Nonnull final Collection<Type>... typeSets) {
    final Set<Type> combined =
        Stream.of(typeSets)
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return new ForTypesBuilder(combined);
  }

  /**
   * Builder for defining the mapping function.
   *
   * <p>Example: forTypes(NUMERIC).define(Signatures::unaryOp)
   */
  public static final class ForTypesBuilder {
    private final Set<Type> types;

    private ForTypesBuilder(@Nonnull final Set<Type> types) {
      this.types = types;
    }

    /**
     * Define the signature mapping function.
     *
     * @param mapper function that takes a Type and returns a SignatureDefinition
     * @return type mapping that can be expanded into signatures
     */
    @Nonnull
    public TypeMapping define(@Nonnull final Function<Type, SignatureDefinition> mapper) {
      return new TypeMapping(types, mapper);
    }
  }
}
