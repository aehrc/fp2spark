package au.csiro.fhirpath.ast;

/** Base interface for all AST nodes in a FHIRPath expression tree. */
public interface AstNode {
  /** Returns a unique identity-based identifier for this node. */
  default int getId() {
    return System.identityHashCode(this);
  }
}
