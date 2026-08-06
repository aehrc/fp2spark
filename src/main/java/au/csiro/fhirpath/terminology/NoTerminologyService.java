package au.csiro.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.Serial;

/**
 * The terminology service used when no terminology server has been configured.
 *
 * <p>Every membership test reports the value set as unresolvable, which the FHIR FHIRPath
 * specification maps to an empty result. This is deliberately not an error, so that expressions
 * containing {@code memberOf()} still compile and evaluate without a terminology server.
 *
 * <p><b>Consequence worth knowing:</b> because an empty result is falsy inside {@code where()}, an
 * expression such as {@code Observation.component.where(code.memberOf(url))} yields <em>no</em>
 * elements on an unconfigured build rather than failing loudly. Code generation therefore logs a
 * warning at each {@code memberOf()} call site compiled against this service, since an empty output
 * is otherwise indistinguishable from data that genuinely matched nothing. Supply a {@link
 * DefaultTerminologyServiceFactory} to get real answers.
 */
public final class NoTerminologyService implements TerminologyService, TerminologyServiceFactory {

  @Serial private static final long serialVersionUID = 1L;

  /** The singleton instance. */
  public static final NoTerminologyService INSTANCE = new NoTerminologyService();

  private NoTerminologyService() {}

  @Nullable
  @Override
  public Boolean validateCode(
      @Nonnull final String valueSetUrl,
      @Nonnull final String system,
      @Nonnull final String code,
      @Nullable final String version) {
    return null;
  }

  @Nonnull
  @Override
  public TerminologyService build() {
    return this;
  }

  /** Preserves the singleton identity across Java deserialization on Spark executors. */
  @Serial
  private Object readResolve() {
    return INSTANCE;
  }
}
