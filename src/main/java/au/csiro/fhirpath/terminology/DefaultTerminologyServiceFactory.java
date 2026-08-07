/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.terminology;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.Serial;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link DefaultTerminologyService} instances from a {@link TerminologyConfiguration}.
 *
 * <p>Only the configuration is serialized to Spark executors; the FHIR context, HTTP client, and
 * response cache are built lazily on each executor and memoised per JVM, keyed by configuration.
 * Without that memoisation every row would construct a fresh {@code FhirContext}, which takes on
 * the order of seconds.
 *
 * @param configuration the terminology server configuration
 */
public record DefaultTerminologyServiceFactory(@Nonnull TerminologyConfiguration configuration)
    implements TerminologyServiceFactory {

  @Serial private static final long serialVersionUID = 1L;

  private static final Logger log = LoggerFactory.getLogger(DefaultTerminologyServiceFactory.class);

  /**
   * Per-JVM terminology services, keyed by configuration. Static so that all UDF instances deployed
   * to the same executor share one client and one cache.
   */
  private static final Map<TerminologyConfiguration, TerminologyService> INSTANCES =
      new ConcurrentHashMap<>();

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
   * context, because the underlying HTTP client is configured per configuration — mutating a shared
   * context would leak those settings across configurations.
   */
  @Nonnull
  private static TerminologyService createService(
      @Nonnull final TerminologyConfiguration configuration) {
    final FhirContext fhirContext = FhirContext.forR4();
    final IRestfulClientFactory clientFactory = fhirContext.getRestfulClientFactory();
    // The terminology server's capability statement is not needed, and fetching it would add a
    // round trip to the first request on every executor.
    clientFactory.setServerValidationMode(ServerValidationModeEnum.NEVER);
    // Timeouts and retries are set on the raw HttpClient below rather than through the factory's
    // own setConnectTimeout()/setSocketTimeout(), which HAPI ignores once a custom client is
    // supplied via setHttpClient().
    clientFactory.setHttpClient(buildHttpClient(configuration));

    final IGenericClient client = clientFactory.newGenericClient(configuration.serverUrl());
    return new CachingTerminologyService(
        new DefaultTerminologyService(client),
        configuration.cacheMaxEntries(),
        configuration.cacheTtl());
  }

  /**
   * Builds the Apache HttpClient backing terminology requests, with retries on request failure —
   * mirroring Pathling's terminology client exactly, down to the retry handler.
   *
   * <p>{@link DefaultHttpRequestRetryHandler}'s two-argument constructor retries a fixed set of
   * {@code IOException}s, but its default {@code nonRetriableClasses} explicitly excludes {@code
   * InterruptedIOException} (so a socket timeout is <b>not</b> retried), {@code ConnectException}
   * (connection refused), {@code UnknownHostException} (DNS failure), {@code
   * NoRouteToHostException}, and {@code SSLException}. What it does retry is everything else
   * derived from {@code IOException} — for example a connection reset mid-response. Retrying only
   * happens before an HTTP response is received; a completed error response (a 4xx or 5xx status)
   * is never retried here — {@link DefaultTerminologyService#isUnresolvable} decides, from the
   * response itself, whether that is an unresolvable value set or a genuine failure.
   */
  @Nonnull
  private static CloseableHttpClient buildHttpClient(
      @Nonnull final TerminologyConfiguration configuration) {
    final RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(configuration.connectTimeoutMillis())
            .setSocketTimeout(configuration.socketTimeoutMillis())
            .build();
    final HttpClientBuilder clientBuilder =
        HttpClients.custom().setDefaultRequestConfig(requestConfig);
    if (configuration.retryEnabled()) {
      clientBuilder.setRetryHandler(new LoggingRequestRetryHandler(configuration.retryCount()));
    }
    return clientBuilder.build();
  }

  /**
   * A retry handler that logs each retry, so a flaky server is visible rather than silent.
   *
   * <p>Package-private, rather than a private nested class, so the exception classification
   * inherited from {@link DefaultHttpRequestRetryHandler} is directly testable — see the {@code
   * buildHttpClient} javadoc above for exactly which exceptions that is.
   */
  static final class LoggingRequestRetryHandler extends DefaultHttpRequestRetryHandler {

    LoggingRequestRetryHandler(final int retryCount) {
      super(retryCount, true);
    }

    @Override
    public boolean retryRequest(
        final IOException exception, final int executionCount, final HttpContext context) {
      log.debug("Problem issuing terminology request, retrying", exception);
      return super.retryRequest(exception, executionCount, context);
    }
  }
}
