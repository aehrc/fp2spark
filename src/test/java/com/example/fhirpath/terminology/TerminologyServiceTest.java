package com.example.fhirpath.terminology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnclassifiedServerFailureException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.NoHttpResponseException;
import org.apache.http.protocol.BasicHttpContext;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;

/**
 * Tests for the terminology service layer: response handling, request construction, caching, and
 * the serialization contract that lets a factory travel to Spark executors.
 *
 * <p>The HTTP interaction of {@link DefaultTerminologyService} is not covered here — only the
 * request it builds and the responses it interprets, both of which are exercised without a server.
 */
class TerminologyServiceTest {

  private static final String SERVER_URL = "http://example.org/fhir";
  private static final String VALUE_SET_URL = "http://example.org/ValueSet/vs";
  private static final String SYSTEM = "http://loinc.org";
  private static final String CODE = "55915-3";

  @Test
  void validateCodeRequestUsesOperationParameterNames() {
    final Parameters request =
        DefaultTerminologyService.buildRequest(VALUE_SET_URL, SYSTEM, CODE, "2.74");

    assertEquals(VALUE_SET_URL, request.getParameterValue("url").primitiveValue());
    assertEquals(SYSTEM, request.getParameterValue("system").primitiveValue());
    assertEquals(CODE, request.getParameterValue("code").primitiveValue());
    // The operation defines the code system version parameter as "systemVersion", not "version".
    assertEquals("2.74", request.getParameterValue("systemVersion").primitiveValue());
  }

  @Test
  void validateCodeRequestOmitsAbsentVersion() {
    final Parameters request =
        DefaultTerminologyService.buildRequest(VALUE_SET_URL, SYSTEM, CODE, null);

    assertFalse(request.hasParameter("systemVersion"));
  }

  @Test
  void resultParameterIsReturnedAsIs() {
    final Parameters affirmative = new Parameters();
    affirmative.addParameter().setName("result").setValue(new BooleanType(true));
    assertEquals(Boolean.TRUE, DefaultTerminologyService.extractResult(affirmative, VALUE_SET_URL));

    final Parameters negative = new Parameters();
    negative.addParameter().setName("result").setValue(new BooleanType(false));
    assertEquals(Boolean.FALSE, DefaultTerminologyService.extractResult(negative, VALUE_SET_URL));
  }

  @Test
  void responseWithoutResultParameterIsTreatedAsUnresolvable() {
    // A non-conformant response must not silently become "not a member".
    assertNull(DefaultTerminologyService.extractResult(new Parameters(), VALUE_SET_URL));
  }

  @Test
  void cacheServesRepeatedRequestsFromASingleDelegateCall() {
    final RecordingTerminologyService delegate = new RecordingTerminologyService(true);
    final CachingTerminologyService cached =
        new CachingTerminologyService(delegate, 100, Duration.ofHours(1));

    assertEquals(Boolean.TRUE, cached.validateCode(VALUE_SET_URL, SYSTEM, CODE, null));
    assertEquals(Boolean.TRUE, cached.validateCode(VALUE_SET_URL, SYSTEM, CODE, null));

    assertEquals(1, delegate.requests.size());
  }

  @Test
  void cacheRetainsUnresolvableAnswers() {
    // Without caching nulls, a mistyped value set URL would produce one request per row.
    final RecordingTerminologyService delegate = new RecordingTerminologyService(null);
    final CachingTerminologyService cached =
        new CachingTerminologyService(delegate, 100, Duration.ofHours(1));

    assertNull(cached.validateCode(VALUE_SET_URL, SYSTEM, CODE, null));
    assertNull(cached.validateCode(VALUE_SET_URL, SYSTEM, CODE, null));

    assertEquals(1, delegate.requests.size());
  }

  @Test
  void cacheDistinguishesCodesValueSetsAndVersions() {
    final RecordingTerminologyService delegate = new RecordingTerminologyService(true);
    final CachingTerminologyService cached =
        new CachingTerminologyService(delegate, 100, Duration.ofHours(1));

    cached.validateCode(VALUE_SET_URL, SYSTEM, CODE, null);
    cached.validateCode(VALUE_SET_URL, SYSTEM, "99999-9", null);
    cached.validateCode("http://example.org/ValueSet/other", SYSTEM, CODE, null);
    cached.validateCode(VALUE_SET_URL, SYSTEM, CODE, "2.74");

    assertEquals(4, delegate.requests.size());
  }

  @Test
  void noTerminologyServiceReportsEveryValueSetAsUnresolvable() {
    assertNull(
        NoTerminologyService.INSTANCE.build().validateCode(VALUE_SET_URL, SYSTEM, CODE, null));
  }

  @Test
  void noTerminologyServiceSurvivesSerializationAsASingleton() throws Exception {
    // Spark serializes the factory to executors; the singleton identity must hold on the far side.
    assertSame(NoTerminologyService.INSTANCE, roundTrip(NoTerminologyService.INSTANCE));
  }

  @Test
  void defaultFactorySerializesAndRebuildsAnEquivalentService() throws Exception {
    final DefaultTerminologyServiceFactory factory =
        DefaultTerminologyServiceFactory.forServer(SERVER_URL);

    final DefaultTerminologyServiceFactory deserialized = roundTrip(factory);

    assertEquals(factory, deserialized);
    // Services are memoised per JVM per configuration, so an equal factory yields the same instance
    // — this is what keeps one HTTP client and one cache per executor.
    assertSame(factory.build(), deserialized.build());
  }

