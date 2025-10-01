# FHIRPath → Spark SQL Translator (Java Scaffold)

This document specifies the **Java scaffolding** for a FHIRPath-to-Spark SQL translator.
The goal is to parse FHIRPath expressions, build an AST, analyze them into a typed IR, and finally compile into Spark SQL **Java API** (`org.apache.spark.sql.Column`) expressions.

---

## Package Layout

```
com.example.fhirpath
 ├── ast
 │    ├── AstNode.java
 │    ├── AstTraversal.java
 │    ├── AstFunctionCall.java
 │    ├── AstLiteral.java
 ├── ir
 │    ├── IRNode.java
 │    ├── Add.java
 │    ├── Cast.java
 │    ├── Count.java
 │    ├── Exists.java
 │    ├── Literal.java
 ├── typing
 │    ├── Type.java
 │    ├── TypeSystem.java
 ├── analyzer
 │    ├── Analyzer.java
 │    ├── FunctionRegistry.java
 ├── compiler
 │    ├── SparkCompiler.java
 ├── parser
 │    ├── FhirPath.g4
 │    ├── AstBuilderVisitor.java
 └── util
      ├── SourceLocation.java
      ├── SourceLocationTracker.java
      ├── Diagnostic.java
```

---

## AST Layer (`com.example.fhirpath.ast`)

```java
package com.example.fhirpath.ast;

public interface AstNode {
    int getId(); // e.g., System.identityHashCode(this)
}

public class AstTraversal implements AstNode {
    private final String path;

    public AstTraversal(String path) {
        this.path = path;
    }

    public String getPath() { return path; }

    @Override
    public int getId() { return System.identityHashCode(this); }
}

public class AstFunctionCall implements AstNode {
    private final String functionName;
    private final java.util.List<AstNode> arguments;

    public AstFunctionCall(String functionName, java.util.List<AstNode> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }

    public String getFunctionName() { return functionName; }
    public java.util.List<AstNode> getArguments() { return arguments; }

    @Override
    public int getId() { return System.identityHashCode(this); }
}

public class AstLiteral implements AstNode {
    private final Object value;

    public AstLiteral(Object value) {
        this.value = value;
    }

    public Object getValue() { return value; }

    @Override
    public int getId() { return System.identityHashCode(this); }
}
```

---

## IR Layer (`com.example.fhirpath.ir`)

```java
package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;

public interface IRNode {
    Type getType();
}

public class Add implements IRNode {
    private final IRNode left;
    private final IRNode right;

    public Add(IRNode left, IRNode right) {
        this.left = left;
        this.right = right;
    }

    public IRNode left() { return left; }
    public IRNode right() { return right; }

    @Override
    public Type getType() {
        return com.example.fhirpath.typing.Type.DECIMAL;
    }
}

public class Cast implements IRNode {
    private final IRNode child;
    private final Type targetType;

    public Cast(IRNode child, Type targetType) {
        this.child = child;
        this.targetType = targetType;
    }

    public IRNode child() { return child; }

    @Override
    public Type getType() {
        return targetType;
    }
}

public class Count implements IRNode {
    private final IRNode child;

    public Count(IRNode child) {
        this.child = child;
    }

    public IRNode child() { return child; }

    @Override
    public Type getType() {
        return com.example.fhirpath.typing.Type.INTEGER;
    }
}

public class Exists implements IRNode {
    private final IRNode child;

    public Exists(IRNode child) {
        this.child = child;
    }

    public IRNode child() { return child; }

    @Override
    public Type getType() {
        return com.example.fhirpath.typing.Type.BOOLEAN;
    }
}

public class Literal implements IRNode {
    private final Object value;
    private final Type type;

    public Literal(Object value, Type type) {
        this.value = value;
        this.type = type;
    }

    public Object value() { return value; }

    @Override
    public Type getType() {
        return type;
    }
}
```

---

## Typing (`com.example.fhirpath.typing`)

```java
package com.example.fhirpath.typing;

public enum Type {
    INTEGER,
    DECIMAL,
    QUANTITY,
    DATE,
    DATE_TIME,
    BOOLEAN,
    STRING,
    UNKNOWN
}
```

```java
package com.example.fhirpath.typing;

public class TypeSystem {

    public static boolean canCast(Type from, Type to) {
        if (from == to) return true;

        // FHIRPath implicit conversions
        if (from == Type.INTEGER && to == Type.DECIMAL) return true;
        if (from == Type.DECIMAL && to == Type.QUANTITY) return true;
        if (from == Type.DATE && to == Type.DATE_TIME) return true;

        return false;
    }
}
```

---

## Analyzer (`com.example.fhirpath.analyzer`)

