package com.smartcalc.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcalc.app.CalcViewModel

private val aiExamples = listOf(
    "A train covers 360 km in 4 hours. How long will it take to cover 540 km at the same speed?",
    "Ek dukaan wala 20% profit par saman bechta hai. Cost price 850 ho to selling price kya hoga?",
    "Solve 2x² − 7x + 3 = 0",
    "If I save ₹5000 every month at 7% yearly return, how much will I have in 10 years?",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen(vm: CalcViewModel, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val listen = rememberVoiceInput { vm.aiQuery = it; vm.askAi(it) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AI Solve — word problems, equations, step-by-step", style = MaterialTheme.typography.titleMedium)
        if (vm.apiKey.isBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "AI Solve uses Claude. Add your Anthropic API key in Settings (⚙ top-right). " +
                        "The Calc and Smart tabs work fully offline without a key.",
                    Modifier.padding(16.dp),
                )
            }
        }
        OutlinedTextField(
            value = vm.aiQuery,
            onValueChange = { vm.aiQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type any maths question (English / Hindi / Hinglish)") },
            minLines = 3,
            trailingIcon = { IconButton(onClick = listen) { Text("🎤", fontSize = 20.sp) } },
        )
        Button(
            onClick = { keyboard?.hide(); vm.askAi() },
            enabled = !vm.aiLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.aiLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Thinking…")
            } else Text("Solve with AI")
        }

        vm.aiError?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        vm.aiAnswer?.let { answer ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SelectionContainer { Text(answer, lineHeight = 22.sp) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Q: ${vm.aiQuery}\n\n$answer\n\n— SmartCalc AI")
                            }
                            context.startActivity(Intent.createChooser(send, "Share solution"))
                        }) { Text("Share") }
                    }
                }
            }
        }

        Text("Examples", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            aiExamples.forEach { ex ->
                SuggestionChip(onClick = { vm.aiQuery = ex }, label = { Text(ex, maxLines = 2) })
            }
        }
    }
}
