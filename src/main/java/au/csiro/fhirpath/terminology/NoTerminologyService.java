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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.Serial;

/**
 * The terminology service used when no terminology server has been configured.
 *
 * <p>Every membership test reports the value set as unresolvable, which the FHIR FHIRPath
 * specification maps to an empty result. This is deliberately not an error, so that expressions
 * containing {@code memberOf()} still compile and evaluate without a terminology server.
 *
 * <p><b>Consequence worth knowing:</b> because an empty result is falsy inside {@code where()}, an
 * expression such as {@code Observation.component.where(code.memberOf(url))} yields <em>no</em>
 * elements on an unconfigured build rather than failing loudly. Code generation therefore logs a
 * warning at each {@code memberOf()} call site compiled against this service, since an empty output
 * is otherwise indistinguishable from data that genuinely matched nothing. Supply a {@link
 * DefaultTerminologyServiceFactory} to get real answers.
 */
public final class NoTerminologyService implements TerminologyService, TerminologyServiceFactory {

  @Serial private static final long serialVersionUID = 1L;

  /** The singleton instance. */
  public static final NoTerminologyService INSTANCE = new NoTerminologyService();

  private NoTerminologyService() {}

  @Nullable
  @Override
  public Boolean validateCode(
      @Nonnull final String valueSetUrl,
      @Nonnull final String system,
      @Nonnull final String code,
      @Nullable final String version) {
    return null;
  }

  @Nonnull
  @Override
  public TerminologyService build() {
    return this;
  }

  /** Preserves the singleton identity across Java deserialization on Spark executors. */
  @Serial
  private Object readResolve() {
    return INSTANCE;
  }
}
