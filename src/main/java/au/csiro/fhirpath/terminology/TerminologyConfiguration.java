package au.csiro.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import java.io.Serializable;
import java.time.Duration;

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
 * @param cacheTtl how long a validate-code result stays cached
 * @param retryEnabled whether to retry a request that failed for a possibly transient reason
 *     (connection refused, timeout, DNS failure) rather than a server response
 * @param retryCount how many times to retry such a request before giving up
 */
public record TerminologyConfiguration(
    @Nonnull String serverUrl,
    int connectTimeoutMillis,
    int socketTimeoutMillis,
    long cacheMaxEntries,
    @Nonnull Duration cacheTtl,
    boolean retryEnabled,
    int retryCount)
    implements Serializable {

  /** Default socket connection timeout, in milliseconds. */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;

  /** Default socket read timeout, in milliseconds. */
  public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 30_000;

  /** Default maximum number of cached validate-code results. Matches Pathling's default. */
  public static final long DEFAULT_CACHE_MAX_ENTRIES = 200_000L;

  /**
   * Default lifetime of a cached validate-code result. Matches Pathling's default fallback expiry —
   * Pathling additionally respects a server-provided expiry when present and revalidates via ETag,
   * neither of which fp2sql currently implements; see #288.
   */
  public static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);

  /** Whether transient request failures are retried by default. */
  public static final boolean DEFAULT_RETRY_ENABLED = true;

  /** Default number of retries for a transiently-failed request. */
  public static final int DEFAULT_RETRY_COUNT = 2;

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
    if (cacheTtl.isNegative() || cacheTtl.isZero()) {
      throw new IllegalArgumentException("Terminology cache TTL must be positive");
    }
    if (retryCount < 0) {
      throw new IllegalArgumentException("Terminology retry count must not be negative");
    }
  }

  /**
   * Creates a configuration for the given server URL using default timeouts, cache size, cache
   * lifetime, and retry policy.
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
        DEFAULT_CACHE_MAX_ENTRIES,
        DEFAULT_CACHE_TTL,
        DEFAULT_RETRY_ENABLED,
        DEFAULT_RETRY_COUNT);
  }
}
