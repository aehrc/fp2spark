package com.example.fhirpath.parser;

import com.example.fhirpath.ast.AstBinaryOperator;
import com.example.fhirpath.ast.AstFunctionCall;
import com.example.fhirpath.ast.AstIterationVariable;
import com.example.fhirpath.ast.AstLiteral;
import com.example.fhirpath.ast.AstNode;
import com.example.fhirpath.ast.AstTraversal;
import com.example.fhirpath.ast.AstVariable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ANTLR visitor that builds an AST from a parsed FHIRPath expression.
 *
 * <p>Transforms the ANTLR parse tree into a higher-level AST representation used for semantic
 * analysis.
 */
public class AstBuilderVisitor extends FhirPathBaseVisitor<AstNode> {

  @Override
  public AstNode visitEntireExpression(final FhirPathParser.EntireExpressionContext ctx) {
    return visit(ctx.expression());
  }

  // Handle different expression types - for now, focus on the ones we support
  @Override
  public AstNode visitTermExpression(final FhirPathParser.TermExpressionContext ctx) {
    return visit(ctx.term());
  }

  @Override
  public AstNode visitAdditiveExpression(final FhirPathParser.AdditiveExpressionContext ctx) {
    final AstNode left = visit(ctx.expression(0));
    final AstNode right = visit(ctx.expression(1));

    final String op = ctx.getChild(1).getText();
    return new AstBinaryOperator(op, left, right);
  }

  @Override
  public AstNode visitMultiplicativeExpression(
      final FhirPathParser.MultiplicativeExpressionContext ctx) {
    final AstNode left = visit(ctx.expression(0));
    final AstNode right = visit(ctx.expression(1));

    final String op = ctx.getChild(1).getText();
    return new AstBinaryOperator(op, left, right);
  }

  @Override
  public AstNode visitPolarityExpression(final FhirPathParser.PolarityExpressionContext ctx) {
    final AstNode operand = visit(ctx.expression());
    final String op = ctx.getChild(0).getText();

    final String functionName =
        switch (op) {
          case "+" -> "unaryPlus";
          case "-" -> "unaryMinus";
          default -> throw new IllegalArgumentException("Unknown polarity operator: " + op);
        };

    return new AstFunctionCall(functionName, List.of(operand));
  }

  // Term handling
  @Override
  public AstNode visitLiteralTerm(final FhirPathParser.LiteralTermContext ctx) {
    return visit(ctx.literal());
  }

  @Override
  public AstNode visitInvocationTerm(final FhirPathParser.InvocationTermContext ctx) {
    return visit(ctx.invocation());
  }

  @Override
  public AstNode visitParenthesizedTerm(final FhirPathParser.ParenthesizedTermContext ctx) {
    return visit(ctx.expression());
  }

  // Literal handling
  @Override
  public AstNode visitStringLiteral(final FhirPathParser.StringLiteralContext ctx) {
    final String text = ctx.STRING().getText();
    // Remove surrounding quotes
    final String quotesRemoved = text.substring(1, text.length() - 1);
    // Process escape sequences per FHIRPath spec
    final String value = StringEscapeUtils.unescapeFhirPathString(quotesRemoved);
    return new AstLiteral(value);
  }

