package com.lasso.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 转移卫式求值器。
 * 文法：or  := and ('||' and)*
 *       and := eq ('&&' eq)*
 *       eq  := rel (('=='|'!=') rel)*
 *       rel := add (('&lt;'|'&lt;='|'&gt;'|'&gt;=') add)*
 *       add := mul (('+'|'-') mul)*
 *       mul := unary (('*'|'/'|'%') unary)*
 *       unary := ('!'|'-') unary | primary
 * 引用未定义变量的卫式按“未启用”处理（fail-closed）。
 */
public final class GuardEvaluator {

    private GuardEvaluator() {
    }

    public static boolean isEnabled(String guard, Map<String, Object> variables) {
        if (guard == null || guard.isBlank()) {
            return true;
        }
        try {
            Object value = new Parser(tokenize(guard), variables).parseOr();
            return truthy(value);
        } catch (UndefinedVariableException e) {
            return false;
        }
    }

    public static List<String> referencedVariables(String guard) {
        List<String> result = new ArrayList<>();
        if (guard == null || guard.isBlank()) {
            return result;
        }
        for (Token token : tokenize(guard)) {
            if (token.kind == TokenKind.IDENT
                    && !token.text.equals("true") && !token.text.equals("false")
                    && !result.contains(token.text)) {
                result.add(token.text);
            }
        }
        return result;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        return false;
    }

    private enum TokenKind { NUMBER, IDENT, OP, LPAREN, RPAREN, EOF }

    private record Token(TokenKind kind, String text) {
    }

