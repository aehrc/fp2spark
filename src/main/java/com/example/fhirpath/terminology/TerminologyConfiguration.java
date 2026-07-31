package com.example.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import java.io.Serializable;

/**
 * Configuration for a FHIR terminology server connection.
 *
 * <p>Authentication is not currently supported — the server must be reachable without credentials.
 *
 * @param serverUrl the base URL of the FHIR terminology server (e.g. {@code
 *     https://tx.ontoserver.csiro.au/fhir})
 * @param connectTimeoutMillis socket connection timeout, in milliseconds
 * @param socketTimeoutMillis socket read timeout, in milliseconds
 * @param cacheMaxEntries maximum number of validate-code results held in the per-JVM cache
 */
public record TerminologyConfiguration(
    @Nonnull String serverUrl,
    int connectTimeoutMillis,
    int socketTimeoutMillis,
    long cacheMaxEntries)
    implements Serializable {

  /** Default socket connection timeout, in milliseconds. */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;

  /** Default socket read timeout, in milliseconds. */
  public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 30_000;

  /** Default maximum number of cached validate-code results. */
  public static final long DEFAULT_CACHE_MAX_ENTRIES = 100_000L;

  /** Validates the configuration. */
  public TerminologyConfiguration {
    if (serverUrl.isBlank()) {
      throw new IllegalArgumentException("Terminology server URL must not be blank");
    }
    if (connectTimeoutMillis <= 0 || socketTimeoutMillis <= 0) {
      throw new IllegalArgumentException("Terminology timeouts must be positive");
    }
    if (cacheMaxEntries < 0) {
      throw new IllegalArgumentException("Terminology cache size must not be negative");
    }
  }

  /**
   * Creates a configuration for the given server URL using default timeouts and cache size.
   *
   * @param serverUrl the base URL of the FHIR terminology server
   * @return a configuration with default settings
   */
  @Nonnull
  public static TerminologyConfiguration of(@Nonnull final String serverUrl) {
    return new TerminologyConfiguration(
        serverUrl,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_SOCKET_TIMEOUT_MILLIS,
        DEFAULT_CACHE_MAX_ENTRIES);
  }
}
