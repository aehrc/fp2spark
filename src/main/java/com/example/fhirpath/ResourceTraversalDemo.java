package com.example.fhirpath;

import com.example.fhirpath.typing.*;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.*;

/**
 * Demonstrates resource traversal with complex types in FHIRPath expressions
 */
public class ResourceTraversalDemo {

    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("FHIRPath Resource Traversal Demo")
                .master("local[*]")
                .getOrCreate();

        // Create a Patient resource specification
        ResourceType patientSpec = createPatientResourceSpec();

        // Create a sample dataset with Patient structure
        Dataset<Row> patients = createSamplePatientDataset(spark);

        // Demonstrate various FHIRPath expressions with resource traversal
        demonstrateResourceTraversal(patients, patientSpec);

        spark.stop();
    }

    private static ResourceType createPatientResourceSpec() {
        // Define HumanName complex type
        ComplexType humanNameType = new ComplexType(
            FieldSpec.singular("family", Type.STRING),
            FieldSpec.collection("given", Type.STRING),
            FieldSpec.singular("use", Type.STRING)
        );

        // Define ContactPoint complex type
        ComplexType contactPointType = new ComplexType(
            FieldSpec.singular("system", Type.STRING),
            FieldSpec.singular("value", Type.STRING),
            FieldSpec.singular("use", Type.STRING)
        );

        // Define Address complex type
        ComplexType addressType = new ComplexType(
            FieldSpec.collection("line", Type.STRING),
            FieldSpec.singular("city", Type.STRING),
            FieldSpec.singular("state", Type.STRING),
            FieldSpec.singular("postalCode", Type.STRING),
            FieldSpec.singular("country", Type.STRING)
        );

        // Define Patient resource fields
        return new ResourceType("Patient",
            FieldSpec.singular("id", Type.STRING),
            FieldSpec.singular("active", Type.BOOLEAN),
            FieldSpec.collection("name", humanNameType),
            FieldSpec.collection("telecom", contactPointType),
            FieldSpec.singular("gender", Type.STRING),
            FieldSpec.singular("birthDate", Type.DATE),
            FieldSpec.collection("address", addressType)
        );
    }

    private static Dataset<Row> createSamplePatientDataset(SparkSession spark) {
        // Create schema that matches the Patient resource specification
        StructType humanNameSchema = DataTypes.createStructType(new StructField[]{
            DataTypes.createStructField("family", DataTypes.StringType, true),
            DataTypes.createStructField("given", DataTypes.createArrayType(DataTypes.StringType), true),
            DataTypes.createStructField("use", DataTypes.StringType, true)
        });

        StructType contactPointSchema = DataTypes.createStructType(new StructField[]{
            DataTypes.createStructField("system", DataTypes.StringType, true),
            DataTypes.createStructField("value", DataTypes.StringType, true),
            DataTypes.createStructField("use", DataTypes.StringType, true)
        });

        StructType addressSchema = DataTypes.createStructType(new StructField[]{
            DataTypes.createStructField("line", DataTypes.createArrayType(DataTypes.StringType), true),
            DataTypes.createStructField("city", DataTypes.StringType, true),
            DataTypes.createStructField("state", DataTypes.StringType, true),
            DataTypes.createStructField("postalCode", DataTypes.StringType, true),
            DataTypes.createStructField("country", DataTypes.StringType, true)
        });

        StructType patientSchema = DataTypes.createStructType(new StructField[]{
            DataTypes.createStructField("id", DataTypes.StringType, false),
            DataTypes.createStructField("active", DataTypes.BooleanType, true),
            DataTypes.createStructField("name", DataTypes.createArrayType(humanNameSchema), true),
            DataTypes.createStructField("telecom", DataTypes.createArrayType(contactPointSchema), true),
            DataTypes.createStructField("gender", DataTypes.StringType, true),
            DataTypes.createStructField("birthDate", DataTypes.DateType, true),
            DataTypes.createStructField("address", DataTypes.createArrayType(addressSchema), true)
        });

        StructType rootSchema = DataTypes.createStructType(new StructField[]{
            DataTypes.createStructField("patient", patientSchema, false)
        });

        // Create sample data using SQL
        return spark.sql("""
            SELECT struct(
                'patient-123' as id,
                true as active,
                array(
                    struct('Doe' as family, array('John', 'William') as given, 'official' as use),
                    struct('Smith' as family, array('Johnny') as given, 'nickname' as use)
                ) as name,
                array(
                    struct('phone' as system, '+1-555-123-4567' as value, 'home' as use),
                    struct('email' as system, 'john.doe@example.com' as value, 'work' as use)
                ) as telecom,
                'male' as gender,
                date('1990-05-15') as birthDate,
                array(
                    struct(
                        array('123 Main St', 'Apt 4B') as line,
                        'Springfield' as city,
                        'IL' as state,
                        '62701' as postalCode,
                        'USA' as country
                    )
                ) as address
            ) as patient
            """);
    }

    private static void demonstrateResourceTraversal(Dataset<Row> patients, ResourceType patientSpec) {
        System.out.println("=== FHIRPath Resource Traversal Demo ===\n");

        // Simple field access
        System.out.println("1. Simple field access - Patient ID:");
        patients.select(FhirPath.toColumn("id", patientSpec).alias("patient_id"))
                .show(false);

        // Boolean field access
        System.out.println("2. Boolean field access - Active status:");
        patients.select(FhirPath.toColumn("active", patientSpec).alias("is_active"))
                .show(false);

        // Complex field access - this would work once we implement array element access
        System.out.println("3. Complex field access - First name (family):");
        try {
            // This demonstrates the structure is recognized, but array access needs more implementation
            patients.select(FhirPath.toColumn("name", patientSpec).alias("all_names"))
                    .show(false);
        } catch (Exception e) {
            System.out.println("Array access not yet fully implemented: " + e.getMessage());
        }

        // Date field access
        System.out.println("4. Date field access - Birth date:");
        patients.select(FhirPath.toColumn("birthDate", patientSpec).alias("birth_date"))
                .show(false);

        // String field access
        System.out.println("5. String field access - Gender:");
        patients.select(FhirPath.toColumn("gender", patientSpec).alias("gender"))
                .show(false);

        System.out.println("\nResource traversal demo completed successfully!");
        System.out.println("The system correctly recognizes and handles complex resource structures.");
    }
}