    private static final class UndefinedVariableException extends RuntimeException {
        UndefinedVariableException(String name) {
            super(name);
        }
    }

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(input.charAt(i + 1)))) {
                int start = i;
                while (i < n && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenKind.NUMBER, input.substring(start, i)));
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < n && Character.isJavaIdentifierPart(input.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenKind.IDENT, input.substring(start, i)));
            } else if (c == '(') {
                tokens.add(new Token(TokenKind.LPAREN, "("));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(TokenKind.RPAREN, ")"));
                i++;
            } else {
                String two = i + 1 < n ? input.substring(i, i + 2) : "";
                if (two.equals("==") || two.equals("!=") || two.equals("<=")
                        || two.equals(">=") || two.equals("&&") || two.equals("||")) {
                    tokens.add(new Token(TokenKind.OP, two));
                    i += 2;
                } else if ("<>+-*/%!".indexOf(c) >= 0) {
                    tokens.add(new Token(TokenKind.OP, String.valueOf(c)));
                    i++;
                } else {
                    throw new IllegalArgumentException("无法识别的卫式字符: " + c);
                }
            }
        }
        tokens.add(new Token(TokenKind.EOF, ""));
        return tokens;
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final Map<String, Object> vars;
        private int pos;

        private Parser(List<Token> tokens, Map<String, Object> vars) {
            this.tokens = tokens;
            this.vars = vars;
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private Token consume() {
            return tokens.get(pos++);
        }

        private boolean matchOp(String op) {
            if (peek().kind == TokenKind.OP && peek().text.equals(op)) {
                pos++;
                return true;
            }
            return false;
        }

        Object parseOr() {
            Object left = parseAnd();
            while (matchOp("||")) {
                Object right = parseAnd();
                left = truthy(left) || truthy(right);
            }
            return left;
        }

        private Object parseAnd() {
            Object left = parseEq();
            while (matchOp("&&")) {
                Object right = parseEq();
                left = truthy(left) && truthy(right);
            }
            return left;
        }

        private Object parseEq() {
            Object left = parseRel();
            while (peek().kind == TokenKind.OP
                    && (peek().text.equals("==") || peek().text.equals("!="))) {
                String op = consume().text;
                Object right = parseRel();
                boolean equal = valueEquals(left, right);
                left = op.equals("==") == equal;
            }
            return left;
        }

        private Object parseRel() {
            Object left = parseAdd();
            while (peek().kind == TokenKind.OP
                    && (peek().text.equals("<") || peek().text.equals("<=")
                    || peek().text.equals(">") || peek().text.equals(">="))) {
                String op = consume().text;
                Object right = parseAdd();
                double a = asNumber(left);
                double b = asNumber(right);
                left = switch (op) {
                    case "<" -> a < b;
                    case "<=" -> a <= b;
                    case ">" -> a > b;
                    default -> a >= b;
                };
            }
            return left;
        }

        private Object parseAdd() {
            Object left = parseMul();
            while (peek().kind == TokenKind.OP
                    && (peek().text.equals("+") || peek().text.equals("-"))) {
                String op = consume().text;
                Object right = parseMul();
                if (op.equals("+") && (left instanceof String || right instanceof String)) {
                    left = String.valueOf(left) + right;
                } else {
                    left = op.equals("+") ? addNumbers(left, right) : subNumbers(left, right);
                }
            }
            return left;
        }

        private Object parseMul() {
            Object left = parseUnary();
            while (peek().kind == TokenKind.OP
                    && (peek().text.equals("*") || peek().text.equals("/") || peek().text.equals("%"))) {
                String op = consume().text;
                Object right = parseUnary();
                if (op.equals("*")) {
                    left = mulNumbers(left, right);
                } else if (op.equals("/")) {
                    left = divNumbers(left, right);
                } else {
                    left = modNumbers(left, right);
                }
            }
            return left;
        }

        private Object parseUnary() {
            if (matchOp("!")) {
                return !truthy(parseUnary());
            }
            if (matchOp("-")) {
                Object value = parseUnary();
                if (value instanceof Long l) {
                    return -l;
                }
                return -asNumber(value);
            }
            return parsePrimary();
        }

        private Object parsePrimary() {
            Token token = consume();
            return switch (token.kind) {
                case NUMBER -> parseNumber(token.text);
                case IDENT -> switch (token.text) {
                    case "true" -> Boolean.TRUE;
                    case "false" -> Boolean.FALSE;
                    default -> {
                        if (!vars.containsKey(token.text)) {
                            throw new UndefinedVariableException(token.text);
                        }
                        yield vars.get(token.text);
                    }
                };
                case LPAREN -> {
                    Object value = parseOr();
                    if (peek().kind != TokenKind.RPAREN) {
                        throw new IllegalArgumentException("缺少右括号");
                    }
                    consume();
                    yield value;
                }
                default -> throw new IllegalArgumentException("意外的卫式记号: " + token.text);
            };
        }
    }

    private static Object parseNumber(String text) {
        if (text.contains(".")) {
            return Double.parseDouble(text);
        }
        return Long.parseLong(text);
    }

    private static boolean valueEquals(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            if (a instanceof Long && b instanceof Long) {
                return ((Long) a).longValue() == ((Long) b).longValue();
            }
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        if (a instanceof Boolean || b instanceof Boolean) {
            return a instanceof Boolean x && b instanceof Boolean y && x.equals(y);
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static double asNumber(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static Object addNumbers(Object a, Object b) {
        if (a instanceof Long && b instanceof Long) {
            return (Long) a + (Long) b;
        }
        return asNumber(a) + asNumber(b);
    }

    private static Object subNumbers(Object a, Object b) {
        if (a instanceof Long && b instanceof Long) {
            return (Long) a - (Long) b;
        }
        return asNumber(a) - asNumber(b);
    }

    private static Object mulNumbers(Object a, Object b) {
        if (a instanceof Long && b instanceof Long) {
            return (Long) a * (Long) b;
        }
        return asNumber(a) * asNumber(b);
    }

    private static Object divNumbers(Object a, Object b) {
        if (a instanceof Long && b instanceof Long) {
            long divisor = (Long) b;
            if (divisor == 0) {
                throw new ArithmeticException("卫式除零");
            }
            return (Long) a / divisor;
        }
        return asNumber(a) / asNumber(b);
    }

    private static Object modNumbers(Object a, Object b) {
        if (a instanceof Long && b instanceof Long) {
            long divisor = (Long) b;
            if (divisor == 0) {
                throw new ArithmeticException("卫式取模零");
            }
            return (Long) a % divisor;
        }
        return asNumber(a) % asNumber(b);
    }
}
