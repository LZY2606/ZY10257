package dev.lasso.engine;

import java.util.Map;

/**
 * 三值卫式求值：缺失变量记为 UNKNOWN，UNKNOWN | true = true，UNKNOWN & false = false，
 * 从而“删掉不影响卫式结果的变量”仍可重放，而真正需要的变量不能省。
 */
public final class GuardEvaluator {

    public enum Tri { FALSE, TRUE, UNKNOWN }

    private GuardEvaluator() {
    }

    public static Tri eval(String guard, Map<String, Object> values) {
        Parser parser = new Parser(guard, values);
        Tri result = parser.parseExpression();
        if (parser.error != null) {
            return Tri.UNKNOWN;
        }
        return result;
    }

    public static boolean isEnabled(String guard, Map<String, Object> values) {
        return eval(guard, values) == Tri.TRUE;
    }

    private static final class Parser {
        private final String s;
        private final Map<String, Object> values;
        private int pos;
        private String error;

        Parser(String s, Map<String, Object> values) {
            this.s = s == null ? "true" : s.trim();
            this.values = values;
        }

        Tri parseExpression() {
            Tri left = parseAnd();
            skip();
            while (match("||")) {
                Tri right = parseAnd();
                left = or(left, right);
                skip();
            }
            return left;
        }

        Tri parseAnd() {
            Tri left = parseAtom();
            skip();
            while (match("&&")) {
                Tri right = parseAtom();
                left = and(left, right);
                skip();
            }
            return left;
        }

        Tri parseAtom() {
            skip();
            if (consume("(")) {
                Tri inner = parseExpression();
                skip();
                if (!consume(")")) {
                    error = "missing )";
                }
                return inner;
            }
            if (consume("!")) {
                return not(parseAtom());
            }
            if (consume("true")) {
                return Tri.TRUE;
            }
            if (consume("false")) {
                return Tri.FALSE;
            }
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) {
                pos++;
            }
            if (pos == start) {
                error = "expected atom";
                return Tri.UNKNOWN;
            }
            String name = s.substring(start, pos);
            Object value = values.get(name);
            if (value == null) {
                return Tri.UNKNOWN;
            }
            if (Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue() == 1)) {
                return Tri.TRUE;
            }
            if (Boolean.FALSE.equals(value) || (value instanceof Number n && n.intValue() == 0)) {
                return Tri.FALSE;
            }
            error = "non-boolean value for " + name;
            return Tri.UNKNOWN;
        }

        private boolean consume(String token) {
            skip();
            if (s.regionMatches(true, pos, token, 0, token.length())) {
                pos += token.length();
                return true;
            }
            return false;
        }

        private boolean match(String op) {
            if (s.startsWith(op, pos)) {
                pos += op.length();
                return true;
            }
            return false;
        }

        private void skip() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private static Tri or(Tri a, Tri b) {
            if (a == Tri.TRUE || b == Tri.TRUE) {
                return Tri.TRUE;
            }
            if (a == Tri.FALSE && b == Tri.FALSE) {
                return Tri.FALSE;
            }
            return Tri.UNKNOWN;
        }

        private static Tri and(Tri a, Tri b) {
            if (a == Tri.FALSE || b == Tri.FALSE) {
                return Tri.FALSE;
            }
            if (a == Tri.TRUE && b == Tri.TRUE) {
                return Tri.TRUE;
            }
            return Tri.UNKNOWN;
        }

        private static Tri not(Tri a) {
            return switch (a) {
                case TRUE -> Tri.FALSE;
                case FALSE -> Tri.TRUE;
                case UNKNOWN -> Tri.UNKNOWN;
            };
        }
    }
}
