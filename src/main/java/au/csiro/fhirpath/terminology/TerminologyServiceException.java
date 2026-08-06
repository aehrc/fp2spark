package au.csiro.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import java.io.Serial;

/**
 * Thrown when a terminology server cannot be reached or returns an error that is not a failure to
 * resolve the requested value set.
 *
 * <p>An unresolvable value set is <em>not</em> an error condition — it yields an empty result per
 * the FHIR FHIRPath specification. This exception is reserved for genuine failures, which surface
 * as Spark task failures rather than being silently absorbed into empty results.
 */
public class TerminologyServiceException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a terminology service exception.
   *
   * @param message the detail message
   * @param cause the underlying failure
   */
  public TerminologyServiceException(
      @Nonnull final String message, @Nonnull final Throwable cause) {
    super(message, cause);
  }
}
