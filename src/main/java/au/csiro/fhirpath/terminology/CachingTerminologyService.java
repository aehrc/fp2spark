package au.csiro.fhirpath.terminology;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.Optional;

/**
 * Decorates a {@link TerminologyService} with a bounded in-memory cache.
 *
 * <p>Spark invokes the membership UDF once per row per coding, and real datasets repeat the same
 * (value set, code) pairs heavily, so caching removes the overwhelming majority of network calls.
 *
 * <p>Unresolvable value sets are cached alongside genuine answers, so that a mistyped URL does not
 * produce one request per row.
 */
public class CachingTerminologyService implements TerminologyService {

  @Nonnull private final TerminologyService delegate;

  /**
   * Cached answers keyed by the full request. Values are wrapped in {@link Optional} because the
   * cache cannot store nulls, and {@code null} is a meaningful answer here (unresolvable value
   * set).
   */
  @Nonnull private final Cache<CacheKey, Optional<Boolean>> cache;

  /**
   * Wraps a terminology service with a bounded, expiring cache.
   *
   * <p>Entries expire as well as evict because unresolvable answers are cached too: without expiry,
   * a value set that is absent when a long-running Spark application starts would stay
   * "unresolvable" for the lifetime of the JVM even after it is loaded onto the server.
   *
   * @param delegate the service to delegate uncached requests to
   * @param maxEntries the maximum number of cached answers
   * @param ttl how long an answer stays cached
   */
  public CachingTerminologyService(
      @Nonnull final TerminologyService delegate,
      final long maxEntries,
      @Nonnull final Duration ttl) {
    this.delegate = delegate;
    this.cache = Caffeine.newBuilder().maximumSize(maxEntries).expireAfterWrite(ttl).build();
  }

  @Nullable
  @Override
  public Boolean validateCode(
      @Nonnull final String valueSetUrl,
      @Nonnull final String system,
      @Nonnull final String code,
      @Nullable final String version) {
    final CacheKey key = new CacheKey(valueSetUrl, system, code, version);
    return cache
        .get(
            key,
            k -> Optional.ofNullable(delegate.validateCode(valueSetUrl, system, code, version)))
        .orElse(null);
  }

  /**
   * Identifies a validate-code request. Only the parts that participate in the request are included
   * — a coding's {@code display} and {@code userSelected} do not affect membership.
   */
  private record CacheKey(
      @Nonnull String valueSetUrl,
      @Nonnull String system,
      @Nonnull String code,
      @Nullable String version) {}
}
