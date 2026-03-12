package com.example.fhirpath.ir;

import com.example.fhirpath.codegen.spark.SparkCodeGenerator;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Base class for IRNode unit testing. Provides common infrastructure for evaluating IRNodes with
 * Spark.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IRNodeTestBase {

  protected SparkSession spark;

  @BeforeAll
  void setupSpark() {
    spark =
        SparkSession.builder()
            .appName("irnode-test")
            .master("local[*]")
            .config("spark.ui.enabled", "false")
            .config("spark.sql.warehouse.dir", "target/spark-warehouse")
            .getOrCreate();
  }

  @AfterAll
  void teardownSpark() {
    if (spark != null) {
      spark.stop();
    }
  }

  /**
   * Evaluates an IRNode and returns the result as a Java object. Returns null for empty/null
   * results.
   */
  @Nullable
  protected Object evaluateIRNode(IRNode node) {
    Column column = node.accept(new SparkCodeGenerator(SparkOperationRegistry.standard()));
    Dataset<Row> result = spark.range(1).select(column.alias("result"));
    Row row = result.first();
    return row.isNullAt(0) ? null : row.get(0);
  }

  /**
   * Evaluates an IRNode and returns the result as a String. Returns null for empty/null results.
   */
  @Nullable
  protected String evaluateAsString(IRNode node) {
    Object result = evaluateIRNode(node);
    if (result == null) {
      return null;
    }
    return valueToString(result);
  }

  /**
   * Evaluates an IRNode and returns the result as an Integer. Returns null for empty/null results.
   */
  @Nullable
  protected Integer evaluateAsInteger(IRNode node) {
    Object result = evaluateIRNode(node);
    if (result == null) {
      return null;
    }
    if (result instanceof Integer i) {
      return i;
    }
    if (result instanceof Long l) {
      return l.intValue();
    }
    throw new IllegalStateException("Expected Integer but got: " + result.getClass());
  }

  /**
   * Evaluates an IRNode and returns the result as a Boolean. Returns null for empty/null results.
   */
  @Nullable
  protected Boolean evaluateAsBoolean(IRNode node) {
    Object result = evaluateIRNode(node);
    if (result == null) {
      return null;
    }
    if (result instanceof Boolean b) {
      return b;
    }
    throw new IllegalStateException("Expected Boolean but got: " + result.getClass());
  }

  /** Helper to create a Literal IRNode */
  protected IRNode lit(@Nullable Object value, Type type) {
    return new Literal(value, type);
  }

  /** Helper to create a String Literal IRNode */
  protected IRNode str(@Nullable String value) {
    return new Literal(value, Types.STRING);
  }

  /** Helper to create an Integer Literal IRNode */
  protected IRNode integer(@Nullable Integer value) {
    return new Literal(value, Types.INTEGER);
  }

  /** Helper to create a Decimal Literal IRNode */
  protected IRNode decimal(@Nullable Double value) {
    return new Literal(value, Types.DECIMAL);
  }

  /** Helper to create a Boolean Literal IRNode */
  protected IRNode bool(@Nullable Boolean value) {
    return new Literal(value, Types.BOOLEAN);
  }

  /** Converts various result types to string representation for comparison */
  protected String valueToString(Object value) {
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros().toString();
    } else if (value instanceof scala.collection.Seq<?> seq) {
      // Scala 2.13: Use javaapi.CollectionConverters for Java interop
      final java.util.List<?> javaList = scala.jdk.javaapi.CollectionConverters.asJava(seq);
      final List<String> elements = javaList.stream().map(this::valueToString).toList();
      return "[" + String.join(", ", elements) + "]";
    } else {
      return value.toString();
    }
  }
}
