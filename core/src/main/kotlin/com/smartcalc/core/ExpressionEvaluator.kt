package com.smartcalc.core

import kotlin.math.*

enum class AngleMode { DEG, RAD }

class CalcException(message: String) : Exception(message)

/**
 * Recursive-descent evaluator for calculator expressions.
 *
 * Supports: + - * / ^ (right associative), unary minus, postfix ! and %,
 * parentheses, implicit multiplication (2π, 3(4+1), 2sin(30)),
 * constants (pi, π, e, ans), and common scientific functions.
 */
class ExpressionEvaluator(
    var angleMode: AngleMode = AngleMode.DEG,
    var ans: Double = 0.0,
) {
    private lateinit var tokens: List<Token>
    private var pos = 0

    fun evaluate(input: String): Double {
        val normalized = normalize(input)
        if (normalized.isBlank()) throw CalcException("Empty expression")
        tokens = tokenize(normalized)
        pos = 0
        val value = parseExpression()
        if (pos < tokens.size) throw CalcException("Unexpected '${tokens[pos].text}'")
        if (value.isNaN()) throw CalcException("Not a number")
        return value
    }

    /** Returns null instead of throwing — handy for live previews. */
    fun tryEvaluate(input: String): Double? = try {
        evaluate(input)
    } catch (_: Exception) {
        null
    }

    // ---------- Tokenizer ----------

    private sealed class Token(val text: String) {
        class Num(val value: Double, text: String) : Token(text)
        class Ident(text: String) : Token(text)
        class Op(text: String) : Token(text)
        object LParen : Token("(")
        object RParen : Token(")")
        object Comma : Token(",")
    }

    private fun normalize(s: String): String = s
        .replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-')
        .replace("√", "sqrt").replace("∛", "cbrt").replace("**", "^")
        .replace(",", "") // thousands separators (1,000) — functions take one argument
        .trim()

    private fun tokenize(s: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    // scientific notation: 1e5, 2.5E-3
                    if (i < s.length && (s[i] == 'e' || s[i] == 'E') && i + 1 < s.length &&
                        (s[i + 1].isDigit() || ((s[i + 1] == '-' || s[i + 1] == '+') && i + 2 < s.length && s[i + 2].isDigit()))
                    ) {
                        i += 2
                        while (i < s.length && s[i].isDigit()) i++
                    }
                    val text = s.substring(start, i)
                    val v = text.toDoubleOrNull() ?: throw CalcException("Bad number '$text'")
                    out += Token.Num(v, text)
                }
                c.isLetter() || c == 'π' -> {
                    if (c == 'π') { out += Token.Ident("pi"); i++; continue }
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    out += Token.Ident(s.substring(start, i).lowercase())
                }
                c == '(' || c == '[' -> { out += Token.LParen; i++ }
                c == ')' || c == ']' -> { out += Token.RParen; i++ }
                c in "+-*/^!%" -> { out += Token.Op(c.toString()); i++ }
                else -> throw CalcException("Unknown symbol '$c'")
            }
        }
        return out
    }

    // ---------- Parser ----------
    // expression := term (('+'|'-') term)*
    // term       := unary (('*'|'/'|implicit) unary)*
    // unary      := ('-'|'+') unary | power
    // power      := postfix ('^' unary)?
    // postfix    := primary ('!' | '%')*
    // primary    := number | constant | func primary | '(' expression ')'

    private fun peek(): Token? = tokens.getOrNull(pos)
    private fun isOp(t: Token?, op: String) = t is Token.Op && t.text == op

    private fun parseExpression(): Double {
        var left = parseTerm()
        while (true) {
            val t = peek()
            val plus = isOp(t, "+")
            if (!plus && !isOp(t, "-")) return left
            pos++
            val right = parseTerm()
            // Calculator-style percent: 200 + 10% = 220, 200 - 10% = 180
            val pct = isOp(tokens[pos - 1], "%")
            val delta = if (pct) left * right else right
            left = if (plus) left + delta else left - delta
        }
    }

    private fun startsPrimary(t: Token?) =
        t is Token.Num || t is Token.Ident || t is Token.LParen

    private fun parseTerm(): Double {
        var left = parseUnary()
        while (true) {
            val t = peek()
            left = when {
                isOp(t, "*") -> { pos++; left * parseUnary() }
                isOp(t, "/") -> {
                    pos++
                    val r = parseUnary()
                    if (r == 0.0) throw CalcException("Cannot divide by zero")
                    left / r
                }
                startsPrimary(t) -> left * parseUnary() // implicit multiplication
                else -> return left
            }
        }
    }

    private fun parseUnary(): Double {
        val t = peek()
        if (isOp(t, "-")) { pos++; return -parseUnary() }
        if (isOp(t, "+")) { pos++; return parseUnary() }
        return parsePower()
    }

    private fun parsePower(): Double {
        val base = parsePostfix()
        if (isOp(peek(), "^")) {
            pos++
            val exp = parseUnary()
            return base.pow(exp)
        }
        return base
    }

    private fun parsePostfix(): Double {
        var v = parsePrimary()
        while (true) {
            val t = peek()
            v = when {
                isOp(t, "!") -> { pos++; factorial(v) }
                isOp(t, "%") -> { pos++; v / 100.0 }
                else -> return v
            }
        }
    }

    private fun parsePrimary(): Double {
        val t = peek() ?: throw CalcException("Incomplete expression")
        return when (t) {
            is Token.Num -> { pos++; t.value }
            is Token.LParen -> {
                pos++
                val v = parseExpression()
                // Be forgiving: allow a missing closing parenthesis at the end.
                if (peek() is Token.RParen) pos++
                else if (peek() != null) throw CalcException("Missing ')'")
                v
            }
            is Token.Ident -> { pos++; identifier(t.text) }
            else -> throw CalcException("Unexpected '${t.text}'")
        }
    }

    private fun identifier(name: String): Double {
        when (name) {
            "pi" -> return PI
            "e" -> return E
            "ans" -> return ans
            "phi" -> return (1 + sqrt(5.0)) / 2
        }
        val fn = FUNCTIONS[name] ?: throw CalcException("Unknown function '$name'")
        // Function argument: a parenthesised expression, or the next power-level operand (e.g. "sin 30", "√9").
        val arg = if (peek() is Token.LParen) parsePrimary() else parsePower()
        return fn(arg)
    }

    private fun toRad(x: Double) = if (angleMode == AngleMode.DEG) Math.toRadians(x) else x
    private fun fromRad(x: Double) = if (angleMode == AngleMode.DEG) Math.toDegrees(x) else x

    /** Snap values like sin(180°) = 1.22e-16 to exact 0. */
    private fun clean(x: Double) = if (abs(x) < 1e-12) 0.0 else x

    private val FUNCTIONS: Map<String, (Double) -> Double> = mapOf(
        "sin" to { x -> clean(sin(toRad(x))) },
        "cos" to { x -> clean(cos(toRad(x))) },
        "tan" to { x ->
            val c = cos(toRad(x))
            if (abs(c) < 1e-12) throw CalcException("tan is undefined here")
            clean(tan(toRad(x)))
        },
        "asin" to { x -> fromRad(asin(x)) },
        "acos" to { x -> fromRad(acos(x)) },
        "atan" to { x -> fromRad(atan(x)) },
        "sinh" to { x -> sinh(x) },
        "cosh" to { x -> cosh(x) },
        "tanh" to { x -> tanh(x) },
        "sqrt" to { x ->
            if (x < 0) throw CalcException("Square root of a negative number")
            sqrt(x)
        },
        "cbrt" to { x -> cbrt(x) },
        "log" to { x ->
            if (x <= 0) throw CalcException("log needs a positive number")
            log10(x)
        },
        "ln" to { x ->
            if (x <= 0) throw CalcException("ln needs a positive number")
            ln(x)
        },
        "exp" to { x -> exp(x) },
        "abs" to { x -> abs(x) },
        "floor" to { x -> floor(x) },
        "ceil" to { x -> ceil(x) },
        "round" to { x -> x.roundToLong().toDouble() },
    )

    companion object {
        fun factorial(n: Double): Double {
            if (n < 0 || n != floor(n)) throw CalcException("Factorial needs a whole number ≥ 0")
            if (n > 170) throw CalcException("Number too large for factorial")
            var r = 1.0
            for (i in 2..n.toInt()) r *= i
            return r
        }
    }
}
