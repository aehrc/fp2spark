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
package au.csiro.fhirpath.compat;

import org.junit.jupiter.api.Disabled;

/**
 * Tests for terminology functions.
 *
 * <p>Disabled because this test requires Pathling TerminologyService, SharedMocks,
 * TerminologyServiceHelpers, FhirTypedLiteral, and Spring context support which are not available
 * in fp2sql.
 */
@Disabled("Requires terminology service support")
public class TerminologyFunctionsDslTest extends CompatTestBase {}
