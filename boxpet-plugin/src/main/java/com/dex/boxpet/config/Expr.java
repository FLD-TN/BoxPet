package com.dex.boxpet.config;

import java.util.function.DoubleUnaryOperator;

/**
 * Tiny arithmetic expression of one variable {@code L}: numbers, {@code + - * / ^ %}, parentheses, unary minus and
 * {@code floor() ceil() round() sqrt() abs()}. Used by levels.yml ({@code 100 + 5 * L^2}).
 */
public final class Expr {

    private final String src;
    private int pos;

    private Expr(String src) {
        this.src = src;
    }

    /** Parses once; the returned function evaluates the expression for a value of {@code L}. */
    public static DoubleUnaryOperator compile(String expression) {
        Node node = new Expr(expression.replace(" ", "")).parseAll();
        return node::eval;
    }

    private interface Node {
        double eval(double l);
    }

    private Node parseAll() {
        Node n = parseSum();
        if (pos != src.length()) throw new IllegalArgumentException("unexpected '" + src.charAt(pos) + "' at " + pos);
        return n;
    }

    private Node parseSum() {
        Node left = parseProduct();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c != '+' && c != '-') break;
            pos++;
            Node a = left;
            Node b = parseProduct();
            left = c == '+' ? l -> a.eval(l) + b.eval(l) : l -> a.eval(l) - b.eval(l);
        }
        return left;
    }

    private Node parseProduct() {
        Node left = parsePower();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c != '*' && c != '/' && c != '%') break;
            pos++;
            Node a = left;
            Node b = parsePower();
            left = switch (c) {
                case '*' -> l -> a.eval(l) * b.eval(l);
                case '/' -> l -> a.eval(l) / b.eval(l);
                default -> l -> a.eval(l) % b.eval(l);
            };
        }
        return left;
    }

    private Node parsePower() {
        Node base = parseUnary();
        if (pos < src.length() && src.charAt(pos) == '^') {
            pos++;
            Node exp = parsePower();
            return l -> Math.pow(base.eval(l), exp.eval(l));
        }
        return base;
    }

    private Node parseUnary() {
        if (pos < src.length() && src.charAt(pos) == '-') {
            pos++;
            Node n = parseUnary();
            return l -> -n.eval(l);
        }
        if (pos < src.length() && src.charAt(pos) == '+') {
            pos++;
            return parseUnary();
        }
        return parseAtom();
    }

    private Node parseAtom() {
        if (pos >= src.length()) throw new IllegalArgumentException("unexpected end");
        char c = src.charAt(pos);
        if (c == '(') {
            pos++;
            Node n = parseSum();
            expect(')');
            return n;
        }
        if (Character.isDigit(c) || c == '.') {
            int start = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
            double v = Double.parseDouble(src.substring(start, pos));
            return l -> v;
        }
        if (Character.isLetter(c)) {
            int start = pos;
            while (pos < src.length() && Character.isLetter(src.charAt(pos))) pos++;
            String word = src.substring(start, pos);
            if (word.equalsIgnoreCase("L")) return l -> l;
            DoubleUnaryOperator f = switch (word.toLowerCase()) {
                case "floor" -> Math::floor;
                case "ceil" -> Math::ceil;
                case "round" -> d -> Math.round(d);
                case "sqrt" -> Math::sqrt;
                case "abs" -> Math::abs;
                default -> throw new IllegalArgumentException("unknown name '" + word + "'");
            };
            expect('(');
            Node arg = parseSum();
            expect(')');
            return l -> f.applyAsDouble(arg.eval(l));
        }
        throw new IllegalArgumentException("unexpected '" + c + "' at " + pos);
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) throw new IllegalArgumentException("expected '" + c + "' at " + pos);
        pos++;
    }
}
