package com.example.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.hl7.fhir.r4.model.Coding;

/**
 * A terminology service capable of answering ValueSet membership questions.
 *
 * <p>Instances are created on Spark executors via a {@link TerminologyServiceFactory} and are
 * expected to be usable from multiple threads concurrently.
 */
public interface TerminologyService {

  /**
   * Tests whether a coding is a member of a value set.
   *
   * <p>The tri-state return models the FHIRPath {@code memberOf()} contract: the FHIR FHIRPath
   * specification requires an <em>empty</em> result when the value set URI cannot be resolved,
   * which is distinct from a resolvable value set that simply does not contain the code.
   *
   * @param valueSetUrl the canonical URL of the value set to test against
   * @param coding the coding to test; implementations may assume {@code system} and {@code code}
   *     are both non-null
   * @return {@code true} if the coding is a member, {@code false} if it is not, or {@code null} if
   *     the value set could not be resolved
   */
  @Nullable
  Boolean validateCode(@Nonnull String valueSetUrl, @Nonnull Coding coding);
}
