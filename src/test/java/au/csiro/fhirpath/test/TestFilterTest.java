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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Tests for TestFilter substring matching functionality. */
class TestFilterTest {

  @Test
  void emptyFilterMatchesEverything() {
    final TestFilter filter = new TestFilter("");

    assertFalse(filter.isActive());
    assertTrue(filter.matches("5 + 10 => 15"));
    assertTrue(filter.matches("anything"));
    assertTrue(filter.matches(""));
  }

  @Test
  void exactMatch() {
    final TestFilter filter = new TestFilter("5 + 10");

    assertTrue(filter.isActive());
    assertTrue(filter.matches("5 + 10 => 15"));
    assertTrue(filter.matches("5 + 10"));
    assertFalse(filter.matches("5 - 10"));
  }

  @Test
  void caseInsensitiveMatch() {
    final TestFilter filter = new TestFilter("addition");

    assertTrue(filter.matches("Integer addition"));
    assertTrue(filter.matches("ADDITION"));
    assertTrue(filter.matches("addition"));
    assertTrue(filter.matches("5 + 10 => 15 [Integer addition]"));
    assertFalse(filter.matches("subtraction"));
  }

  @Test
  void substringMatch() {
    final TestFilter filter = new TestFilter("addition");

    assertTrue(filter.matches("Integer addition"));
    assertTrue(filter.matches("Decimal addition"));
    assertTrue(filter.matches("5 + 10 => 15 [Integer addition]"));
    assertFalse(filter.matches("Integer subtraction"));
  }

  @Test
  void matchesGroupName() {
    final TestFilter filter = new TestFilter("[Integer addition]");

    assertTrue(filter.matches("5 + 10 => 15 [Integer addition]"));
    assertFalse(filter.matches("5 + 10 => 15 [Decimal addition]"));
  }

  @Test
  void matchesExpression() {
    final TestFilter filter = new TestFilter("5 + 10");

    assertTrue(filter.matches("5 + 10 => 15 [Integer addition]"));
    assertFalse(filter.matches("5 + 5 => 10 [Integer addition]"));
  }

  @Test
  void matchesExpectedValue() {
    final TestFilter filter = new TestFilter("=> 15");

    assertTrue(filter.matches("5 + 10 => 15 [Integer addition]"));
    assertTrue(filter.matches("10 + 5 => 15 [Integer addition]"));
    assertFalse(filter.matches("5 + 5 => 10 [Integer addition]"));
  }

  @Test
  void matchesDescription() {
    final TestFilter filter = new TestFilter("Division always returns decimal");

    assertTrue(filter.matches("10 / 2 => 5.0 : Division always returns decimal [Division]"));
    assertFalse(filter.matches("10 / 2 => 5.0 [Division]"));
  }

  @Test
  void whitespaceInPatternIsTrimmed() {
    final TestFilter filter = new TestFilter("  addition  ");

    // Pattern should be trimmed and lowercased in constructor
    assertEquals("addition", filter.getPattern());
    assertTrue(filter.matches("Integer addition"));
  }
}
