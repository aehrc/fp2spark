package com.example.fhirpath.terminology;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TerminologyService} backed by a FHIR terminology server, using the {@code
 * ValueSet/$validate-code} operation.
 *
 * <p>Requests are issued as HTTP GET so that they are cacheable by intermediate proxies.
 *
 * @see <a href="https://www.hl7.org/fhir/R4/valueset-operation-validate-code.html">
 *     ValueSet/$validate-code</a>
 */
public class DefaultTerminologyService implements TerminologyService {

  private static final Logger log = LoggerFactory.getLogger(DefaultTerminologyService.class);

  /** Name of the FHIR operation invoked to test value set membership. */
  private static final String VALIDATE_CODE_OPERATION = "$validate-code";

  /**
   * Name of the output parameter carrying the membership answer. Per the operation definition this
   * parameter is mandatory in a successful response.
   */
  private static final String RESULT_PARAMETER = "result";

  @Nonnull private final IGenericClient client;

  /**
   * Creates a terminology service over the given FHIR REST client.
   *
   * @param client a client pointed at the terminology server's base URL
   */
  public DefaultTerminologyService(@Nonnull final IGenericClient client) {
    this.client = client;
  }

  @Nullable
  @Override
  public Boolean validateCode(
      @Nonnull final String valueSetUrl,
      @Nonnull final String system,
      @Nonnull final String code,
      @Nullable final String version) {
    final Parameters request = buildRequest(valueSetUrl, system, code, version);
    final Parameters response;
    try {
      response =
          client
              .operation()
              .onType(ValueSet.class)
              .named(VALIDATE_CODE_OPERATION)
              .withParameters(request)
              .useHttpGet()
              .returnResourceType(Parameters.class)
              .execute();
    } catch (final BaseServerResponseException e) {
      if (isUnresolvable(e)) {
        // The server could not resolve the value set URI. The FHIR FHIRPath specification requires
        // an empty result in this case, which this contract represents as null.
        log.debug("Value set could not be resolved: {}", valueSetUrl, e);
        return null;
      }
      throw new TerminologyServiceException(
          "Terminology server returned an error validating code against " + valueSetUrl, e);
    }
    return extractResult(response, valueSetUrl);
  }

  /**
   * Classifies a server error as a failure to resolve the value set, as opposed to a genuine
   * failure.
   *
   * <p>This distinction carries the specification's "cannot be resolved → empty" rule: a 4xx
   * response means the server rejected the request as we made it — most often because the value set
   * URI does not resolve, but the specification does not require finer-grained diagnosis than that.
   * Matches Pathling's {@code BaseTerminologyService.handleError}, including its treatment of
   * 401/403 as unresolvable rather than as a distinguished failure — see #283 for the risk that
   * carries (a rate-limited or misconfigured server can silently look like "no code is a member"
   * instead of failing the job). Anything else — a timeout, a 5xx — surfaces as a Spark task
   * failure.
   *
   * <p>Package-private so the classification is testable without an HTTP server, since which
   * exception HAPI maps a given status to is not something to discover after a dependency upgrade.
   */
  static boolean isUnresolvable(@Nonnull final BaseServerResponseException e) {
    return e.getStatusCode() / 100 == 4;
  }

  /**
   * Builds the {@code $validate-code} input parameters for a single coding.
   *
   * <p>Package-private so that the parameter names, which must match the operation definition, are
   * directly testable without an HTTP server.
   */
  @Nonnull
  static Parameters buildRequest(
      @Nonnull final String valueSetUrl,
      @Nonnull final String system,
      @Nonnull final String code,
      @Nullable final String version) {
    final Parameters request = new Parameters();
    request.addParameter().setName("url").setValue(new UriType(valueSetUrl));
    request.addParameter().setName("system").setValue(new UriType(system));
    request.addParameter().setName("code").setValue(new CodeType(code));
    if (version != null) {
      request.addParameter().setName("systemVersion").setValue(new StringType(version));
    }
    return request;
  }

  /**
   * Extracts the {@code result} output parameter. A response missing it is treated as unresolvable
   * rather than as a negative answer, so that a non-conformant server cannot silently turn into
   * "not a member".
   *
   * <p>Package-private so that response handling is testable without an HTTP server.
   */
  @Nullable
  static Boolean extractResult(
      @Nonnull final Parameters response, @Nonnull final String valueSetUrl) {
    if (!response.hasParameter(RESULT_PARAMETER)) {
      log.warn(
          "Terminology server response for {} omitted the '{}' parameter; treating the value set as"
              + " unresolvable",
          valueSetUrl,
          RESULT_PARAMETER);
      return null;
    }
    return response.getParameterBool(RESULT_PARAMETER);
  }
}