  @Test
  void any4xxMeansTheValueSetCouldNotBeResolved() {
    // This classification carries the spec's "cannot be resolved -> empty" rule. Any 4xx is treated
    // as a rejection of the request as made — most often an unresolvable value set URI, but the
    // specification does not require finer diagnosis. Matches Pathling's BaseTerminologyService
    // exactly, including 401/403 — see #283 for the risk that carries.
    assertTrue(
        DefaultTerminologyService.isUnresolvable(new ResourceNotFoundException("not found")));
    assertTrue(DefaultTerminologyService.isUnresolvable(new InvalidRequestException("bad url")));
    assertTrue(
        DefaultTerminologyService.isUnresolvable(new UnprocessableEntityException("bad code")));
    assertTrue(
        DefaultTerminologyService.isUnresolvable(
            new AuthenticationException("credentials needed")));
    assertTrue(
        DefaultTerminologyService.isUnresolvable(new ForbiddenOperationException("not allowed")));
  }

  @Test
  void otherServerErrorsAreGenuineFailures() {
    // A server that is down or erroring must not look like a code that is simply not a member.
    assertFalse(
        DefaultTerminologyService.isUnresolvable(new InternalErrorException("server exploded")));
    assertFalse(
        DefaultTerminologyService.isUnresolvable(
            new UnclassifiedServerFailureException(503, "busy")));
  }

  @Test
  void retryHandlerRetriesGenericIoFailuresButNotConnectionLevelOnes() {
    // Pins DefaultHttpRequestRetryHandler's default nonRetriableClasses, since the retry handler's
    // whole purpose depends on which exceptions actually get retried — see the buildHttpClient
    // javadoc on DefaultTerminologyServiceFactory for why this list is exactly this and not, say,
    // "everything that looks like a connection problem".
    final DefaultTerminologyServiceFactory.LoggingRequestRetryHandler handler =
        new DefaultTerminologyServiceFactory.LoggingRequestRetryHandler(2);
    final BasicHttpContext context = new BasicHttpContext();

    assertFalse(handler.retryRequest(new SocketTimeoutException(), 1, context), "timeout");
    assertFalse(handler.retryRequest(new ConnectException(), 1, context), "connection refused");
    assertFalse(handler.retryRequest(new UnknownHostException(), 1, context), "DNS failure");
    assertFalse(handler.retryRequest(new NoRouteToHostException(), 1, context));
    assertTrue(
        handler.retryRequest(new NoHttpResponseException("connection reset"), 1, context),
        "a generic IOException not in the exclusion list is retried");
  }

  @Test
  void retryHandlerStopsAfterTheConfiguredCount() {
    final DefaultTerminologyServiceFactory.LoggingRequestRetryHandler handler =
        new DefaultTerminologyServiceFactory.LoggingRequestRetryHandler(2);
    final BasicHttpContext context = new BasicHttpContext();
    final NoHttpResponseException retryable = new NoHttpResponseException("connection reset");

    assertTrue(handler.retryRequest(retryable, 1, context));
    assertTrue(handler.retryRequest(retryable, 2, context));
    assertFalse(handler.retryRequest(retryable, 3, context), "exceeds the configured retry count");
  }

  @Test
  void configurationRejectsBlankServerUrl() {
    assertThrows(IllegalArgumentException.class, () -> TerminologyConfiguration.of("  "));
  }

  @Test
  void configurationRejectsNonPositiveTimeouts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TerminologyConfiguration(SERVER_URL, 0, 1000, 10, Duration.ofHours(1), true, 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TerminologyConfiguration(SERVER_URL, 1000, 0, 10, Duration.ofHours(1), true, 2));
  }

  @Test
  void configurationRejectsNegativeCacheSize() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TerminologyConfiguration(SERVER_URL, 1000, 1000, -1, Duration.ofHours(1), true, 2));
  }

  @Test
  void configurationRejectsNonPositiveCacheTtl() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TerminologyConfiguration(SERVER_URL, 1000, 1000, 10, Duration.ZERO, true, 2));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TerminologyConfiguration(
                SERVER_URL, 1000, 1000, 10, Duration.ofSeconds(-1), true, 2));
  }

  @Test
  void configurationRejectsNegativeRetryCount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TerminologyConfiguration(
                SERVER_URL, 1000, 1000, 10, Duration.ofHours(1), true, -1));
  }

  @SuppressWarnings("unchecked")
  private static <T> T roundTrip(@Nonnull final T value)
      throws IOException, ClassNotFoundException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(value);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) in.readObject();
    }
  }

  /** A terminology service that records every request and returns a fixed answer. */
  private static final class RecordingTerminologyService implements TerminologyService {

    private final List<String> requests = new ArrayList<>();

    @Nullable private final Boolean answer;

    private RecordingTerminologyService(@Nullable final Boolean answer) {
      this.answer = answer;
    }

    @Nullable
    @Override
    public Boolean validateCode(
        @Nonnull final String valueSetUrl,
        @Nonnull final String system,
        @Nonnull final String code,
        @Nullable final String version) {
      requests.add(String.join("|", valueSetUrl, system, code, String.valueOf(version)));
      return answer;
    }
  }
}
