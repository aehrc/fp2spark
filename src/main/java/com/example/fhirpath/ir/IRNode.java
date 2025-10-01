package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

public interface IRNode {
    Type getType();
    Column eval();
}