```java
package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.*;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.Type;

public class Analyzer {

    public IRNode analyze(AstNode node) {
        if (node instanceof AstLiteral lit) {
            return new Literal(lit.getValue(), inferType(lit.getValue()));
        }
        if (node instanceof AstFunctionCall call) {
            return FunctionRegistry.resolve(call);
        }
        if (node instanceof AstTraversal trav) {
            // Simplified placeholder
            return new Literal("field:" + trav.getPath(), Type.STRING);
        }
        throw new IllegalArgumentException("Unsupported AST node: " + node);
    }

    private Type inferType(Object value) {
        if (value instanceof Integer) return Type.INTEGER;
        if (value instanceof Double) return Type.DECIMAL;
        if (value instanceof Boolean) return Type.BOOLEAN;
        if (value instanceof String) return Type.STRING;
        return Type.UNKNOWN;
    }
}
```

```java
package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.AstFunctionCall;
import com.example.fhirpath.ir.IRNode;

public class FunctionRegistry {
    public static IRNode resolve(AstFunctionCall call) {
        // TODO: implement function/operator resolution
        throw new UnsupportedOperationException("Function resolution not implemented");
    }
}
```

---

## Compiler (`com.example.fhirpath.compiler`)

```java
package com.example.fhirpath.compiler;

import com.example.fhirpath.ir.*;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public class SparkCompiler {

    public Column compile(IRNode node) {
        if (node instanceof Literal lit) {
            return functions.lit(lit.value());
        }
        if (node instanceof Add add) {
            return compile(add.left()).plus(compile(add.right()));
        }
        if (node instanceof Count c) {
            return functions.count(compile(c.child()));
        }
        if (node instanceof Exists e) {
            // TODO: exists may need dataset context
            return functions.expr("EXISTS(" + compile(e.child()).expr().sql() + ")");
        }
        if (node instanceof Cast cast) {
            return compile(cast.child()).cast(cast.getType().name());
        }
        throw new UnsupportedOperationException("Unhandled IR node: " + node.getClass());
    }
}
```

---

## Parser (ANTLR) (`com.example.fhirpath.parser`)

### Grammar (`FhirPath.g4`)

```antlr
grammar FhirPath;

parse : expression EOF ;

expression
    : literal
    | identifier
    | functionCall
    | expression '+' expression   # AddExpr
    | expression '-' expression   # SubExpr
    ;

literal
    : INT
    | STRING
    ;

functionCall
    : identifier '(' (expression (',' expression)*)? ')'
    ;

identifier
    : IDENTIFIER
    ;

INT     : [0-9]+ ;
STRING  : '\'' (~['\r\n])* '\'' ;
IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;
WS      : [ \t\r\n]+ -> skip ;
```

### Visitor (`AstBuilderVisitor.java`)

```java
package com.example.fhirpath.parser;

import com.example.fhirpath.ast.*;
import java.util.*;

public class AstBuilderVisitor extends FhirPathBaseVisitor<AstNode> {

    @Override
    public AstNode visitLiteral(FhirPathParser.LiteralContext ctx) {
        if (ctx.INT() != null) {
            return new AstLiteral(Integer.parseInt(ctx.INT().getText()));
        }
        if (ctx.STRING() != null) {
            String val = ctx.STRING().getText();
            return new AstLiteral(val.substring(1, val.length()-1));
        }
        throw new RuntimeException("Unsupported literal: " + ctx.getText());
    }

    @Override
    public AstNode visitIdentifier(FhirPathParser.IdentifierContext ctx) {
        return new AstTraversal(ctx.getText());
    }

    @Override
    public AstNode visitFunctionCall(FhirPathParser.FunctionCallContext ctx) {
        String fn = ctx.identifier().getText();
        List<AstNode> args = new ArrayList<>();
        if (ctx.expression() != null) {
            for (var e : ctx.expression()) {
                args.add(visit(e));
            }
        }
        return new AstFunctionCall(fn, args);
    }

    @Override
    public AstNode visitAddExpr(FhirPathParser.AddExprContext ctx) {
        return new AstFunctionCall("add", List.of(visit(ctx.expression(0)), visit(ctx.expression(1))));
    }
}
```

---

## Diagnostics (`com.example.fhirpath.util`)

```java
package com.example.fhirpath.util;

public record SourceLocation(int line, int column) {}

public record Diagnostic(String message, SourceLocation location) {}
```

```java
package com.example.fhirpath.util;

import com.example.fhirpath.ast.AstNode;
import java.util.*;

public class SourceLocationTracker {
    private final Map<Integer, SourceLocation> locations = new HashMap<>();

    public void register(AstNode node, SourceLocation loc) {
        locations.put(node.getId(), loc);
    }

    public Optional<SourceLocation> get(AstNode node) {
        return Optional.ofNullable(locations.get(node.getId()));
    }
}
```

---

## Example End-to-End Usage

```java
String expr = "5 + 10";

// Parse
FhirPathLexer lexer = new FhirPathLexer(CharStreams.fromString(expr));
FhirPathParser parser = new FhirPathParser(new CommonTokenStream(lexer));
AstNode ast = new AstBuilderVisitor().visit(parser.parse());

// Analyze
Analyzer analyzer = new Analyzer();
IRNode ir = analyzer.analyze(ast);

// Compile
SparkCompiler compiler = new SparkCompiler();
Column col = compiler.compile(ir);

// Use in Spark
Dataset<Row> df = ...;
df.select(col.alias("result")).show();
```

---

