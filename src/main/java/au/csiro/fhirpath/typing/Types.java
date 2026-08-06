package au.csiro.fhirpath.typing;

/** Common Type constants for convenience. */
public final class Types {
  public static final Type INTEGER = SystemType.INTEGER;
  public static final Type DECIMAL = SystemType.DECIMAL;
  public static final Type BOOLEAN = SystemType.BOOLEAN;
  public static final Type STRING = SystemType.STRING;
  public static final Type DATE = SystemType.DATE;
  public static final Type DATE_TIME = SystemType.DATE_TIME;
  public static final Type TIME = SystemType.TIME;
  public static final Type QUANTITY = SystemType.QUANTITY;
  public static final Type CODING = SystemType.CODING;
  public static final Type NULL = SystemType.NULL;
  public static final Type ANY = SystemType.ANY;

  private Types() {
    // Utility class
  }
}
