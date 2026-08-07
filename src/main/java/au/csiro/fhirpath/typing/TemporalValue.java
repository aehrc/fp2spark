package au.csiro.fhirpath.typing;

/**
 * Sealed interface for FHIRPath temporal literal value wrappers.
 *
 * <p>Unifies {@link DateValue}, {@link DateTimeValue}, and {@link TimeValue} so that code needing
 * to unwrap temporal literals can use a single {@code instanceof TemporalValue} check.
 */
public sealed interface TemporalValue permits DateValue, DateTimeValue, TimeValue {

  /** Returns the ISO 8601 string value with the FHIRPath prefix stripped. */
  String value();
}
