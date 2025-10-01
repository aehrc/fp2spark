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
                Arguments.of("5 + 10", "15"),
                Arguments.of("5.1 + 10.2", "15.3"),
                Arguments.of("5 + 10.2", "15.2"),
                Arguments.of("5.1 + 10", "15.1"),
                Arguments.of("'foo' + 'bar'", "foobar"),
                // test count() on literals
                Arguments.of("10.count()", "1"),
                Arguments.of("{}.count()", "0"),
                Arguments.of("'xxx'.exists()", "true"),
                Arguments.of("{}.exists()", "false")
        );
    }

    @ParameterizedTest
    @MethodSource("expressions")
    void testFhirPathExpressions(String expression, String expectedResult) {
        final Column column = FhirPath.toColumn(expression);

        // Evaluate the expression
        final Dataset<Row> result = spark.range(1).toDF().select(column.alias("result"));
        final Row row = result.first();
        final Object value = row.get(0);
        final String actualResult = value.toString();
        assertEquals(expectedResult, actualResult,
                "Expression '" + expression + "' did not produce expected result");
    }
}
