package com.smartcalc.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartcalc.core.AiSolver
import com.smartcalc.core.AngleMode
import com.smartcalc.core.ExpressionEvaluator
import com.smartcalc.core.NumberFormatter
import com.smartcalc.core.SmartEngine
import com.smartcalc.core.SmartResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(val input: String, val result: String, val source: String, val time: Long)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class CalcViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("smartcalc", Context.MODE_PRIVATE)
    private val evaluator = ExpressionEvaluator()
    private val smartEngine = SmartEngine(evaluator)

    // ---------- Settings ----------
    var angleMode by mutableStateOf(
        if (prefs.getString("angle", "DEG") == "RAD") AngleMode.RAD else AngleMode.DEG
    )
        private set
    var themeMode by mutableStateOf(ThemeMode.valueOf(prefs.getString("theme", "SYSTEM") ?: "SYSTEM"))
        private set
    var apiKey by mutableStateOf(prefs.getString("api_key", "") ?: "")
        private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true))
        private set

    init { evaluator.angleMode = angleMode }

    fun toggleAngle() {
        angleMode = if (angleMode == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
        evaluator.angleMode = angleMode
        prefs.edit().putString("angle", angleMode.name).apply()
        updatePreview()
    }

    fun updateTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun updateApiKey(key: String) {
        apiKey = key.trim()
        prefs.edit().putString("api_key", apiKey).apply()
    }

    fun updateHaptics(on: Boolean) {
        haptics = on
        prefs.edit().putBoolean("haptics", on).apply()
    }

    // ---------- Calculator ----------
    var expression by mutableStateOf("")
        private set
    var preview by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var justEvaluated by mutableStateOf(false)
        private set
    var scientific by mutableStateOf(false)

    fun input(token: String) {
        error = null
        if (justEvaluated) {
            // After "=", an operator continues from the result; a digit starts fresh.
            val continues = token.firstOrNull()?.let { it in "+-×÷^%!" } == true
            expression = if (continues) expression else ""
            justEvaluated = false
        }
        expression += token
        updatePreview()
    }

    fun backspace() {
        error = null
        justEvaluated = false
        expression = when {
            expression.endsWith("ans") -> expression.dropLast(3)
            FUNCTION_SUFFIX.containsMatchIn(expression) -> expression.replace(FUNCTION_SUFFIX, "")
            else -> expression.dropLast(1)
        }
        updatePreview()
    }

    fun clear() {
        expression = ""
        preview = ""
        error = null
        justEvaluated = false
    }

    fun setExpressionText(text: String) {
        expression = text
        justEvaluated = false
        error = null
        updatePreview()
    }

    private fun updatePreview() {
        val v = evaluator.tryEvaluate(expression)
        val looksComplete = expression.any { it in "+-×÷^%!(" || it.isLetter() }
        preview = if (v != null && looksComplete) NumberFormatter.format(v, grouping = true) else ""
    }

    fun equals() {
        if (expression.isBlank()) return
        try {
            val v = evaluator.evaluate(expression)
            val shown = NumberFormatter.format(v)
            addHistory(expression, NumberFormatter.format(v, grouping = true), "calc")
            evaluator.ans = v
            expression = shown
            preview = ""
            justEvaluated = true
        } catch (e: Exception) {
            error = e.message ?: "Error"
        }
    }

    // ---------- Smart (offline natural language) ----------
    var smartQuery by mutableStateOf("")
    var smartResult by mutableStateOf<SmartResult?>(null)
        private set
    var smartError by mutableStateOf<String?>(null)
        private set

    fun solveSmart(query: String = smartQuery) {
        smartQuery = query
        if (query.isBlank()) return
        try {
            val r = smartEngine.solve(query)
            smartResult = r
            smartError = null
            evaluator.ans = r.value
            addHistory(query, r.display, "smart")
        } catch (e: Exception) {
            smartResult = null
            smartError = (e.message ?: "Could not understand that") +
                if (apiKey.isNotBlank()) "\nTip: try the AI tab for word problems." else ""
        }
    }

    // ---------- AI (Claude) ----------
    var aiQuery by mutableStateOf("")
    var aiAnswer by mutableStateOf<String?>(null)
        private set
    var aiLoading by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set

    fun askAi(query: String = aiQuery) {
        aiQuery = query
        if (query.isBlank() || aiLoading) return
        if (apiKey.isBlank()) {
            aiError = "Add your Anthropic API key in Settings (⚙) to use AI Solve."
            return
        }
        aiLoading = true
        aiError = null
        aiAnswer = null
        val key = apiKey
        viewModelScope.launch {
            try {
                val answer = withContext(Dispatchers.IO) {
                    val solver = AiSolver(key)
                    try { solver.solve(query) } finally { solver.close() }
                }
                aiAnswer = answer
                val finalLine = answer.lines().lastOrNull { it.trim().startsWith("Answer", true) }
                addHistory(query, finalLine?.substringAfter(":")?.trim() ?: "AI answer", "ai")
            } catch (e: Exception) {
                aiError = e.message ?: "Something went wrong"
            } finally {
                aiLoading = false
            }
        }
    }

    // ---------- History ----------
    val history = mutableStateListOf<HistoryItem>().apply { addAll(loadHistory()) }

    private fun addHistory(input: String, result: String, source: String) {
        history.add(0, HistoryItem(input, result, source, System.currentTimeMillis()))
        while (history.size > 200) history.removeAt(history.lastIndex)
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    fun deleteHistory(item: HistoryItem) {
        history.remove(item)
        saveHistory()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.forEach {
            arr.put(JSONObject().put("i", it.input).put("r", it.result).put("s", it.source).put("t", it.time))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<HistoryItem> = try {
        val arr = JSONArray(prefs.getString("history", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryItem(o.getString("i"), o.getString("r"), o.optString("s", "calc"), o.optLong("t"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private val FUNCTION_SUFFIX = Regex("""(asin|acos|atan|sin|cos|tan|log|ln|sqrt|cbrt|abs)\($""")
    }
}
