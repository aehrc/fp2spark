package au.csiro.fhirpath.typing;

/**
 * Specification for a field in a complex type.
 *
 * <p>Each field has a name and a shape (element type + cardinality).
 */
public class FieldSpec {
  private final String name;
  private final Shape shape;

  /**
   * Constructs a field specification with the given name and shape.
   *
   * @param name the field name
   * @param shape the field shape (element type + cardinality)
   */
  public FieldSpec(final String name, final Shape shape) {
    this.name = name;
    this.shape = shape;
  }

  /** Returns the name of this field. */
  public String getName() {
    return name;
  }

  /** Returns the shape (type + cardinality) of this field. */
  public Shape getShape() {
    return shape;
  }

  /** Returns the element type of this field. */
  public Type getType() {
    return shape.elementType();
  }

  /** Returns the cardinality of this field. */
  public Cardinality getCardinality() {
    return shape.cardinality();
  }

  /** Returns whether this field has single cardinality (0..1). */
  public boolean isSingular() {
    return shape.isSingle();
  }

  @Override
  public String toString() {
    return name + ":" + shape;
  }
}
