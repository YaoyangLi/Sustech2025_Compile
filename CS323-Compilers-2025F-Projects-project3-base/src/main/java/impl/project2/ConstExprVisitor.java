package impl.project2;

import generated.Splc.SplcBaseVisitor;
import generated.Splc.SplcParser;

// ...existing code...
public class ConstExprVisitor extends SplcBaseVisitor<Integer> {
    @Override
    public Integer visitExpression(SplcParser.ExpressionContext ctx) {
        return evaluate(ctx);
    }

    private Integer evaluate(SplcParser.ExpressionContext ctx) {
        if (ctx == null) {
            return null;
        }

        if (ctx.Number() != null && ctx.getChildCount() == 1) {
            return parseInteger(ctx.Number().getText());
        }

        if (ctx.getChildCount() == 3
                && "(".equals(ctx.getChild(0).getText())
                && ")".equals(ctx.getChild(2).getText())
                && ctx.expression().size() == 1) {
            return evaluate(ctx.expression(0));
        }

        if (ctx.getChildCount() == 2 && ctx.expression().size() == 1) {
            String op = ctx.getChild(0).getText();
            if ("+".equals(op) || "-".equals(op)) {
                Integer value = evaluate(ctx.expression(0));
                if (value == null) {
                    return null;
                }
                return "+".equals(op) ? value : -value;
            }
            return null;
        }

        if (ctx.getChildCount() == 3 && ctx.expression().size() == 2) {
            String op = ctx.getChild(1).getText();
            if (!isBinaryArithmetic(op)) {
                return null;
            }
            Integer left = evaluate(ctx.expression(0));
            Integer right = evaluate(ctx.expression(1));
            if (left == null || right == null) {
                return null;
            }
            return applyBinary(op, left, right);
        }

        return null;
    }

    private boolean isBinaryArithmetic(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op) || "%".equals(op);
    }

    private Integer applyBinary(String op, int left, int right) {
        switch (op) {
            case "+":
                return left + right;
            case "-":
                return left - right;
            case "*":
                return left * right;
            case "/":
                if (right == 0) {
                    return null;
                }
                return left / right;
            case "%":
                if (right == 0) {
                    return null;
                }
                return left % right;
            default:
                return null;
        }
    }

    private Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}