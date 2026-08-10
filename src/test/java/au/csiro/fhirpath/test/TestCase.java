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
package au.csiro.fhirpath.test;

import au.csiro.fhirpath.test.assertion.Assertion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A single FHIRPath test case containing an expression and expected outcome.
 *
 * @param description Human-readable description of what this test verifies
 * @param expression The FHIRPath expression to evaluate
 * @param context Optional FHIRPath expression to use as %context (null if not used)
 * @param subject Optional test subject providing resource data and type (null for
 *     literal/context-only tests)
 * @param assertion The assertion that verifies the result
 */
record TestCase(
    @Nonnull String description,
    @Nonnull String expression,
    @Nullable Context context,
    @Nullable TestSubject subject,
    @Nonnull Assertion assertion) {}
