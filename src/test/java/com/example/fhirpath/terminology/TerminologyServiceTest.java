package com.example.fhirpath.terminology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Coding;
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

  private static final String VALUE_SET_URL = "http://example.org/ValueSet/vs";
  private static final String SYSTEM = "http://loinc.org";
  private static final String CODE = "55915-3";

  @Nonnull
  private static Coding coding() {
    return new Coding().setSystem(SYSTEM).setCode(CODE);
  }

  @Test
  void validateCodeRequestUsesOperationParameterNames() {
    final Parameters request =
        DefaultTerminologyService.buildRequest(VALUE_SET_URL, coding().setVersion("2.74"));

    assertEquals(VALUE_SET_URL, request.getParameterValue("url").primitiveValue());
    assertEquals(SYSTEM, request.getParameterValue("system").primitiveValue());
    assertEquals(CODE, request.getParameterValue("code").primitiveValue());
    // The operation defines the code system version parameter as "systemVersion", not "version".
    assertEquals("2.74", request.getParameterValue("systemVersion").primitiveValue());
  }

  @Test
  void validateCodeRequestOmitsAbsentVersion() {
    final Parameters request = DefaultTerminologyService.buildRequest(VALUE_SET_URL, coding());

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
    final CachingTerminologyService cached = new CachingTerminologyService(delegate, 100);

    assertEquals(Boolean.TRUE, cached.validateCode(VALUE_SET_URL, coding()));
    assertEquals(Boolean.TRUE, cached.validateCode(VALUE_SET_URL, coding()));

    assertEquals(1, delegate.requests.size());
  }

  @Test
  void cacheRetainsUnresolvableAnswers() {
    // Without caching nulls, a mistyped value set URL would produce one request per row.
    final RecordingTerminologyService delegate = new RecordingTerminologyService(null);
    final CachingTerminologyService cached = new CachingTerminologyService(delegate, 100);

    assertNull(cached.validateCode(VALUE_SET_URL, coding()));
    assertNull(cached.validateCode(VALUE_SET_URL, coding()));

    assertEquals(1, delegate.requests.size());
  }

  @Test
  void cacheDistinguishesCodesAndValueSets() {
    final RecordingTerminologyService delegate = new RecordingTerminologyService(true);
    final CachingTerminologyService cached = new CachingTerminologyService(delegate, 100);

    cached.validateCode(VALUE_SET_URL, coding());
    cached.validateCode(VALUE_SET_URL, coding().setCode("99999-9"));
    cached.validateCode("http://example.org/ValueSet/other", coding());
    cached.validateCode(VALUE_SET_URL, coding().setVersion("2.74"));

    assertEquals(4, delegate.requests.size());
  }

  @Test
  void noTerminologyServiceReportsEveryValueSetAsUnresolvable() {
    assertNull(NoTerminologyService.INSTANCE.build().validateCode(VALUE_SET_URL, coding()));
  }

  @Test
  void noTerminologyServiceSurvivesSerializationAsASingleton() throws Exception {
    // Spark serializes the factory to executors; the singleton identity must hold on the far side.
    assertSame(NoTerminologyService.INSTANCE, roundTrip(NoTerminologyService.INSTANCE));
  }

  @Test
  void defaultFactorySerializesAndRebuildsAnEquivalentService() throws Exception {
    final DefaultTerminologyServiceFactory factory =
        DefaultTerminologyServiceFactory.forServer("http://example.org/fhir");

    final DefaultTerminologyServiceFactory deserialized = roundTrip(factory);

    assertEquals(factory, deserialized);
    assertEquals(factory.getConfiguration(), deserialized.getConfiguration());
    // Services are memoised per JVM per configuration, so an equal factory yields the same instance
    // — this is what keeps one HTTP client and one cache per executor.
    assertSame(factory.build(), deserialized.build());
  }

  @Test
  void configurationRejectsInvalidSettings() {
    assertTrue(
        throwsIllegalArgument(() -> TerminologyConfiguration.of("  ")),
        "blank server URL rejected");
    assertTrue(
        throwsIllegalArgument(
            () -> new TerminologyConfiguration("http://example.org/fhir", 0, 1000, 10)),
        "non-positive connect timeout rejected");
    assertTrue(
        throwsIllegalArgument(
            () -> new TerminologyConfiguration("http://example.org/fhir", 1000, 1000, -1)),
        "negative cache size rejected");
  }

  private static boolean throwsIllegalArgument(@Nonnull final Runnable action) {
    try {
      action.run();
      return false;
    } catch (final IllegalArgumentException e) {
      return true;
    }
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
    public Boolean validateCode(@Nonnull final String valueSetUrl, @Nonnull final Coding coding) {
      requests.add(valueSetUrl + " " + coding.getSystem() + "|" + coding.getCode());
      return answer;
    }
  }
}
