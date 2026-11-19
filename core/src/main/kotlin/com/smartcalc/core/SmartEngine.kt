package com.smartcalc.core

import kotlin.math.pow

/** The result of a smart (natural language) calculation, with human-readable steps. */
data class SmartResult(
    val query: String,
    val value: Double,
    val display: String,
    val interpretation: String,
    val steps: List<String>,
)

/**
 * Offline "smart" engine: understands everyday phrases such as
 *  - "15% of 2400", "what is 45 plus 12 times 3", "square root of 144"
 *  - "250 is what percent of 1000", "increase 500 by 20%", "1200 minus 15%"
 *  - "18% gst on 1500", "tip 15% on 850", "discount 30% on 2999"
 *  - "10 km to miles", "100 f in c", "5 gb to mb"
 *  - "emi 500000 at 8.5% for 20 years", "simple interest 10000 at 7% for 3 years",
 *    "compound interest 10000 at 8% for 5 years"
 *  - "average of 4, 8, 15, 16", "sum of 1 2 3", "bmi 70 kg 175 cm"
 * Falls back to the scientific evaluator for plain expressions.
 */
class SmartEngine(private val evaluator: ExpressionEvaluator = ExpressionEvaluator()) {

    private val num = """(-?\d+(?:,\d{3})*(?:\.\d+)?|-?\.\d+)"""

    private fun n(s: String) = s.replace(",", "").toDouble()
    private fun f(v: Double) = NumberFormatter.format(v, grouping = true)

    fun solve(raw: String): SmartResult {
        val q = cleanup(raw)
        if (q.isBlank()) throw CalcException("Type or say a question")

        for (rule in rules) {
            val m = rule.regex.find(q) ?: continue
            if (m.range.first != 0 || m.range.last != q.length - 1) continue
            return rule.handler(raw.trim(), m.groupValues)
        }

        // Fallback: translate words to operators and evaluate.
        val expr = wordsToExpression(q)
        val v = evaluator.evaluate(expr)
        return SmartResult(raw.trim(), v, f(v), "Expression: $expr", listOf("$expr = ${f(v)}"))
    }

