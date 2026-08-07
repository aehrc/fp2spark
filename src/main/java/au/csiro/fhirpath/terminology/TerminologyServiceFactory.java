package au.csiro.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import java.io.Serializable;

/**
 * Creates {@link TerminologyService} instances.
 *
 * <p>Spark UDFs are serialized and shipped to executors, so the terminology service cannot be
 * captured directly — a FHIR REST client is not serializable. Instead the UDF captures this
 * factory, which <em>is</em> serializable, and calls {@link #build()} on the executor.
 *
 * <p>{@link #build()} is invoked once per row evaluation, so implementations that construct
 * expensive resources (HTTP clients, {@code FhirContext}) MUST memoise them per JVM rather than
 * building afresh on each call.
 */
public interface TerminologyServiceFactory extends Serializable {

  /**
   * Returns a terminology service for use on the calling JVM.
   *
   * @return a terminology service, never null
   */
  @Nonnull
  TerminologyService build();
}
