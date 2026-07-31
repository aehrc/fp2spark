package com.example.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.Serial;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory {@link TerminologyService} for tests, declaring value set membership up front
 * instead of contacting a terminology server.
 *
 * <p>Value sets not declared via the builder are reported as unresolvable — {@code validateCode}
 * returns null — which exercises the specification's empty-result path.
 *
 * <p>Serializable so that it can be captured by a Spark UDF and shipped to executors; the local
 * test SparkSession runs in-process, so declared memberships remain visible.
 *
 * <pre>{@code
 * MockTerminologyService.builder()
 *     .withMember(VS_URL, "http://loinc.org", "55915-3")
 *     .withEmptyValueSet(OTHER_VS_URL)
 *     .build();
 * }</pre>
 */
public final class MockTerminologyService implements TerminologyService, TerminologyServiceFactory {

  @Serial private static final long serialVersionUID = 1L;

  /** Members of each declared value set, keyed by URL, as {@code system|code} strings. */
  @Nonnull private final Map<String, Set<String>> membersByValueSet;

  private MockTerminologyService(@Nonnull final Map<String, Set<String>> membersByValueSet) {
    this.membersByValueSet = membersByValueSet;
  }

  /**
   * Creates a builder for a mock terminology service.
   *
   * @return a new builder
   */
  @Nonnull
  public static Builder builder() {
    return new Builder();
  }

  @Nullable
  @Override
  public Boolean validateCode(
      @Nonnull final String valueSetUrl,
      @Nonnull final String system,
      @Nonnull final String code,
      @Nullable final String version) {
    final Set<String> members = membersByValueSet.get(valueSetUrl);
    if (members == null) {
      // Undeclared value set: unresolvable, which yields an empty result.
      return null;
    }
    return members.contains(memberKey(system, code));
  }

  @Nonnull
  @Override
  public TerminologyService build() {
    return this;
  }

  /** Builds the membership key for a code. Version and display do not affect membership. */
  @Nonnull
  private static String memberKey(@Nonnull final String system, @Nonnull final String code) {
    return system + "|" + code;
  }

  /** Builds a {@link MockTerminologyService} by declaring value sets and their members. */
  public static final class Builder {

    @Nonnull private final Map<String, Set<String>> membersByValueSet = new HashMap<>();

    private Builder() {}

    /**
     * Declares that the given code is a member of the given value set. Also marks the value set as
     * resolvable.
     *
     * @param valueSetUrl the value set URL
     * @param system the code system of the member
     * @param code the code of the member
     * @return this builder
     */
    @Nonnull
    public Builder withMember(
        @Nonnull final String valueSetUrl,
        @Nonnull final String system,
        @Nonnull final String code) {
      membersByValueSet
          .computeIfAbsent(valueSetUrl, url -> new HashSet<>())
          .add(memberKey(system, code));
      return this;
    }

    /**
     * Declares a value set that resolves but contains no codes, so every membership test is false.
     *
     * @param valueSetUrl the value set URL
     * @return this builder
     */
    @Nonnull
    public Builder withEmptyValueSet(@Nonnull final String valueSetUrl) {
      membersByValueSet.computeIfAbsent(valueSetUrl, url -> new HashSet<>());
      return this;
    }

    /**
     * Builds the mock service.
     *
     * @return a mock terminology service, usable directly as a {@link TerminologyServiceFactory}
     */
    @Nonnull
    public MockTerminologyService build() {
      return new MockTerminologyService(new HashMap<>(membersByValueSet));
    }
  }
}
