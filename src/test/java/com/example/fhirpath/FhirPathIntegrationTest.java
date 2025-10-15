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
class FhirPathIntegrationTest {

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
                Arguments.of("bar", null),
                // simple where tests
                Arguments.of("(1 | 2 | 3).where($this > 1)", "[2, 3]"),
                Arguments.of("('a' | 'bc' | 'cd').where(length() > 1)", "[bc, cd]"),
                // Edge cases: where() on empty collections and singular values
                // Per FHIRPath spec 5.2.5: "If the input collection is empty ({ }), the result is empty"
                // Per FHIRPath spec 2.1: All expressions return collections, even single values
                // DISABLE NULL EVAL: Arguments.of("{}.where($this > 1)", null), // empty collection returns empty
                Arguments.of("2.where($this > 1)", "2"), // singular value matching criteria returns the value
                Arguments.of("'foo'.where($this = 'bar')", null), // singular string not matching criteria
                // exists(criteria) tests on literals (desugared to where(criteria).exists())
                // DISABLE NULL EVAL: Arguments.of("{}.exists($this > 1)", "false"), // empty collection returns empty
                Arguments.of("2.exists($this > 1)", "true"), // singular value matching criteria returns the value
                Arguments.of("'foo'.exists($this = 'bar')", "false"), // singular string not matching criteria
                Arguments.of("(1 | 2 | 3).exists($this > 1)", "true"),
                Arguments.of("(1 | 2 | 3).exists($this > 5)", "false"),
                Arguments.of("('a' | 'b' | 'c').exists($this = 'b')", "true"),
                // iif() conditional tests - FHIRPath Spec 6.7
                // Basic: iif with literal boolean criterion
                Arguments.of("{}.iif(true, 'true')", "true"),          // empty collection, true criterion returns result
                Arguments.of("{}.iif(false, 'true')", null),           // empty collection, false criterion returns empty
                Arguments.of("5.iif(true, 'found')", "found"),         // singular value, true criterion
                Arguments.of("5.iif(false, 'found')", null),           // singular value, false criterion
                // Collection-level criterion: $this refers to entire collection (implicit $this)
                Arguments.of("(1 | 2).iif(exists(), $this)", "[1, 2]"),               // implicit $this.exists()
                Arguments.of("(1 | 2).iif(empty(), $this)", null),                    // implicit $this.empty()
                Arguments.of("(1 | 2 | 3).iif(count() > 2, $this)", "[1, 2, 3]"),   // implicit $this.count()
                Arguments.of("(1 | 2).iif(count() > 2, $this)", null),               // count criterion false
                Arguments.of("(1 | 2 | 3).iif(count() = 3, first())", "1"),         // implicit in both lambdas
                // True-result as lambda: operates on collection (implicit $this)
                Arguments.of("(5 | 10 | 15).iif(exists(), count())", "3"),                      // implicit in both
                Arguments.of("(1 | 2 | 3 | 4).iif(count() > 2, where($this > 2))", "[3, 4]"), // implicit count(), explicit $this in where
                // Nested iif: iif within criterion or result (implicit $this)
                Arguments.of("(1 | 2).iif(iif(exists(), true), 'nested')", "nested"),  // nested in criterion, implicit
                Arguments.of("(1 | 2).iif(true, iif(count() = 2, 'match'))", "match"), // nested in result, implicit
                Arguments.of("5.iif(true, 10.iif(true, 'deep'))", "deep"),             // double nested result
                // Edge: Different result types (implicit $this)
                Arguments.of("(1 | 2 | 3).iif(count() > 2, 'found')", "found"),       // implicit, returns string
                Arguments.of("('a' | 'b').iif(exists(), 999)", "999"),                 // implicit, returns integer
                // Edge: Combining with other operations (implicit $this)
                Arguments.of("(1 | 2 | 3).iif(exists(), $this).count()", "3"),        // implicit in criterion
                Arguments.of("(5 | 10).iif(count() = 2, first()) + 3", "8"),           // implicit in both lambdas
                // first() function tests - FHIRPath Spec 6.6
                // spec: Returns first element from multi-element collection
                Arguments.of("(1 | 2 | 3).first()", "1"),
                Arguments.of("('a' | 'b' | 'c').first()", "a"),
                Arguments.of("(5.2 | 10.5 | 15.3).first()", "5.2"),
                // spec: Returns empty for empty collection (equivalent to {}[0])
                Arguments.of("{}.first()", null),
                // edge: Singular value returns that value (single-element collection)
                Arguments.of("5.first()", "5"),
                Arguments.of("'hello'.first()", "hello")
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
                Arguments.of("value.getValue()", "344.1"),
                // where() function tests - FHIRPath Spec 5.2.5
                // spec: Basic filtering with equality
                Arguments.of("name.where(use = 'official').family", "[Szul]"),
                Arguments.of("name.where(use = 'alias').family", "[Brown]"),

