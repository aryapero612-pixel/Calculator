package com.smartcalc.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcalc.app.CalcViewModel

private val smartExamples = listOf(
    "15% of 2400",
    "18% gst on 1500",
    "emi 500000 at 8.5% for 20 years",
    "10 km to miles",
    "250 is what percent of 1000",
    "square root of 144 plus 5",
    "average of 4, 8, 15, 16",
    "compound interest 10000 at 8% for 5 years",
    "100 f in c",
    "bmi 70 kg 175 cm",
    "30% off on 2999",
    "tip 15% on 850",
)

/** Launcher for Android's built-in speech recogniser. */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your calculation…")
        }
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Voice input is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartScreen(vm: CalcViewModel, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val listen = rememberVoiceInput { vm.solveSmart(it) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ask in plain words — works offline", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vm.smartQuery,
            onValueChange = { vm.smartQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. 18% gst on 1500") },
            singleLine = true,
            trailingIcon = { IconButton(onClick = listen) { Text("🎤", fontSize = 20.sp) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); vm.solveSmart() }),
        )
        Button(onClick = { keyboard?.hide(); vm.solveSmart() }, modifier = Modifier.fillMaxWidth()) {
            Text("Calculate")
        }

        vm.smartError?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        vm.smartResult?.let { r ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.interpretation, style = MaterialTheme.typography.labelLarge)
                    Text(r.display, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("Steps", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    r.steps.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${i + 1}. ", fontWeight = FontWeight.SemiBold)
                            Text(s)
                        }
                    }
                }
            }
        }

        Text("Try these", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            smartExamples.forEach { ex ->
                SuggestionChip(onClick = { vm.solveSmart(ex) }, label = { Text(ex) })
            }
        }
    }
}
