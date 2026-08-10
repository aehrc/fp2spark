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
package au.csiro.fhirpath.operation;

import jakarta.annotation.Nonnull;
import java.util.Map;

/**
 * Utility for normalizing operator symbols to their canonical function names.
 *
 * <p>FHIRPath supports both symbolic operators (+, -, *, /, etc.) and named functions (add,
 * subtract, multiply, divide, etc.). This class provides a centralized mapping from operator
 * symbols to their canonical names used in the operation registry.
 *
 * <p>Example mappings:
 *
 * <ul>
 *   <li>{@code "+"} → {@code "add"}
 *   <li>{@code ">"} → {@code "gt"}
 *   <li>{@code "="} → {@code "equals"}
 *   <li>{@code "!="} → {@code "notEquals"}
 *   <li>{@code "|"} → {@code "union"} (merge with deduplication)
 *   <li>{@code ";"} → {@code "combine"} (ordered concatenation)
 *   <li>{@code "in"} → {@code "in"} (membership)
 *   <li>{@code "contains"} → {@code "memberContains"} (membership, disambiguated from string
 *       contains)
 * </ul>
 */
public final class OperatorNormalizer {

  /**
   * Mapping of operator symbols to canonical names. Immutable map defined once at class load time.
   */
  private static final Map<String, String> CANONICAL_NAMES =
      Map.ofEntries(
          Map.entry(">", "gt"),
          Map.entry("<", "lt"),
          Map.entry(">=", "geq"),
          Map.entry("<=", "leq"),
          Map.entry("+", "add"),
          Map.entry("-", "sub"),
          Map.entry("*", "multiply"),
          Map.entry("/", "divide"),
          Map.entry("%", "mod"),
          Map.entry("=", "equals"),
          Map.entry("!=", "notEquals"),
          Map.entry("&", "stringConcat"),
          Map.entry("|", "union"),
          Map.entry(";", "combine"),
          // Identity mapping so isOperatorSymbol("in") returns true
          Map.entry("in", "in"),
          Map.entry("contains", "memberContains"));

  private OperatorNormalizer() {
    // Utility class - no instantiation
  }

  /**
   * Normalizes an operator symbol or function name to its canonical name. If the input is already a
   * canonical name, returns it unchanged.
   *
   * @param operatorSymbol The operator symbol or function name
   * @return The canonical function name
   */
  @Nonnull
  public static String normalize(@Nonnull final String operatorSymbol) {
    return CANONICAL_NAMES.getOrDefault(operatorSymbol, operatorSymbol);
  }

  /**
   * Checks if the given string is an operator symbol (vs. a function name).
   *
   * @param symbol The string to check
   * @return true if this is an operator symbol, false if it's a function name
   */
  public static boolean isOperatorSymbol(@Nonnull final String symbol) {
    return CANONICAL_NAMES.containsKey(symbol);
  }
}
