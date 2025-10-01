package com.example.fhirpath;

import org.apache.spark.sql.*;
import org.apache.spark.sql.types.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.apache.spark.sql.functions.col;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FhirPathIntegrationTest {

    private SparkSession spark;

    @BeforeAll
    void setupSpark() {
        spark = SparkSession.builder()
                .appName("fhirpath-test")
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

    Stream<Arguments> expressions() {
        return Stream.of(
                Arguments.of("'hello'", "hello"),
                Arguments.of("12", "12"),
                Arguments.of("10.4", "10.4"),
                Arguments.of("true", "true"),
                Arguments.of("{}", null),
                // plus operator with different types
                Arguments.of("5 + 10", "15"),
                Arguments.of("5.1 + 10.2", "15.3"),
                Arguments.of("5 + 10.2", "15.2"),
                Arguments.of("5.1 + 10", "15.1"),
                Arguments.of("'foo' + 'bar'", "foobar"),
                // minus operator with different types
                Arguments.of("10 - 4", "6"),
                Arguments.of("10.5 - 4.2", "6.3"),
                Arguments.of("10 - 4.2", "5.8"),
                Arguments.of("10.5 - 4", "6.5"),
                // test count() on literals
                Arguments.of("10.count()", "1"),
                Arguments.of("{}.count()", "0"),
                Arguments.of("'xxx'.exists()", "true"),
                Arguments.of("{}.exists()", "false"),
                // test equality operators
                Arguments.of("5 = 5", "true"),
                Arguments.of("5 = 5.0", "true"),
                Arguments.of("5.3 = 5.3", "true"),
                Arguments.of("6.0 = 6", "true"),
                Arguments.of("'x' = 'y'", "false"),
                Arguments.of("'1' = 1", "false"),// different types
                Arguments.of("{} = 1", null),
                Arguments.of("'xxx'={}", null)
        );
    }

    @ParameterizedTest
    @MethodSource("expressions")
    void testFhirPathExpressions(String expression, String expectedResult) {
        final Column column = FhirPath.toColumn(expression);

        // Evaluate the expression
        final Dataset<Row> result = spark.range(1).toDF().select(column.alias("result"));
        final Row row = result.first();
        final String actualResult = row.isNullAt(0)?null: row.get(0).toString();
        assertEquals(expectedResult, actualResult,
                "Expression '" + expression + "' did not produce expected result");
    }
}
