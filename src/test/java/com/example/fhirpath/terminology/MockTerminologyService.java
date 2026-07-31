package com.example.fhirpath.terminology;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
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
 * <p>Serializable so that it can be captured by a Spark UDF and shipped to executors. Note that
 * Spark serializes task closures even in local mode, so the instance a UDF uses is a <em>copy</em>:
 * this class is therefore deliberately immutable, and tests must assert on expression results
 * rather than on state recorded here.
 *
 * <pre>{@code
 * MockTerminologyService.builder()
 *     .withMember(VS_URL, "http://loinc.org", "55915-3")
 *     .withVersionedMember(OTHER_VS, "http://loinc.org", "55915-3", "2.74")
 *     .withEmptyValueSet(EMPTY_VS)
 *     .build();
 * }</pre>
 */
public final class MockTerminologyService implements TerminologyService, TerminologyServiceFactory {

  @Serial private static final long serialVersionUID = 1L;

  /** The declared contents of each resolvable value set, keyed by URL. */
  @Nonnull private final Map<String, ValueSetContents> valueSets;

  private MockTerminologyService(@Nonnull final Map<String, ValueSetContents> valueSets) {
    this.valueSets = valueSets;
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
    final ValueSetContents contents = valueSets.get(valueSetUrl);
    if (contents == null) {
      // Undeclared value set: unresolvable, which yields an empty result.
      return null;
    }
    return contents.contains(system, code, version);
  }

  @Nonnull
  @Override
  public TerminologyService build() {
    return this;
  }

  /**
   * The declared contents of one value set.
   *
   * @param anyVersion members that match regardless of the requested code system version
   * @param exactVersion members that match only at a specific code system version
   */
  private record ValueSetContents(
      @Nonnull Set<String> anyVersion, @Nonnull Set<String> exactVersion) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    boolean contains(
        @Nonnull final String system, @Nonnull final String code, @Nullable final String version) {
      return anyVersion.contains(key(system, code))
          || exactVersion.contains(key(system, code) + "|" + version);
    }

    @Nonnull
    static String key(@Nonnull final String system, @Nonnull final String code) {
      return system + "|" + code;
    }
  }

  /** Builds a {@link MockTerminologyService} by declaring value sets and their members. */
  public static final class Builder {

    @Nonnull private final Map<String, ValueSetContents> valueSets = new HashMap<>();

    private Builder() {}

    /**
     * Declares that the given code is a member of the given value set, at any code system version.
     * Also marks the value set as resolvable.
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
      contentsOf(valueSetUrl).anyVersion().add(ValueSetContents.key(system, code));
      return this;
    }

    /**
     * Declares that the given code is a member of the given value set <em>only</em> at the given
     * code system version.
     *
     * <p>Lets a test prove that the version argument reaches the terminology service and lands in
     * the right slot, which version-insensitive membership cannot show.
     *
     * @param valueSetUrl the value set URL
     * @param system the code system of the member
     * @param code the code of the member
     * @param version the only code system version at which the code is a member
     * @return this builder
     */
    @Nonnull
    public Builder withVersionedMember(
        @Nonnull final String valueSetUrl,
        @Nonnull final String system,
        @Nonnull final String code,
        @Nonnull final String version) {
      contentsOf(valueSetUrl)
          .exactVersion()
          .add(ValueSetContents.key(system, code) + "|" + version);
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
      contentsOf(valueSetUrl);
      return this;
    }

    @Nonnull
    private ValueSetContents contentsOf(@Nonnull final String valueSetUrl) {
      return valueSets.computeIfAbsent(
          valueSetUrl, url -> new ValueSetContents(new HashSet<>(), new HashSet<>()));
    }

    /**
     * Builds the mock service.
     *
     * @return a mock terminology service, usable directly as a {@link TerminologyServiceFactory}
     */
    @Nonnull
    public MockTerminologyService build() {
      return new MockTerminologyService(new HashMap<>(valueSets));
    }
  }
}
