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
package au.csiro.fhirpath.ast;

/**
 * Represents a binary operator expression in FHIRPath (e.g., {@code a + b}, {@code x = y}).
 *
 * @param operator the operator symbol (e.g., "+", "=", "and")
 * @param left the left operand
 * @param right the right operand
 */
public record AstBinaryOperator(String operator, AstNode left, AstNode right) implements AstNode {}
