package au.csiro.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A terminology service capable of answering ValueSet membership questions.
 *
 * <p>Instances are created on Spark executors via a {@link TerminologyServiceFactory} and are
 * expected to be usable from multiple threads concurrently.
 *
 * <p>A concept is identified by its parts rather than by a HAPI {@code Coding}, because this method
 * is called once per row per coding and every caching implementation keys on exactly these strings.
 * Materialising a {@code Coding} is left to the implementations that actually need one.
 */
public interface TerminologyService {

  /**
   * Tests whether a code is a member of a value set.
   *
   * <p>The tri-state return models the FHIRPath {@code memberOf()} contract: the FHIR FHIRPath
   * specification requires an <em>empty</em> result when the value set URI cannot be resolved,
   * which is distinct from a resolvable value set that simply does not contain the code.
   *
   * @param valueSetUrl the canonical URL of the value set to test against
   * @param system the code system of the code to test
   * @param code the code to test
   * @param version the code system version, or null to let the server choose
   * @return {@code true} if the code is a member, {@code false} if it is not, or {@code null} if
   *     the value set could not be resolved
   */
  @Nullable
  Boolean validateCode(
      @Nonnull String valueSetUrl,
      @Nonnull String system,
      @Nonnull String code,
      @Nullable String version);
}
