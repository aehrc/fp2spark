package com.example.fhirpath.terminology;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Optional;
import org.hl7.fhir.r4.model.Coding;

/**
 * Decorates a {@link TerminologyService} with a bounded in-memory cache.
 *
 * <p>Spark invokes the {@code member_of} UDF once per row, and real datasets repeat the same (value
 * set, coding) pairs heavily, so caching removes the overwhelming majority of network calls.
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
   * Wraps a terminology service with a cache of the given maximum size.
   *
   * @param delegate the service to delegate uncached requests to
   * @param maxEntries the maximum number of cached answers
   */
  public CachingTerminologyService(
      @Nonnull final TerminologyService delegate, final long maxEntries) {
    this.delegate = delegate;
    this.cache = Caffeine.newBuilder().maximumSize(maxEntries).build();
  }

  @Nullable
  @Override
  public Boolean validateCode(@Nonnull final String valueSetUrl, @Nonnull final Coding coding) {
    final CacheKey key =
        new CacheKey(valueSetUrl, coding.getSystem(), coding.getCode(), coding.getVersion());
    return cache
        .get(key, k -> Optional.ofNullable(delegate.validateCode(valueSetUrl, coding)))
        .orElse(null);
  }

  /**
   * Identifies a validate-code request. Only the fields that participate in the request are
   * included — {@code display} and {@code userSelected} do not affect membership.
   */
  private record CacheKey(
      @Nonnull String valueSetUrl,
      @Nonnull String system,
      @Nonnull String code,
      @Nullable String version) {}
}
