package com.example.fhirpath;

import com.example.fhirpath.typing.*;
import com.example.fhirpath.typing.fhir.FhirType;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Nonnull
    static String valueToString(@Nonnull final Object value) {
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toString();
        } else if (value instanceof scala.collection.mutable.WrappedArray<?> wa) {
            final List<String> elements = Arrays.stream((Object[]) wa.array())
                    .map(FhirPathIntegrationTest::valueToString)
                    .toList();
            return "[" + String.join(", ", elements) + "]";
        } else {
            return value.toString();
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
                Arguments.of("{} + 10", null),
                Arguments.of("{} + {}", null),
                // minus operator with different types
                Arguments.of("10 - 4", "6"),
                Arguments.of("10.5 - 4.2", "6.3"),
                Arguments.of("10 - 4.2", "5.8"),
                Arguments.of("10.5 - 4", "6.5"),
                // div operator with different types
                Arguments.of("10 / 4", "2.5"),
                Arguments.of("10.4 / 4.0", "2.6"),
                Arguments.of("10 / 4.0", "2.5"),
                Arguments.of("10.4 / 4", "2.6"),
                // Comparison operators
                Arguments.of("5 < 10", "true"),
                Arguments.of("5.1 > 10.2", "false"),
                Arguments.of("5 < 10.2", "true"),
                Arguments.of("5.1 >= 10", "false"),
                Arguments.of("'a' > 'A'", "true"),
                Arguments.of("'a' > {}", null),
                // Math functions
                // abs() on different types
                Arguments.of("5.abs()", "5"),
                Arguments.of("5.3.abs()", "5.3"),
                // exp() on different types
                Arguments.of("1.exp()", "2.7182818284590455"),
                Arguments.of("2.0.exp()", "7.38905609893065"),
                // String functions
                Arguments.of("{}.substring(1,2)", null),
                Arguments.of("'abcde'.substring(1,2)", "bc"),
                Arguments.of("'abcde'.substring(2)", "cde"),
                // test count() on literals
                Arguments.of("10.count()", "1"),
                Arguments.of("{}.count()", "0"),
                Arguments.of("(1 | 2).count()", "2"),
                Arguments.of("('a' | 'b' | 'c').count()", "3"),
                // test exists() on literals
                Arguments.of("'xxx'.exists()", "true"),
                Arguments.of("{}.exists()", "false"),
                Arguments.of("(1 | 2).exists()", "true"),
                // test equality operators
                Arguments.of("5 = 5", "true"),
                Arguments.of("5 = 5.0", "true"),
                Arguments.of("5.3 = 5.3", "true"),
                Arguments.of("6.0 = 6", "true"),
                Arguments.of("'x' = 'y'", "false"),
                Arguments.of("'1' = 1", "false"),// different types
                Arguments.of("{} = 1", null),
                Arguments.of("'xxx'={}", null),
                // Union operator test left for later when implemented
                Arguments.of("5 | 10", "[5, 10]"),
                Arguments.of("5.2 | 10.5", "[5.2, 10.5]"),
                Arguments.of("'a' | 'b' | 'c'", "[a, b, c]"),
                Arguments.of("1 | {}", "[1]"),
                Arguments.of("{} | 1", "[1]"),
                Arguments.of("true | false | true", "[true, false]"),
                // NOTE: this may not be the correct behavior in general
                // but because we do not support polymorphic collection this seem to be reasonable
                // Another option is to fail when types are not the same
                Arguments.of("1.1 | (2 | 3)", "[1.1, 2, 3]"),
                // 2.0 should be skipped
                Arguments.of("(2 | 3) | (1.1 | 2.3 | 2.0)", "[2, 3, 1.1, 2.3]"),
                // test default empty context which is also empty resource
                Arguments.of("count()", "0"),
                Arguments.of("%resource.exists()", "false"),
                Arguments.of("%resource.foo", null),
                Arguments.of("bar", null)
        );
    }

    @ParameterizedTest
    @MethodSource("expressions")
    void testFhirPathExpressions(String expression, String expectedResult) {
        final Column column = FhirPath.toColumn(expression);

        // Evaluate the expression
        final Dataset<Row> result = spark.range(1).toDF().select(column.alias("result"));
        final Row row = result.first();
        final String actualResult = row.isNullAt(0) ? null : valueToString(row.get(0));
        assertEquals(expectedResult, actualResult,
                "Expression '" + expression + "' did not produce expected result");
    }


    Stream<Arguments> expressionsWithContext() {
        return Stream.of(
                Arguments.of("%context.count()", "'x'", "1"),
                Arguments.of("exists()", "'x'", "true"),
                Arguments.of("%context.exists()", "{}", "false"),
                Arguments.of("5 + %context", "10", "15"),
                Arguments.of("count() = 3", "10 | 20 | 30 | %context.foo", "true")
        );
    }

    @ParameterizedTest
    @MethodSource("expressionsWithContext")
    void testFhirPathExpressionsWithContext(String expression, String context, String expectedResult) {
        final Column column = FhirPath.toColumn(expression, context);
        // Evaluate the expression
        final Dataset<Row> result = spark.range(1).toDF().select(column.alias("result"));
        final Row row = result.first();
        final String actualResult = row.isNullAt(0) ? null : valueToString(row.get(0));
        assertEquals(expectedResult, actualResult,
                "Expression '" + expression + "' did not produce expected result");
    }

    Stream<Arguments> expressionsWithResource() {
        return Stream.of(
                Arguments.of("id", "id1"),
                Arguments.of("age", "55"),
                Arguments.of("name.family", "[Szul, Brown]"),
                Arguments.of("name.given", "[Piotr, Jaroslaw, John, Mark]"),
                Arguments.of("%context.name.use", "[official, alias]"),
                Arguments.of("%resource.count()", "1"),
                Arguments.of("exists()", "true"),
                Arguments.of("%context.gender = %resource.gender", "true"),
                // test FHIR
                Arguments.of("age + 10", "65"),
                Arguments.of("10.3 + age", "65.3"),
                Arguments.of("gender = 'male'", "true"),
                Arguments.of("value", "344.1000"),
                Arguments.of("value.getValue()", "344.1")
        );
    }

    @ParameterizedTest
    @MethodSource("expressionsWithResource")
    void testFhirPathExpressionsWithResource(String expression, String expectedResult) {

        StructType humanNameSchema = DataTypes.createStructType(
                new StructField[]{
                        new StructField("family", DataTypes.StringType, true, Metadata.empty()),
                        new StructField("given", DataTypes.createArrayType(DataTypes.StringType), true, Metadata.empty()),
                        new StructField("use", DataTypes.StringType, true, Metadata.empty()),
                });
        StructType patientSchema = DataTypes.createStructType(
                new StructField[]{
                        new StructField("id", DataTypes.StringType, true, Metadata.empty()),
                        new StructField("gender", DataTypes.StringType, true, Metadata.empty()),
                        new StructField("age", DataTypes.IntegerType, true, Metadata.empty()),
                        new StructField("value", DataTypes.StringType, true, Metadata.empty()),
                        new StructField("name", DataTypes.createArrayType(humanNameSchema), true, Metadata.empty()),
                }
        );

        String data = """
                {
                "id":"id1",
                "gender":"male",
                "age":55,
                "value":"344.1000",
                "name":[
                    {
                      "family":"Szul",
                      "given":["Piotr", "Jaroslaw"],
                      "use": "official"
                    },
                    {
                      "family":"Brown",
                      "given":["John", "Mark"],
                      "use": "alias"
                    },
                    {}
                ]
                }
                """;
        Dataset<Row> inputDf = spark.createDataset(List.of(data), Encoders.STRING())
                .select(
                        functions.from_json(functions.col("value"), patientSchema).alias("Patient"));

        final ComplexType humanNameType = new ComplexType(
                new FieldSpec("family", Type.STRING),
                new FieldSpec("given", new CollectionType(Type.STRING)),
                new FieldSpec("use", Type.STRING)
        );

        final Column column = FhirPath.toColumn(expression, new ResourceType("Patient",
                new FieldSpec("id", Type.STRING),
                new FieldSpec("gender", new FhirType(PrimitiveType.STRING)),
                new FieldSpec("age", new FhirType(PrimitiveType.INTEGER)),
                new FieldSpec("value", new FhirType(PrimitiveType.DECIMAL)),
                new FieldSpec("name", new CollectionType(humanNameType))
        ));
        // Evaluate the expression
        final Dataset<Row> result = inputDf.select(column.alias("result"));
        final Row row = result.first();
        final String actualResult = row.isNullAt(0) ? null : valueToString(row.get(0));
        assertEquals(expectedResult, actualResult,
                "Expression '" + expression + "' did not produce expected result");

    }
}