    private fun cleanup(s: String): String = s.lowercase()
        .replace(Regex("""[?=]+\s*$"""), "")
        .replace(Regex("""^(please\s+)?(calculate|compute|find|evaluate|solve|what is|what's|whats|how much is|tell me)\s+"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .removeSuffix(".")

    private class Rule(val regex: Regex, val handler: (String, List<String>) -> SmartResult)

    private fun rule(pattern: String, handler: (String, List<String>) -> SmartResult) =
        Rule(Regex(pattern), handler)

    private val rules: List<Rule> = listOf(
        // X is what percent of Y
        rule("""$num is what (?:percent|percentage|%) of $num""") { q, g ->
            val a = n(g[1]); val b = n(g[2])
            if (b == 0.0) throw CalcException("Cannot divide by zero")
            val v = a / b * 100
            SmartResult(q, v, "${f(v)}%", "Percentage of a total",
                listOf("Percentage = (part ÷ whole) × 100", "= (${f(a)} ÷ ${f(b)}) × 100", "= ${f(v)}%"))
        },
        // X% of Y
        rule("""$num ?(?:%|percent|per cent) of $num""") { q, g ->
            val p = n(g[1]); val b = n(g[2])
            val v = b * p / 100
            SmartResult(q, v, f(v), "${f(p)}% of ${f(b)}",
                listOf("${f(p)}% of ${f(b)} = ${f(b)} × ${f(p)} ÷ 100", "= ${f(v)}"))
        },
        // percentage change from A to B
        rule("""(?:percent(?:age)? )?change from $num to $num""") { q, g ->
            val a = n(g[1]); val b = n(g[2])
            if (a == 0.0) throw CalcException("Starting value cannot be zero")
            val v = (b - a) / a * 100
            SmartResult(q, v, "${f(v)}%", "Percentage change",
                listOf("Change = (new − old) ÷ old × 100", "= (${f(b)} − ${f(a)}) ÷ ${f(a)} × 100", "= ${f(v)}%"))
        },
        // increase / decrease Y by X%
        rule("""(increase|decrease|reduce) $num by $num ?(?:%|percent)""") { q, g ->
            val up = g[1] == "increase"; val b = n(g[2]); val p = n(g[3])
            val delta = b * p / 100
            val v = if (up) b + delta else b - delta
            SmartResult(q, v, f(v), "${if (up) "Increase" else "Decrease"} by ${f(p)}%",
                listOf("${f(p)}% of ${f(b)} = ${f(delta)}", "${f(b)} ${if (up) "+" else "−"} ${f(delta)} = ${f(v)}"))
        },
        // GST / tax / VAT
        rule("""$num ?% ?(gst|tax|vat) (?:on|for) $num""") { q, g ->
            val p = n(g[1]); val label = g[2].uppercase(); val b = n(g[3])
            val tax = b * p / 100; val v = b + tax
            SmartResult(q, v, NumberFormatter.money(v), "$label at ${f(p)}%",
                listOf("$label = ${f(b)} × ${f(p)}% = ${NumberFormatter.money(tax)}",
                    "Total = ${f(b)} + ${NumberFormatter.money(tax)} = ${NumberFormatter.money(v)}"))
        },
        // Tip
        rule("""(?:tip )?$num ?% tip on $num|tip $num ?% (?:on|for) $num""") { q, g ->
            val p = n(g[1].ifEmpty { g[3] }); val b = n(g[2].ifEmpty { g[4] })
            val tip = b * p / 100; val v = b + tip
            SmartResult(q, tip, NumberFormatter.money(tip), "Tip at ${f(p)}%",
                listOf("Tip = ${f(b)} × ${f(p)}% = ${NumberFormatter.money(tip)}",
                    "Bill with tip = ${NumberFormatter.money(v)}"))
        },
        // Discount
        rule("""(?:discount )?$num ?% (?:discount|off) on $num|discount $num ?% on $num""") { q, g ->
            val p = n(g[1].ifEmpty { g[3] }); val b = n(g[2].ifEmpty { g[4] })
            val off = b * p / 100; val v = b - off
            SmartResult(q, v, NumberFormatter.money(v), "${f(p)}% discount",
                listOf("You save ${f(b)} × ${f(p)}% = ${NumberFormatter.money(off)}",
                    "Final price = ${f(b)} − ${NumberFormatter.money(off)} = ${NumberFormatter.money(v)}"))
        },
        // EMI
        rule("""emi (?:for |of )?$num (?:at |@ ?)$num ?%?(?: interest)? (?:for |over )?$num ?(years?|yrs?|months?)""") { q, g ->
            val p = n(g[1]); val annual = n(g[2]); val t = n(g[3])
            val months = if (g[4].startsWith("m")) t else t * 12
            val r = annual / 12 / 100
            val emi = if (r == 0.0) p / months else p * r * (1 + r).pow(months) / ((1 + r).pow(months) - 1)
            val total = emi * months
            SmartResult(q, emi, NumberFormatter.money(emi), "Monthly loan EMI",
                listOf("EMI = P × r × (1+r)ⁿ ÷ ((1+r)ⁿ − 1)",
                    "P = ${f(p)}, r = ${f(annual)}% ÷ 12 = ${f(r)}, n = ${f(months)} months",
                    "EMI = ${NumberFormatter.money(emi)} per month",
                    "Total payment = ${NumberFormatter.money(total)}",
                    "Total interest = ${NumberFormatter.money(total - p)}"))
        },
        // Simple interest
        rule("""(?:simple interest|si) (?:on |for |of )?$num (?:at |@ ?)$num ?%? (?:for |over )?$num ?(years?|yrs?)""") { q, g ->
            val p = n(g[1]); val r = n(g[2]); val t = n(g[3])
            val si = p * r * t / 100
            SmartResult(q, si, NumberFormatter.money(si), "Simple interest",
                listOf("SI = P × R × T ÷ 100", "= ${f(p)} × ${f(r)} × ${f(t)} ÷ 100", "= ${NumberFormatter.money(si)}",
                    "Amount = ${NumberFormatter.money(p + si)}"))
        },
        // Compound interest (annual)
        rule("""(?:compound interest|ci) (?:on |for |of )?$num (?:at |@ ?)$num ?%? (?:for |over )?$num ?(years?|yrs?)""") { q, g ->
            val p = n(g[1]); val r = n(g[2]); val t = n(g[3])
            val amount = p * (1 + r / 100).pow(t)
            val ci = amount - p
            SmartResult(q, ci, NumberFormatter.money(ci), "Compound interest (yearly)",
                listOf("A = P × (1 + R/100)ᵀ", "= ${f(p)} × (1 + ${f(r)}/100)^${f(t)}",
                    "A = ${NumberFormatter.money(amount)}", "CI = A − P = ${NumberFormatter.money(ci)}"))
        },
        // BMI
        rule("""bmi (?:for )?$num ?kg,? (?:and )?$num ?cm""") { q, g ->
            val kg = n(g[1]); val cm = n(g[2])
            val m = cm / 100
            val bmi = kg / (m * m)
            val cat = when {
                bmi < 18.5 -> "Underweight"
                bmi < 25 -> "Normal"
                bmi < 30 -> "Overweight"
                else -> "Obese"
            }
            SmartResult(q, bmi, "${NumberFormatter.format((bmi * 10).toLong() / 10.0)} ($cat)", "Body Mass Index",
                listOf("BMI = weight ÷ height²", "= ${f(kg)} ÷ (${f(m)})²", "= ${f(bmi)} → $cat"))
        },
        // Average / sum / min / max of a list
        rule("""(average|avg|mean|sum|total|max|maximum|min|minimum) of ((?:$num[ ,]*(?:and )?)+)""") { q, g ->
            val values = Regex(num).findAll(g[2]).map { n(it.value) }.toList()
            if (values.isEmpty()) throw CalcException("No numbers found")
            val (label, v) = when (g[1]) {
                "average", "avg", "mean" -> "Average" to values.average()
                "sum", "total" -> "Sum" to values.sum()
                "max", "maximum" -> "Maximum" to values.max()
                else -> "Minimum" to values.min()
            }
            val list = values.joinToString(", ") { f(it) }
            val steps = if (label == "Average")
                listOf("Sum = ${f(values.sum())}", "Count = ${values.size}", "Average = ${f(values.sum())} ÷ ${values.size} = ${f(v)}")
            else listOf("$label of [$list] = ${f(v)}")
            SmartResult(q, v, f(v), "$label of ${values.size} numbers", steps)
        },
        // Unit conversion: "10 km to miles"
        rule("""$num ?([a-z°/'"0-9 ]+?) (?:to|in|into|as) ([a-z°/'"0-9 ]+)""") { q, g ->
            val v0 = n(g[1])
            val from = UnitConverter.find(g[2]) ?: throw CalcException("Unknown unit '${g[2]}'")
            val to = UnitConverter.find(g[3]) ?: throw CalcException("Unknown unit '${g[3]}'")
            val v = UnitConverter.convert(v0, from, to)
            SmartResult(q, v, "${f(v)} ${to.symbol}", "${from.name} → ${to.name}",
                listOf("${f(v0)} ${from.symbol} = ${f(v)} ${to.symbol}",
                    if (from.category == UnitCategory.TEMPERATURE) "Converted via Celsius"
                    else "1 ${from.symbol} = ${f(from.factor / to.factor)} ${to.symbol}"))
        },
    )

    private val wordOps = listOf(
        "multiplied by" to "*", "divided by" to "/", "to the power of" to "^", "raised to" to "^",
        "square root of" to "sqrt ", "cube root of" to "cbrt ", "sqrt of" to "sqrt ",
        "squared" to "^2", "cubed" to "^3", "factorial of" to "fact ",
        "plus" to "+", "add" to "+", "minus" to "-", "subtract" to "-", "less" to "-",
        "times" to "*", "into" to "*", "x" to "*", "over" to "/", "by" to "/", "mod" to "%",
        "sine of" to "sin ", "cosine of" to "cos ", "tangent of" to "tan ",
        "log of" to "log ", "natural log of" to "ln ", "and" to "",
        "degrees" to "", "degree" to "",
    )

    private val numberWords = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
        "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
        "hundred" to 100, "thousand" to 1000,
    )

    /** "twenty five" → 25, "three hundred and two" → 302, "two thousand five hundred" → 2500. */
    private fun replaceNumberWords(s: String): String {
        val words = s.split(" ").filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var total = 0L
        var current = 0L
        var inNumber = false
        fun flush() {
            if (inNumber) out += (total + current).toString()
            total = 0; current = 0; inNumber = false
        }
        for (w in words) {
            val v = numberWords[w]
            when {
                v == null -> { flush(); out += w }
                v == 100 -> { current = (if (current == 0L) 1 else current) * 100; inNumber = true }
                v == 1000 -> { total += (if (current == 0L) 1 else current) * 1000; current = 0; inNumber = true }
                else -> { current += v; inNumber = true }
            }
        }
        flush()
        return out.joinToString(" ")
    }

    /** Converts phrases like "five plus 3 times two" into "5+3*2". */
    fun wordsToExpression(q: String): String {
        var s = " ${q.lowercase()} "
        // Longest phrases first so "multiplied by" wins over "by".
        for ((w, op) in wordOps.sortedByDescending { it.first.length }) {
            s = s.replace(Regex("""(?<=[\s\d)])${Regex.escape(w)}(?=[\s\d(])"""), " $op ")
        }
        s = replaceNumberWords(s)
        // "fact 5" → "5!"
        s = Regex("""fact\s*(\d+(?:\.\d+)?)""").replace(s) { m -> "${m.groupValues[1]}!" }
        return s.replace(Regex("""\s+"""), " ").trim()
    }
}
