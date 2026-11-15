package tcc.estudoapp.ui

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tcc.estudoapp.R
import tcc.estudoapp.data.GoalPeriod
import tcc.estudoapp.data.StudyGoal
import tcc.estudoapp.data.StudyGoalDao
import tcc.estudoapp.data.StudySessionDao
import java.util.Calendar

@Composable
internal fun GoalsScreen(
    goalDao: StudyGoalDao,
    sessionDao: StudySessionDao,
    onBack: () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ranges by produceState(currentGoalRanges(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = currentGoalRanges()
                delay(30_000)
            }
        }
    }
    val firstStart = ranges.minOf { it.start }
    val lastEnd = ranges.maxOf { it.endExclusive }
    val sessionFlow = remember(sessionDao, firstStart, lastEnd) {
        sessionDao.observeSessions(firstStart, lastEnd)
    }
    val sessions = key(sessionFlow) {
        sessionFlow.collectAsStateWithLifecycle(initialValue = null).value
    }
    val totals = remember(sessions, ranges) {
        sessions?.let { entries ->
            ranges.associate { range ->
                range.period to entries.sumOf { session ->
                    if (session.startedAtEpochMillis >= range.start &&
                        session.startedAtEpochMillis < range.endExclusive
                    ) session.durationMillis else 0L
                }
            }
        }
    }
    val goalFlow = remember(goalDao) { goalDao.observeGoals() }
    val goals by goalFlow.collectAsStateWithLifecycle(initialValue = null)
    val goalsByPeriod = remember(goals) { goals.orEmpty().associateBy { it.period } }
    var editingPeriod by rememberSaveable { mutableStateOf<GoalPeriod?>(null) }

    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text(stringResource(R.string.stopwatch_back)) }
                Text(
                    text = stringResource(R.string.goals_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            if (goals == null || totals == null) {
                item { CircularProgressIndicator() }
            } else {
                items(GoalPeriod.entries, key = { it.name }) { period ->
                    GoalCard(period, goalsByPeriod[period], totals.getValue(period)) {
                        editingPeriod = period
                    }
                }
            }
        }
    }

    val period = editingPeriod
    if (period != null && goals != null) {
        key(period) {
            GoalDialog(
                period = period,
                goal = goalsByPeriod[period],
                onSave = goalDao::save,
                onClose = { editingPeriod = null }
            )
        }
    }
}

@Composable
private fun GoalCard(period: GoalPeriod, goal: StudyGoal?, studiedMillis: Long, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(period.label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            if (period == GoalPeriod.WEEKLY) Text(stringResource(R.string.goal_week_description))
            Text(stringResource(R.string.goal_studied, formatStudyDuration(studiedMillis)))
            if (goal == null) {
                Text(stringResource(R.string.goal_undefined))
            } else {
                val targetMillis = goal.targetMinutes * 60_000
                Text(stringResource(R.string.goal_target, formatStudyDuration(targetMillis)))
                LinearProgressIndicator(
                    progress = { (studiedMillis.toDouble() / targetMillis).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                val remaining = targetMillis - studiedMillis
                Text(
                    if (remaining <= 0) stringResource(R.string.goal_reached)
                    else stringResource(R.string.goal_remaining, formatStudyDuration(remaining + 999))
                )
            }
            TextButton(onClick = onEdit) {
                Text(stringResource(if (goal == null) R.string.goal_set else R.string.goal_edit))
            }
        }
    }
}

@Composable
private fun GoalDialog(
    period: GoalPeriod,
    goal: StudyGoal?,
    onSave: suspend (StudyGoal) -> Unit,
    onClose: () -> Unit
) {
    var hours by rememberSaveable { mutableStateOf(goal?.let { (it.targetMinutes / 60).toString() }.orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(goal?.let { (it.targetMinutes % 60).toString() }.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hourValue = hours.ifEmpty { "0" }.toIntOrNull()
    val minuteValue = minutes.ifEmpty { "0" }.toIntOrNull()
    val target = if (hourValue != null && minuteValue != null && minuteValue in 0..59) {
        (hourValue.toLong() * 60 + minuteValue).takeIf { it > 0 }
    } else null

    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text(stringResource(period.label)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.goal_hours)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !saving
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.goal_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !saving,
                    isError = minutes.isNotEmpty() && (minuteValue == null || minuteValue !in 0..59)
                )
                Text(stringResource(R.string.goal_duration_hint))
                if (saveFailed) {
                    Text(stringResource(R.string.goal_save_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !saving) {
                Text(stringResource(R.string.task_cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null && !saving,
                onClick = {
                    if (target != null && !saving) {
                        saving = true
                        saveFailed = false
                        scope.launch {
                            try {
                                onSave(StudyGoal(period, target))
                                onClose()
                            } catch (_: SQLiteException) {
                                saveFailed = true
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            ) { Text(stringResource(R.string.task_save)) }
        }
    )
}

private val GoalPeriod.label: Int
    get() = when (this) {
        GoalPeriod.DAILY -> R.string.goal_daily
        GoalPeriod.WEEKLY -> R.string.goal_weekly
        GoalPeriod.MONTHLY -> R.string.goal_monthly
    }

private data class GoalRange(val period: GoalPeriod, val start: Long, val endExclusive: Long)

private fun currentGoalRanges(): List<GoalRange> {
    val today = Calendar.getInstance()
    val startOfWeek = today.clone() as Calendar
    val daysSinceMonday = (today.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    startOfWeek.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
    val startOfMonth = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    return listOf(
        goalRange(GoalPeriod.DAILY, today, Calendar.DAY_OF_MONTH, 1),
        goalRange(GoalPeriod.WEEKLY, startOfWeek, Calendar.DAY_OF_MONTH, 7),
        goalRange(GoalPeriod.MONTHLY, startOfMonth, Calendar.MONTH, 1)
    )
}

private fun goalRange(period: GoalPeriod, firstDay: Calendar, unit: Int, amount: Int): GoalRange {
    val nextPeriod = (firstDay.clone() as Calendar).apply { add(unit, amount) }
    return GoalRange(period, firstDay.startOfDayMillis(), nextPeriod.startOfDayMillis())
}

private fun Calendar.startOfDayMillis(): Long {
    val day = Calendar.getInstance(timeZone)
    day.clear()
    day.set(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH))
    return day.timeInMillis
}