  @Override
  public AstNode visitNumberLiteral(final FhirPathParser.NumberLiteralContext ctx) {
    final String text = ctx.NUMBER().getText();
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
  public AstNode visitLongNumberLiteral(final FhirPathParser.LongNumberLiteralContext ctx) {
    String text = ctx.LONGNUMBER().getText();
    // Remove 'L' suffix if present
    if (text.endsWith("L")) {
      text = text.substring(0, text.length() - 1);
    }
    return new AstLiteral(Long.parseLong(text));
  }

  @Override
  public AstNode visitBooleanLiteral(final FhirPathParser.BooleanLiteralContext ctx) {
    final String text = ctx.getText();
    return new AstLiteral(Boolean.parseBoolean(text));
  }

  @Override
  public AstNode visitNullLiteral(final FhirPathParser.NullLiteralContext ctx) {
    return new AstLiteral(null);
  }

  // Invocation handling
  @Override
  public AstNode visitMemberInvocation(final FhirPathParser.MemberInvocationContext ctx) {
    final String identifier = ctx.identifier().getText();
    return new AstTraversal(identifier); // Uses convenience constructor (target = null)
  }

  @Override
  public AstNode visitFunctionInvocation(final FhirPathParser.FunctionInvocationContext ctx) {
    return visit(ctx.function());
  }

  @Override
  public AstNode visitFunction(final FhirPathParser.FunctionContext ctx) {
    final String functionName = ctx.identifier().getText();
    final List<AstNode> arguments = new ArrayList<>();

    if (ctx.paramList() != null) {
      for (final var expr : ctx.paramList().expression()) {
        arguments.add(visit(expr));
      }
    }

    return new AstFunctionCall(functionName, arguments);
  }

  @Override
  public AstNode visitIdentifier(final FhirPathParser.IdentifierContext ctx) {
    return new AstTraversal(ctx.getText()); // Uses convenience constructor (target = null)
  }

  // Default behavior for unsupported expressions - throw informative errors
  @Override
  public AstNode visitInvocationExpression(final FhirPathParser.InvocationExpressionContext ctx) {
    // Handle method-style function calls like "10.count()" or "expr.exists()"
    final AstNode target = visit(ctx.expression());
    final AstNode invocation = visit(ctx.invocation());

    // Convert invocation to function call with target stored separately
    if (invocation instanceof AstFunctionCall funcCall) {
      // Create new function call with target stored in the target field
      return funcCall.withTarget(target);
    } else if (invocation instanceof AstTraversal traversal) {
      // Handle member access like "expr.field" - create traversal with target
      return new AstTraversal(traversal.path(), target);
    }

    throw new UnsupportedOperationException(
        "Unsupported invocation type: " + invocation.getClass());
  }

  @Override
  public AstNode visitIndexerExpression(final FhirPathParser.IndexerExpressionContext ctx) {
    throw new UnsupportedOperationException("Indexer expressions ([]) are not yet supported");
  }

  @Override
  public AstNode visitCombineExpression(final FhirPathParser.CombineExpressionContext ctx) {
    final AstNode left = visit(ctx.expression(0));
    final AstNode right = visit(ctx.expression(1));

    final String op = ctx.getChild(1).getText(); // Should be "|" or ";'
    return new AstBinaryOperator(op, left, right);
  }

  @Override
  public AstNode visitEqualityExpression(final FhirPathParser.EqualityExpressionContext ctx) {
    final AstNode left = visit(ctx.expression(0));
    final AstNode right = visit(ctx.expression(1));

    final String op = ctx.getChild(1).getText();
    return new AstBinaryOperator(op, left, right);
  }

  @Override
  public AstNode visitInequalityExpression(final FhirPathParser.InequalityExpressionContext ctx) {
    final AstNode left = visit(ctx.expression(0));
    final AstNode right = visit(ctx.expression(1));

    final String op = ctx.getChild(1).getText();
    return new AstBinaryOperator(op, left, right);
  }

  @Override
  public AstNode visitMembershipExpression(final FhirPathParser.MembershipExpressionContext ctx) {
    throw new UnsupportedOperationException(
        "Membership expressions (in, contains) are not yet supported");
  }

  @Override
  public AstNode visitAndExpression(final FhirPathParser.AndExpressionContext ctx) {
    final AstNode left = visit(ctx.expression(0));
    final AstNode right = visit(ctx.expression(1));

    return new AstBinaryOperator("and", left, right);
  }

  @Override
  public AstNode visitOrExpression(final FhirPathParser.OrExpressionContext ctx) {
    final AstNode left = visit(ctx.expression(0));
    final AstNode right = visit(ctx.expression(1));

    final String op = ctx.getChild(1).getText(); // Could be "or" or "xor"
    return new AstBinaryOperator(op, left, right);
  }

  @Override
  public AstNode visitImpliesExpression(final FhirPathParser.ImpliesExpressionContext ctx) {
    final AstNode left = visit(ctx.expression(0));
    final AstNode right = visit(ctx.expression(1));

    return new AstBinaryOperator("implies", left, right);
  }

  @Override
  public AstNode visitTypeExpression(final FhirPathParser.TypeExpressionContext ctx) {
    throw new UnsupportedOperationException("Type expressions (is, as) are not yet supported");
  }

  // Unsupported literal types
  @Override
  public AstNode visitDateLiteral(final FhirPathParser.DateLiteralContext ctx) {
    throw new UnsupportedOperationException("Date literals are not yet supported");
  }

  @Override
  public AstNode visitDateTimeLiteral(final FhirPathParser.DateTimeLiteralContext ctx) {
    throw new UnsupportedOperationException("DateTime literals are not yet supported");
  }

  @Override
  public AstNode visitTimeLiteral(final FhirPathParser.TimeLiteralContext ctx) {
    throw new UnsupportedOperationException("Time literals are not yet supported");
  }

  @Override
  public AstNode visitQuantityLiteral(final FhirPathParser.QuantityLiteralContext ctx) {
    throw new UnsupportedOperationException("Quantity literals are not yet supported");
  }

  @Override
  public AstNode visitCodingLiteral(final FhirPathParser.CodingLiteralContext ctx) {
    throw new UnsupportedOperationException("Coding literals are not yet supported");
  }

  // Special invocations
  @Override
  public AstNode visitThisInvocation(final FhirPathParser.ThisInvocationContext ctx) {
    return AstIterationVariable.thisVariable();
  }

  @Override
  public AstNode visitIndexInvocation(final FhirPathParser.IndexInvocationContext ctx) {
    throw new UnsupportedOperationException("$index invocations are not yet supported");
  }

  @Override
  public AstNode visitTotalInvocation(final FhirPathParser.TotalInvocationContext ctx) {
    throw new UnsupportedOperationException("$total invocations are not yet supported");
  }

  @Override
  public AstNode visitExternalConstantTerm(final FhirPathParser.ExternalConstantTermContext ctx) {
    return visit(ctx.externalConstant());
  }

  @Override
  public AstNode visitExternalConstant(final FhirPathParser.ExternalConstantContext ctx) {
    final String variableName;
    if (ctx.identifier() != null) {
      variableName = "%" + ctx.identifier().getText();
    } else if (ctx.STRING() != null) {
      final String stringText = ctx.STRING().getText();
      // Remove surrounding quotes
      variableName = "%" + stringText.substring(1, stringText.length() - 1);
    } else {
      throw new IllegalArgumentException("Invalid external constant");
    }
    return new AstVariable(variableName);
  }
}
