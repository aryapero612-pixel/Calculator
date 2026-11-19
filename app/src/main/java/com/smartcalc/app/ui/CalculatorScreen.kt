package com.smartcalc.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonDefaults
import com.smartcalc.app.CalcViewModel
import com.smartcalc.core.AngleMode

private enum class KeyKind { DIGIT, OP, FUNC, ACTION, EQUALS }

private data class Key(val label: String, val insert: String = label, val kind: KeyKind = KeyKind.DIGIT)

private val basicRows = listOf(
    listOf(Key("AC", kind = KeyKind.ACTION), Key("( )", kind = KeyKind.OP), Key("%", kind = KeyKind.OP), Key("÷", kind = KeyKind.OP)),
    listOf(Key("7"), Key("8"), Key("9"), Key("×", kind = KeyKind.OP)),
    listOf(Key("4"), Key("5"), Key("6"), Key("-", kind = KeyKind.OP)),
    listOf(Key("1"), Key("2"), Key("3"), Key("+", kind = KeyKind.OP)),
    listOf(Key("0"), Key("."), Key("⌫", kind = KeyKind.ACTION), Key("=", kind = KeyKind.EQUALS)),
)

private val sciRows = listOf(
    listOf(Key("sin", "sin(", KeyKind.FUNC), Key("cos", "cos(", KeyKind.FUNC), Key("tan", "tan(", KeyKind.FUNC), Key("π", kind = KeyKind.FUNC), Key("e", kind = KeyKind.FUNC)),
    listOf(Key("ln", "ln(", KeyKind.FUNC), Key("log", "log(", KeyKind.FUNC), Key("√", "sqrt(", KeyKind.FUNC), Key("xʸ", "^", KeyKind.FUNC), Key("x!", "!", KeyKind.FUNC)),
    listOf(Key("sin⁻¹", "asin(", KeyKind.FUNC), Key("cos⁻¹", "acos(", KeyKind.FUNC), Key("tan⁻¹", "atan(", KeyKind.FUNC), Key("x²", "^2", KeyKind.FUNC), Key("Ans", "ans", KeyKind.FUNC)),
)

@Composable
fun CalculatorScreen(vm: CalcViewModel, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current

    fun press(k: Key) {
        if (vm.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (k.label) {
            "AC" -> vm.clear()
            "⌫" -> vm.backspace()
            "=" -> vm.equals()
            "( )" -> {
                val open = vm.expression.count { it == '(' } - vm.expression.count { it == ')' }
                val last = vm.expression.lastOrNull()
                val close = open > 0 && last != null && (last.isLetterOrDigit() || last in ")!%π")
                vm.input(if (close) ")" else "(")
            }
            else -> vm.input(k.insert)
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // ----- Display -----
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            val scroll = rememberScrollState()
            LaunchedEffect(vm.expression) { scroll.scrollTo(scroll.maxValue) }
            Text(
                vm.expression.ifEmpty { "0" },
                fontSize = if (vm.expression.length > 14) 34.sp else 48.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.horizontalScroll(scroll),
            )
            Text(
                vm.error ?: vm.preview.let { if (it.isNotEmpty()) "= $it" else " " },
                fontSize = 22.sp,
                color = if (vm.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ----- Mode chips -----
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vm.scientific, onClick = { vm.scientific = !vm.scientific }, label = { Text("Scientific") })
            FilterChip(
                selected = vm.angleMode == AngleMode.RAD,
                onClick = { vm.toggleAngle() },
                label = { Text(if (vm.angleMode == AngleMode.DEG) "DEG" else "RAD") },
            )
        }

        // ----- Keypad -----
        if (vm.scientific) {
            sciRows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k -> CalcKey(k, height = 44, fontSize = 15) { press(k) } }
                }
            }
        }
        basicRows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> CalcKey(k, height = if (vm.scientific) 58 else 72, fontSize = 24) { press(k) } }
            }
        }
        Box(Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun RowScope.CalcKey(k: Key, height: Int, fontSize: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (k.kind) {
        KeyKind.DIGIT -> cs.surfaceVariant to cs.onSurfaceVariant
        KeyKind.OP -> cs.secondaryContainer to cs.onSecondaryContainer
        KeyKind.FUNC -> cs.surfaceContainerHigh to cs.onSurface
        KeyKind.ACTION -> cs.tertiaryContainer to cs.onTertiaryContainer
        KeyKind.EQUALS -> cs.primary to cs.onPrimary
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(height.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(k.label, fontSize = fontSize.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
