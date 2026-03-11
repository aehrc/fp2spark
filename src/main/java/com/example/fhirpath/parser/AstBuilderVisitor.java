package com.example.fhirpath.parser;

import com.example.fhirpath.ast.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AstBuilderVisitor extends FhirPathBaseVisitor<AstNode> {

    @Override
    public AstNode visitEntireExpression(FhirPathParser.EntireExpressionContext ctx) {
        return visit(ctx.expression());
    }

    // Handle different expression types - for now, focus on the ones we support
    @Override
    public AstNode visitTermExpression(FhirPathParser.TermExpressionContext ctx) {
        return visit(ctx.term());
    }

    @Override
    public AstNode visitAdditiveExpression(FhirPathParser.AdditiveExpressionContext ctx) {
        AstNode left = visit(ctx.expression(0));
        AstNode right = visit(ctx.expression(1));

        String op = ctx.getChild(1).getText();
        return new AstBinaryOperator(op, left, right);
    }

    @Override
    public AstNode visitMultiplicativeExpression(FhirPathParser.MultiplicativeExpressionContext ctx) {
        AstNode left = visit(ctx.expression(0));
        AstNode right = visit(ctx.expression(1));

        String op = ctx.getChild(1).getText();
        return new AstBinaryOperator(op, left, right);
    }

    @Override
    public AstNode visitPolarityExpression(FhirPathParser.PolarityExpressionContext ctx) {
        AstNode operand = visit(ctx.expression());
        String op = ctx.getChild(0).getText();

        String functionName = switch (op) {
            case "+" -> "unaryPlus";
            case "-" -> "unaryMinus";
            default -> throw new IllegalArgumentException("Unknown polarity operator: " + op);
        };

        return new AstFunctionCall(functionName, List.of(operand));
    }

    // Term handling
    @Override
    public AstNode visitLiteralTerm(FhirPathParser.LiteralTermContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public AstNode visitInvocationTerm(FhirPathParser.InvocationTermContext ctx) {
        return visit(ctx.invocation());
    }

    @Override
    public AstNode visitParenthesizedTerm(FhirPathParser.ParenthesizedTermContext ctx) {
        return visit(ctx.expression());
    }

    // Literal handling
    @Override
    public AstNode visitStringLiteral(FhirPathParser.StringLiteralContext ctx) {
        final String text = ctx.STRING().getText();
        // Remove surrounding quotes
        final String quotesRemoved = text.substring(1, text.length() - 1);
        // Process escape sequences per FHIRPath spec
        final String value = StringEscapeUtils.unescapeFhirPathString(quotesRemoved);
        return new AstLiteral(value);
    }

    @Override
    public AstNode visitNumberLiteral(FhirPathParser.NumberLiteralContext ctx) {
        String text = ctx.NUMBER().getText();
        try {
            if (text.contains(".")) {
                return new AstLiteral(new BigDecimal(text));
            } else {
                return new AstLiteral(Integer.parseInt(text));
            }
        } catch (NumberFormatException e) {
            return new AstLiteral(Long.parseLong(text));
        }
    }

    @Override
    public AstNode visitLongNumberLiteral(FhirPathParser.LongNumberLiteralContext ctx) {
        String text = ctx.LONGNUMBER().getText();
        // Remove 'L' suffix if present
        if (text.endsWith("L")) {
            text = text.substring(0, text.length() - 1);
        }
        return new AstLiteral(Long.parseLong(text));
    }

    @Override
    public AstNode visitBooleanLiteral(FhirPathParser.BooleanLiteralContext ctx) {
        String text = ctx.getText();
        return new AstLiteral(Boolean.parseBoolean(text));
    }

    @Override
    public AstNode visitNullLiteral(FhirPathParser.NullLiteralContext ctx) {
        return new AstLiteral(null);
    }

    // Invocation handling
    @Override
    public AstNode visitMemberInvocation(FhirPathParser.MemberInvocationContext ctx) {
        String identifier = ctx.identifier().getText();
        return new AstTraversal(identifier); // Uses convenience constructor (target = null)
    }

    @Override
    public AstNode visitFunctionInvocation(FhirPathParser.FunctionInvocationContext ctx) {
        return visit(ctx.function());
    }

    @Override
    public AstNode visitFunction(FhirPathParser.FunctionContext ctx) {
        String functionName = ctx.identifier().getText();
        List<AstNode> arguments = new ArrayList<>();

        if (ctx.paramList() != null) {
            for (var expr : ctx.paramList().expression()) {
                arguments.add(visit(expr));
            }
        }

        return new AstFunctionCall(functionName, arguments);
    }

    @Override
    public AstNode visitIdentifier(FhirPathParser.IdentifierContext ctx) {
        return new AstTraversal(ctx.getText()); // Uses convenience constructor (target = null)
    }

    // Default behavior for unsupported expressions - throw informative errors
    @Override
    public AstNode visitInvocationExpression(FhirPathParser.InvocationExpressionContext ctx) {
        // Handle method-style function calls like "10.count()" or "expr.exists()"
        AstNode target = visit(ctx.expression());
        AstNode invocation = visit(ctx.invocation());

        // Convert invocation to function call with target stored separately
        if (invocation instanceof AstFunctionCall funcCall) {
            // Create new function call with target stored in the target field
            return funcCall.withTarget(target);
        } else if (invocation instanceof AstTraversal traversal) {
            // Handle member access like "expr.field" - create traversal with target
            return new AstTraversal(traversal.path(), target);
        }

        throw new UnsupportedOperationException("Unsupported invocation type: " + invocation.getClass());
    }

    @Override
    public AstNode visitIndexerExpression(FhirPathParser.IndexerExpressionContext ctx) {
        throw new UnsupportedOperationException("Indexer expressions ([]) are not yet supported");
    }

    @Override
    public AstNode visitCombineExpression(FhirPathParser.CombineExpressionContext ctx) {
        AstNode left = visit(ctx.expression(0));
        AstNode right = visit(ctx.expression(1));

        String op = ctx.getChild(1).getText(); // Should be "|" or ";'
        return new AstBinaryOperator(op, left, right);
    }

    @Override
    public AstNode visitEqualityExpression(FhirPathParser.EqualityExpressionContext ctx) {
        AstNode left = visit(ctx.expression(0));
        AstNode right = visit(ctx.expression(1));

        String op = ctx.getChild(1).getText();
        return new AstBinaryOperator(op, left, right);
    }

    @Override
    public AstNode visitInequalityExpression(FhirPathParser.InequalityExpressionContext ctx) {
        AstNode left = visit(ctx.expression(0));
        AstNode right = visit(ctx.expression(1));

        String op = ctx.getChild(1).getText();
        return new AstBinaryOperator(op, left, right);
    }

    @Override
    public AstNode visitMembershipExpression(FhirPathParser.MembershipExpressionContext ctx) {
        throw new UnsupportedOperationException("Membership expressions (in, contains) are not yet supported");
    }

    @Override
    public AstNode visitAndExpression(FhirPathParser.AndExpressionContext ctx) {
        AstNode left = visit(ctx.expression(0));
        AstNode right = visit(ctx.expression(1));

        return new AstBinaryOperator("and", left, right);
    }

    @Override
    public AstNode visitOrExpression(FhirPathParser.OrExpressionContext ctx) {
        AstNode left = visit(ctx.expression(0));
        AstNode right = visit(ctx.expression(1));

        String op = ctx.getChild(1).getText(); // Could be "or" or "xor"
        return new AstBinaryOperator(op, left, right);
    }

    @Override
    public AstNode visitImpliesExpression(FhirPathParser.ImpliesExpressionContext ctx) {
        AstNode left = visit(ctx.expression(0));
        AstNode right = visit(ctx.expression(1));

        return new AstBinaryOperator("implies", left, right);
    }

    @Override
    public AstNode visitTypeExpression(FhirPathParser.TypeExpressionContext ctx) {
        throw new UnsupportedOperationException("Type expressions (is, as) are not yet supported");
    }

    // Unsupported literal types
    @Override
    public AstNode visitDateLiteral(FhirPathParser.DateLiteralContext ctx) {
        throw new UnsupportedOperationException("Date literals are not yet supported");
    }

    @Override
    public AstNode visitDateTimeLiteral(FhirPathParser.DateTimeLiteralContext ctx) {
        throw new UnsupportedOperationException("DateTime literals are not yet supported");
    }

    @Override
    public AstNode visitTimeLiteral(FhirPathParser.TimeLiteralContext ctx) {
        throw new UnsupportedOperationException("Time literals are not yet supported");
    }

    @Override
    public AstNode visitQuantityLiteral(FhirPathParser.QuantityLiteralContext ctx) {
        throw new UnsupportedOperationException("Quantity literals are not yet supported");
    }

    @Override
    public AstNode visitCodingLiteral(FhirPathParser.CodingLiteralContext ctx) {
        throw new UnsupportedOperationException("Coding literals are not yet supported");
    }

    // Special invocations
    @Override
    public AstNode visitThisInvocation(FhirPathParser.ThisInvocationContext ctx) {
        return AstIterationVariable.thisVariable();
    }

    @Override
    public AstNode visitIndexInvocation(FhirPathParser.IndexInvocationContext ctx) {
        throw new UnsupportedOperationException("$index invocations are not yet supported");
    }

    @Override
    public AstNode visitTotalInvocation(FhirPathParser.TotalInvocationContext ctx) {
        throw new UnsupportedOperationException("$total invocations are not yet supported");
    }

    @Override
    public AstNode visitExternalConstantTerm(FhirPathParser.ExternalConstantTermContext ctx) {
        return visit(ctx.externalConstant());
    }

    @Override
    public AstNode visitExternalConstant(FhirPathParser.ExternalConstantContext ctx) {
        String variableName;
        if (ctx.identifier() != null) {
            variableName = "%" + ctx.identifier().getText();
        } else if (ctx.STRING() != null) {
            String stringText = ctx.STRING().getText();
            // Remove surrounding quotes
            variableName = "%" + stringText.substring(1, stringText.length() - 1);
        } else {
            throw new IllegalArgumentException("Invalid external constant");
        }
        return new AstVariable(variableName);
    }
}
