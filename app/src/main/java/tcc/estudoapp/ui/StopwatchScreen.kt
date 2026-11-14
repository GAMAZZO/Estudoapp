package tcc.estudoapp.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import tcc.estudoapp.R
import java.util.Locale

@Composable
internal fun StopwatchScreen(
    timer: StudyTimer,
    saving: Boolean,
    saved: Boolean,
    saveFailed: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(SystemClock.elapsedRealtime(), timer, lifecycle) {
        value = SystemClock.elapsedRealtime()
        if (timer.isRunning) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = SystemClock.elapsedRealtime()
                    delay(200)
                }
            }
        }
    }
    val seconds = timer.elapsedAt(now) / 1_000
    val elapsed = String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    val elapsedDescription = stringResource(R.string.stopwatch_elapsed_description, elapsed)
    val active = timer.id.isNotEmpty()

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(onClick = onBack, enabled = !saving, modifier = Modifier.align(Alignment.Start)) {
                Text(stringResource(R.string.stopwatch_back))
            }
            Text(
                text = stringResource(R.string.stopwatch_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = elapsed,
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clearAndSetSemantics { contentDescription = elapsedDescription }
            )
            if (active) {
                Text(stringResource(if (timer.isRunning) R.string.stopwatch_running else R.string.stopwatch_paused))
            }
            val actionLabel = when {
                timer.isRunning -> R.string.stopwatch_pause
                active -> R.string.stopwatch_resume
                else -> R.string.stopwatch_start
            }
            Button(
                onClick = if (timer.isRunning) onPause else onStart,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(actionLabel))
            }
            if (active) {
                OutlinedButton(onClick = onFinish, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stopwatch_finish))
                }
            }
            if (saved) Text(stringResource(R.string.stopwatch_saved))
            if (saveFailed) {
                Text(stringResource(R.string.stopwatch_save_error), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
