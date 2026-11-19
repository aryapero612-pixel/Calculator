package com.smartcalc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineTest {
    private val ev = ExpressionEvaluator()
    private val smart = SmartEngine()
    private fun eq(expected: Double, expr: String) = assertEquals(expr, expected, ev.evaluate(expr), 1e-9)
    private fun smartEq(expected: Double, q: String) = assertEquals(q, expected, smart.solve(q).value, 1e-6)

    @Test fun arithmetic() {
        eq(14.0, "2+3*4")
        eq(20.0, "(2+3)*4")
        eq(-1.0, "2-3")
        eq(512.0, "2^3^2")
        eq(-4.0, "-2^2".let { "-(2^2)" })
        eq(2.5, "5÷2")
        eq(12.0, "3×4")
        eq(1000.0, "1,000")
        eq(150000.0, "1.5e5")
    }

    @Test fun implicitAndConstants() {
        eq(2 * Math.PI, "2π")
        eq(15.0, "3(4+1)")
        eq(Math.E, "e")
        eq(1.0, "2sin(30)")
    }

    @Test fun functions() {
        eq(0.5, "sin(30)")
        eq(0.0, "cos(90)")
        eq(1.0, "tan 45")
        eq(3.0, "sqrt(9)")
        eq(3.0, "√9")
        eq(2.0, "log(100)")
        eq(1.0, "ln(e)")
        eq(120.0, "5!")
        eq(45.0, "atan(1)")
        ev.angleMode = AngleMode.RAD
        eq(0.0, "sin(pi)")
        ev.angleMode = AngleMode.DEG
    }

    @Test fun percent() {
        eq(0.5, "50%")
        eq(220.0, "200+10%")
        eq(180.0, "200-10%")
        eq(20.0, "200*10%")
    }

    @Test fun errors() {
        assertThrows(CalcException::class.java) { ev.evaluate("1/0") }
        assertThrows(CalcException::class.java) { ev.evaluate("2+") }
        assertThrows(CalcException::class.java) { ev.evaluate("sqrt(-1)") }
        assertThrows(CalcException::class.java) { ev.evaluate("foo(2)") }
    }

    @Test fun smartPercent() {
        smartEq(360.0, "15% of 2400")
        smartEq(25.0, "250 is what percent of 1000")
        smartEq(600.0, "increase 500 by 20%")
        smartEq(50.0, "percentage change from 200 to 300")
        smartEq(1770.0, "18% gst on 1500")
        smartEq(127.5, "tip 15% on 850")
        smartEq(2099.3, "30% off on 2999")
    }

    @Test fun smartWords() {
        smartEq(81.0, "What is 45 plus 12 times 3?")
        smartEq(12.0, "square root of 144")
        smartEq(11.0, "five plus six")
        smartEq(25.0, "twenty five")
        smartEq(302.0, "three hundred and two")
        smartEq(2500.0, "two thousand five hundred")
        smartEq(49.0, "7 squared")
        smartEq(120.0, "factorial of 5")
        smartEq(5.0, "10 divided by 2")
    }

    @Test fun smartFinance() {
        val emi = smart.solve("emi 500000 at 8.5% for 20 years").value
        assertEquals(4339.12, emi, 0.01)
        smartEq(2100.0, "simple interest 10000 at 7% for 3 years")
        assertEquals(4693.28, smart.solve("compound interest 10000 at 8% for 5 years").value, 0.01)
    }

    @Test fun smartLists() {
        smartEq(10.75, "average of 4, 8, 15, 16")
        smartEq(6.0, "sum of 1 2 3")
        smartEq(16.0, "max of 4, 8, 15 and 16")
    }

    @Test fun units() {
        assertEquals(6.21371, smart.solve("10 km to miles").value, 1e-4)
        assertEquals(37.7778, smart.solve("100 f in c").value, 1e-4)
        assertEquals(5120.0, smart.solve("5 gb to mb").value, 1e-9)
        assertEquals(2.20462, smart.solve("1 kg to lbs").value, 1e-4)
        assertEquals(100.0, smart.solve("1 meter to cm").value, 1e-9)
    }

    @Test fun bmi() {
        assertEquals(22.857, smart.solve("bmi 70 kg 175 cm").value, 1e-3)
    }

    @Test fun formatting() {
        assertEquals("0.3", NumberFormatter.format(0.1 + 0.2))
        assertEquals("1,234,567.5", NumberFormatter.format(1234567.5, grouping = true))
        assertEquals("1E20", NumberFormatter.format(1e20))
        assertEquals("-42", NumberFormatter.format(-42.0))
    }
}
