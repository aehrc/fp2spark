package com.example.fhirpath;

import com.example.fhirpath.terminology.NoTerminologyService;
import com.example.fhirpath.terminology.TerminologyServiceFactory;
import jakarta.annotation.Nonnull;

/**
 * Configuration for {@link FhirPath#toColumn} and {@link FhirPath#generate}, beyond the expression
 * itself.
 *
 * <p>A single options type rather than a growing list of positional parameters, so that future
 * compile-time configuration (e.g. for #203, #273) adds a field here instead of another overload or
 * another parameter position on every {@code toColumn} call site. Mirrors Pathling's {@code
 * PathlingContext}, which solves the same problem for its own broader set of configuration.
 *
 * @param terminologyServiceFactory the factory used to reach a terminology server on executors, for
 *     expressions using terminology functions such as {@code memberOf()}
 */
public record CompilationOptions(@Nonnull TerminologyServiceFactory terminologyServiceFactory) {

  private static final CompilationOptions DEFAULTS =
      new CompilationOptions(NoTerminologyService.INSTANCE);

  /**
   * Returns the default options: no terminology server configured, so terminology functions report
   * every value set as unresolvable.
   *
   * @return the default compilation options
   */
  @Nonnull
  public static CompilationOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Returns a copy of these options with the given terminology service factory.
   *
   * @param terminologyServiceFactory the factory used to reach a terminology server on executors
   * @return a new options instance with that factory
   */
  @Nonnull
  public CompilationOptions withTerminologyServiceFactory(
      @Nonnull final TerminologyServiceFactory terminologyServiceFactory) {
    return new CompilationOptions(terminologyServiceFactory);
  }
}
