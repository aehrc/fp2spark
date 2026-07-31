package com.example.fhirpath.terminology;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import jakarta.annotation.Nonnull;
import java.io.Serial;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds {@link DefaultTerminologyService} instances from a {@link TerminologyConfiguration}.
 *
 * <p>Only the configuration is serialized to Spark executors; the FHIR context, HTTP client, and
 * response cache are built lazily on each executor and memoised per JVM, keyed by configuration.
 * Without that memoisation every row would construct a fresh {@code FhirContext}, which takes on
 * the order of seconds.
 */
public class DefaultTerminologyServiceFactory implements TerminologyServiceFactory {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Per-JVM terminology services, keyed by configuration. Static so that all UDF instances deployed
   * to the same executor share one client and one cache.
   */
  private static final Map<TerminologyConfiguration, TerminologyService> INSTANCES =
      new ConcurrentHashMap<>();

  @Nonnull private final TerminologyConfiguration configuration;

  /**
   * Creates a factory for the given configuration.
   *
   * @param configuration the terminology server configuration
   */
  public DefaultTerminologyServiceFactory(@Nonnull final TerminologyConfiguration configuration) {
    this.configuration = configuration;
  }

  /**
   * Creates a factory for a terminology server at the given URL, using default timeouts and cache
   * size.
   *
   * @param serverUrl the base URL of the FHIR terminology server
   * @return a factory for that server
   */
  @Nonnull
  public static DefaultTerminologyServiceFactory forServer(@Nonnull final String serverUrl) {
    return new DefaultTerminologyServiceFactory(TerminologyConfiguration.of(serverUrl));
  }

  /**
   * Returns the configuration this factory builds services from.
   *
   * @return the terminology server configuration
   */
  @Nonnull
  public TerminologyConfiguration getConfiguration() {
    return configuration;
  }

  @Nonnull
  @Override
  public TerminologyService build() {
    return INSTANCES.computeIfAbsent(
        configuration, DefaultTerminologyServiceFactory::createService);
  }

  /**
   * Builds a cached, client-backed terminology service.
   *
   * <p>A fresh {@link FhirContext} is created per configuration rather than reusing a shared cached
   * context, because client timeouts are configured on the context's {@link IRestfulClientFactory}
   * — mutating a shared context would leak those settings across configurations.
   */
  @Nonnull
  private static TerminologyService createService(
      @Nonnull final TerminologyConfiguration configuration) {
    final FhirContext fhirContext = FhirContext.forR4();
    final IRestfulClientFactory clientFactory = fhirContext.getRestfulClientFactory();
    clientFactory.setConnectTimeout(configuration.connectTimeoutMillis());
    clientFactory.setSocketTimeout(configuration.socketTimeoutMillis());
    // The terminology server's capability statement is not needed, and fetching it would add a
    // round trip to the first request on every executor.
    clientFactory.setServerValidationMode(ServerValidationModeEnum.NEVER);

    final IGenericClient client = clientFactory.newGenericClient(configuration.serverUrl());
    return new CachingTerminologyService(
        new DefaultTerminologyService(client), configuration.cacheMaxEntries());
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof final DefaultTerminologyServiceFactory that
        && configuration.equals(that.configuration);
  }

  @Override
  public int hashCode() {
    return configuration.hashCode();
  }
}
