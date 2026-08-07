package au.csiro.fhirpath.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import au.csiro.fhirpath.typing.Cardinality;
import au.csiro.fhirpath.typing.ComplexType;
import au.csiro.fhirpath.typing.InlineComplexType;
import au.csiro.fhirpath.typing.ResourceType;
import au.csiro.fhirpath.typing.Shape;
import au.csiro.fhirpath.typing.SystemType;
import au.csiro.fhirpath.typing.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ResourceTypeInference}.
 *
 * <p>Focuses on cardinality inference — primitive → single, list → many, and the empty-list special
 * case (see issue <a href="https://github.com/aehrc/fp2spark/issues/156">#156</a>).
 */
class ResourceTypeInferenceTest {

  private static Shape shapeOf(final Type type, final String fieldName) {
    return ((InlineComplexType) type)
        .resolveField(fieldName)
        .orElseThrow(() -> new AssertionError("Field not found: " + fieldName))
        .getShape();
  }

  @Test
  void primitiveFieldsAreSingle() {
    final Map<String, Object> data = new LinkedHashMap<>();
    data.put("s", "hello");
    data.put("i", 42);
    data.put("d", 3.14);
    data.put("b", Boolean.TRUE);

    final ResourceType type = ResourceTypeInference.infer("R", data);

    assertEquals(Shape.single(SystemType.STRING), shapeOf(type, "s"));
    assertEquals(Shape.single(SystemType.INTEGER), shapeOf(type, "i"));
    assertEquals(Shape.single(SystemType.DECIMAL), shapeOf(type, "d"));
    assertEquals(Shape.single(SystemType.BOOLEAN), shapeOf(type, "b"));
  }

  @Test
  void singleElementListIsMany() {
    final Map<String, Object> data = Map.of("xs", List.of("a"));

    final ResourceType type = ResourceTypeInference.infer("R", data);

    assertEquals(Shape.many(SystemType.STRING), shapeOf(type, "xs"));
  }

  @Test
  void multiElementListIsMany() {
    final Map<String, Object> data = Map.of("xs", List.of(1, 2, 3));

    final ResourceType type = ResourceTypeInference.infer("R", data);

    assertEquals(Shape.many(SystemType.INTEGER), shapeOf(type, "xs"));
  }

  /**
   * Regression test for <a href="https://github.com/aehrc/fp2spark/issues/156">#156</a>.
   *
   * <p>An empty list carries no cardinality information. Inferring MANY caused
   * CardinalityMismatchException at compile time for expressions that require ? cardinality
   * operands. The fix infers ?NULL so the field coerces cleanly and empty-propagates per spec.
   */
  @Test
  void emptyListIsOptionalSingletonOfNull() {
    final ResourceType type = ResourceTypeInference.infer("R", Map.of("empty", List.of()));

    assertEquals(Shape.single(SystemType.NULL), shapeOf(type, "empty"));
  }

  @Test
  void subjectWithMixedEmptyAndNonEmptyFields() {
    final Map<String, Object> data = new LinkedHashMap<>();
    data.put("n", 1);
    data.put("empty", List.of());
    data.put("xs", List.of("a", "b"));

    final ResourceType type = ResourceTypeInference.infer("R", data);

    assertEquals(Shape.single(SystemType.INTEGER), shapeOf(type, "n"));
    assertEquals(Shape.single(SystemType.NULL), shapeOf(type, "empty"));
    assertEquals(Shape.many(SystemType.STRING), shapeOf(type, "xs"));
  }

  @Test
  void nestedEmptyListInsideComplexElement() {
    // Subject like Functions.str: [{ attr: 'v', empty: [] }]
    // The outer list makes `items` MANY, the nested empty list remains ?NULL inside the element.
    final Map<String, Object> element = new LinkedHashMap<>();
    element.put("attr", "v");
    element.put("empty", List.of());
    final Map<String, Object> data = Map.of("items", List.of(element));

    final ResourceType type = ResourceTypeInference.infer("R", data);

    final Shape itemsShape = shapeOf(type, "items");
    assertEquals(Cardinality.MANY, itemsShape.cardinality());
    assertInstanceOf(ComplexType.class, itemsShape.elementType());

    final Type elementType = itemsShape.elementType();
    assertEquals(Shape.single(SystemType.STRING), shapeOf(elementType, "attr"));
    assertEquals(
        Shape.single(SystemType.NULL),
        shapeOf(elementType, "empty"),
        "Nested empty list inside a complex element → ?NULL (#156)");
  }

  @Test
  void nullValueRemainsSingleNull() {
    // Baseline — a direct null value was already SINGLE(NULL); the #156 fix must not perturb this.
    final Map<String, Object> data = new LinkedHashMap<>();
    data.put("n", null);

    final ResourceType type = ResourceTypeInference.infer("R", data);

    assertEquals(Shape.single(SystemType.NULL), shapeOf(type, "n"));
  }

  @Test
  void typedNullKeepsDeclaredElementType() {
    // Sanity check — TypedNull-backed empties continue to advertise their declared type,
    // rather than collapsing to NULL like a plain empty list does.
    final Map<String, Object> data = new LinkedHashMap<>();
    data.put("s", TypedNull.STRING);

    final ResourceType type = ResourceTypeInference.infer("R", data);

    assertEquals(Shape.single(SystemType.STRING), shapeOf(type, "s"));
  }
}
