package com.smartcalc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartcalc.app.ui.AiScreen
import com.smartcalc.app.ui.CalculatorScreen
import com.smartcalc.app.ui.HistoryScreen
import com.smartcalc.app.ui.SettingsDialog
import com.smartcalc.app.ui.SmartCalcTheme
import com.smartcalc.app.ui.SmartScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: CalcViewModel = viewModel()
            val dark = when (vm.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            SmartCalcTheme(dark = dark) { App(vm) }
        }
    }
}

private data class Tab(val label: String, val emoji: String)

private val tabs = listOf(
    Tab("Calc", "🧮"),
    Tab("Smart", "✨"),
    Tab("AI Solve", "🤖"),
    Tab("History", "🕘"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: CalcViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SmartCalc AI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Text(tab.emoji, fontSize = 20.sp) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (selected) {
            0 -> CalculatorScreen(vm, m)
            1 -> SmartScreen(vm, m)
            2 -> AiScreen(vm, m)
            else -> HistoryScreen(vm, m, onReuse = { item ->
                when (item.source) {
                    "smart" -> { vm.smartQuery = item.input; selected = 1 }
                    "ai" -> { vm.aiQuery = item.input; selected = 2 }
                    else -> { vm.setExpressionText(item.input); selected = 0 }
                }
            })
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
}
