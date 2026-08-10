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
import java.io.Serial;

/**
 * Thrown when a terminology server cannot be reached or returns an error that is not a failure to
 * resolve the requested value set.
 *
 * <p>An unresolvable value set is <em>not</em> an error condition — it yields an empty result per
 * the FHIR FHIRPath specification. This exception is reserved for genuine failures, which surface
 * as Spark task failures rather than being silently absorbed into empty results.
 */
public class TerminologyServiceException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a terminology service exception.
   *
   * @param message the detail message
   * @param cause the underlying failure
   */
  public TerminologyServiceException(
      @Nonnull final String message, @Nonnull final Throwable cause) {
    super(message, cause);
  }
}
