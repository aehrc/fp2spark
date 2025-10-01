package com.example.fhirpath;

import org.apache.spark.sql.*;
import org.apache.spark.sql.types.*;

import java.util.Arrays;
import java.util.List;

import static org.apache.spark.sql.functions.col;

public class Demo {
    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("fhirpath-demo")
                .master("local[*]")
                .config("spark.ui.enabled", "false")
                .getOrCreate();

        try {
            Dataset<Row> df = spark.range(1).toDF();

            // 1) Evaluate a constant expression
            Column c1 = FhirPath.toColumn("5 + 10");
            df.select(c1.alias("const")).show(false);
        } finally {
            spark.stop();
        }
    }
}

