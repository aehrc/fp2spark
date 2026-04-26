package com.example.fhirpath.codegen.spark.udf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure-Java tests for {@link TemporalNormalize#normalize(String)}.
 *
 * <p>The normalize step underpins DateTime equality, comparison, and union-dedup. Output must be
 * deterministic across JVM default timezones, otherwise tests pass in CI (UTC) but the same
 * expression yields different results when the runtime is configured to a non-UTC zone — see issue
 * #117. Each test pins the JVM default to a non-UTC zone and asserts the same canonical UTC output
 * the UTC environment produces.
 */
class TemporalNormalizeTest {

  private TimeZone originalTimeZone;

  @BeforeEach
  void saveTimeZone() {
    originalTimeZone = TimeZone.getDefault();
  }

  @AfterEach
  void restoreTimeZone() {
    TimeZone.setDefault(originalTimeZone);
  }

  @Test
  void offsetLessDateTimeNormalizesToUtcRegardlessOfSystemTimezone() {
    final String expected = "2020-01-01T10:00:00.000000000";
    for (final String zoneId :
        new String[] {"UTC", "America/New_York", "Asia/Tokyo", "Pacific/Kiritimati"}) {
      TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
      assertEquals(
          expected,
          TemporalNormalize.normalize("2020-01-01T10:00:00"),
          "offset-less DateTime should normalize the same in zone " + zoneId);
    }
  }

  @Test
  void explicitUtcOffsetMatchesOffsetLessNormalization() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    assertEquals(
        TemporalNormalize.normalize("2020-01-01T10:00:00"),
        TemporalNormalize.normalize("2020-01-01T10:00:00+00:00"),
        "missing offset must be treated as UTC so it dedups against an explicit UTC offset");
  }

  @Test
  void explicitNonUtcOffsetConvertsToUtc() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    assertEquals(
        "2020-01-01T15:00:00.000000000",
        TemporalNormalize.normalize("2020-01-01T10:00:00-05:00"),
        "explicit -05:00 offset shifts to UTC, independent of JVM default");
  }

  @Test
  void nullInputReturnsNull() {
    assertNull(TemporalNormalize.normalize(null));
  }
}