                // spec: Filtering that returns empty when no match
                Arguments.of("name.where(use = 'nonexistent').exists()", "false"),
                Arguments.of("name.where(family = 'Unknown').count()", "0"),

                // spec: where() with implicit $this in criteria
                Arguments.of("name.where(family = 'Szul').given", "[Piotr, Jaroslaw]"),

                // edge: where() with explicit $this
                Arguments.of("name.given.where($this = 'John')", "[John]"),
                Arguments.of("name.given.where($this > 'K')", "[Piotr, Mark]"),

                // edge: Chained where() clauses
                Arguments.of("name.where(use = 'official').where(family = 'Szul').exists()", "true"),
                Arguments.of("name.where(use = 'official').where(family = 'Brown').exists()", "false"),

                // spec: where() on empty input collection returns empty
                Arguments.of("name.where(family = 'Unknown').where(use = 'official').count()", "0"),

                // edge: where() with nested field access
                Arguments.of("name.where(given.exists()).count()", "2"),
                Arguments.of("name.where(given.count() > 1).family", "[Szul, Brown]"),

                // edge: nested where() with implicit $this in both levels
                Arguments.of("name.where(given.where($this = 'Piotr').exists()).family", "[Szul]"),

                // edge: where() preserves collection structure
                Arguments.of("name.where(use = 'official').count()", "1"),
//                Arguments.of("name.where(use = 'official' or use = 'alias').count()", "2"),

                // spec: where() with comparison operators
                Arguments.of("name.given.where($this < 'K')", "[Jaroslaw, John]"),
                Arguments.of("name.given.where($this >= 'John').count()", "3"),

//                // edge: where() with boolean operations in criteria
//                Arguments.of("name.where(use = 'official' and family = 'Szul').exists()", "true"),
//                Arguments.of("name.where(use = 'official' and family = 'Brown').exists()", "false")

                // exists(criteria) function tests - FHIRPath Spec 5.2.1 (desugared to where().exists())
                // spec: exists(criteria) with equality - equivalent to where(criteria).exists()
                Arguments.of("name.exists(use = 'official')", "true"),
                Arguments.of("name.exists(use = 'nonexistent')", "false"),
                Arguments.of("name.exists(family = 'Szul')", "true"),
                Arguments.of("name.exists(family = 'Unknown')", "false"),

                // edge: exists(criteria) with comparison operators
                Arguments.of("name.given.exists($this > 'K')", "true"),
                Arguments.of("name.given.exists($this > 'Z')", "false"),
                Arguments.of("name.given.exists($this = 'Piotr')", "true"),

                // edge: exists(criteria) on empty collection
                Arguments.of("name.where(family = 'Unknown').exists(use = 'official')", "false"),

                // edge: Nested exists(criteria) - exists with nested exists
                Arguments.of("name.exists(given.exists())", "true"),
                Arguments.of("name.exists(given.count() > 1)", "true"),

                // spec: exists(criteria) finds any matching element
                Arguments.of("name.given.exists($this = 'John')", "true"),
                // first() function tests - FHIRPath Spec 6.6
                // spec: Returns first element from collection field
                Arguments.of("name.first().family", "Szul"),
                Arguments.of("name.first().given", "[Piotr, Jaroslaw]"),
                // edge: Chaining first() on nested collections
                Arguments.of("name.first().given.first()", "Piotr")
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
                new FieldSpec("family", Types.STRING),
                new FieldSpec("given", new CollectionType(Types.STRING)),
                new FieldSpec("use", Types.STRING)
        );

        final Column column = FhirPath.toColumn(expression, new ResourceType("Patient",
                new FieldSpec("id", Types.STRING),
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
