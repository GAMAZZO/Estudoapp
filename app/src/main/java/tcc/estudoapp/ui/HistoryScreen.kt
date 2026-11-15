package tcc.estudoapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import tcc.estudoapp.R
import tcc.estudoapp.data.GoalPeriod
import tcc.estudoapp.data.StudyGoalDao
import tcc.estudoapp.data.StudySessionDao
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun HistoryScreen(
    sessionDao: StudySessionDao,
    goalDao: StudyGoalDao,
    onBack: () -> Unit
) {
    val zone = TimeZone.getDefault()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val today by produceState(Calendar.getInstance(zone), lifecycle, zone) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Calendar.getInstance(zone)
                delay(30_000)
            }
        }
    }
    val currentMonth = today.get(Calendar.YEAR) * 12 + today.get(Calendar.MONTH)
    val currentDay = today.get(Calendar.DAY_OF_MONTH)
    var month by rememberSaveable {
        mutableIntStateOf(currentMonth)
    }
    var selectedDay by rememberSaveable { mutableIntStateOf(currentDay) }
    val monthStart = remember(month, zone) {
        Calendar.getInstance(zone).apply {
            clear()
            set(month / 12, month % 12, 1)
        }
    }
    val start = monthStart.timeInMillis
    val end = remember(monthStart) {
        (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
    }
    val sessionFlow = remember(sessionDao, start, end) { sessionDao.observeSessions(start, end) }
    val sessions = key(sessionFlow) {
        sessionFlow.collectAsStateWithLifecycle(initialValue = null).value
    }
    val sessionsByDay = remember(sessions, zone) {
        val date = Calendar.getInstance(zone)
        sessions.orEmpty().groupBy {
            date.timeInMillis = it.startedAtEpochMillis
            date.get(Calendar.DAY_OF_MONTH)
        }
    }
    val dailyTotals = remember(sessionsByDay) {
        sessionsByDay.mapValues { (_, entries) -> entries.sumOf { it.durationMillis } }
    }
    val goalFlow = remember(goalDao) { goalDao.observeGoals() }
    val goals by goalFlow.collectAsStateWithLifecycle(initialValue = null)
    val targetMillis = goals?.firstOrNull { it.period == GoalPeriod.DAILY }
        ?.let { it.targetMinutes * 60_000 }
    val elapsedDays = when {
        month < currentMonth -> monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
        month == currentMonth -> currentDay
        else -> 0
    }
    val dayResults = remember(dailyTotals, sessions != null, targetMillis, month, currentMonth, elapsedDays) {
        if (sessions == null || targetMillis == null) {
            emptyMap()
        } else {
            (1..elapsedDays).associateWith { day ->
                when {
                    (dailyTotals[day] ?: 0) >= targetMillis -> GoalResult.MET
                    month == currentMonth && day == elapsedDays -> GoalResult.PENDING
                    else -> GoalResult.MISSED
                }
            }
        }
    }
    val selectedSessions = sessionsByDay[selectedDay].orEmpty()
    val locale = Locale.getDefault()
    val fullDate = remember(locale, zone) {
        DateFormat.getDateInstance(DateFormat.FULL, locale).apply { timeZone = zone }
    }
    val monthLabel = remember(monthStart, locale) {
        SimpleDateFormat("LLLL yyyy", locale).apply { timeZone = zone }.format(monthStart.time)
            .replaceFirstChar { it.titlecase(locale) }
    }
    val clockTime = remember(locale, zone) {
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = zone }
    }

    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.stopwatch_back))
                }
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            item {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { month--; selectedDay = 1 }) {
                        Text(stringResource(R.string.history_previous_month))
                    }
                    TextButton(onClick = { month++; selectedDay = 1 }) {
                        Text(stringResource(R.string.history_next_month))
                    }
                }
                MonthCalendar(
                    monthStart = monthStart,
                    selectedDay = selectedDay,
                    dailyTotals = dailyTotals,
                    dayResults = dayResults,
                    fullDate = fullDate,
                    locale = locale,
                    onSelectDay = { selectedDay = it }
                )
                if (targetMillis != null) {
                    Text(
                        text = stringResource(
                            R.string.history_goal_current,
                            formatStudyDuration(targetMillis)
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GoalLegend(GoalResult.MET, Modifier.weight(1f))
                        GoalLegend(GoalResult.MISSED, Modifier.weight(1f))
                    }
                }
            }
            item {
                MonthAverage(
                    elapsedDays = elapsedDays,
                    totalMillis = dailyTotals.entries.sumOf { (day, total) ->
                        if (day <= elapsedDays) total else 0L
                    },
                    targetMillis = targetMillis,
                    loading = sessions == null || goals == null
                )
            }
            item {
                Text(
                    text = fullDate.format(dateInMonth(monthStart, selectedDay)),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                if (sessions == null) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(
                        R.string.history_day_total,
                        formatStudyDuration(dailyTotals[selectedDay] ?: 0)
                    ))
                    if (selectedSessions.isEmpty()) {
                        Text(stringResource(R.string.history_empty_day))
                    }
                    dayResults[selectedDay]?.let { Text(stringResource(it.label)) }
                }
            }
            items(selectedSessions, key = { it.id }) { session ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.history_session_start, clockTime.format(Date(session.startedAtEpochMillis))))
                        Text(stringResource(R.string.history_session_duration, formatStudyDuration(session.durationMillis)))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    monthStart: Calendar,
    selectedDay: Int,
    dailyTotals: Map<Int, Long>,
    dayResults: Map<Int, GoalResult>,
    fullDate: DateFormat,
    locale: Locale,
    onSelectDay: (Int) -> Unit
) {
    val dayCount = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
    val offset = monthStart.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val weekdayNames = remember(locale) { DateFormatSymbols.getInstance(locale).shortWeekdays }

    Column(Modifier.selectableGroup()) {
        Row(Modifier.fillMaxWidth()) {
            for (weekday in Calendar.SUNDAY..Calendar.SATURDAY) {
                Text(
                    text = weekdayNames[weekday].trimEnd('.'),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
        for (weekStart in 0 until offset + dayCount step 7) {
            Row(Modifier.fillMaxWidth()) {
                for (weekday in 0..6) {
                    val day = weekStart + weekday - offset + 1
                    if (day in 1..dayCount) {
                        val total = dailyTotals[day]
                        val result = dayResults[day]
                        val dayDescription = stringResource(
                            R.string.history_calendar_day,
                            fullDate.format(dateInMonth(monthStart, day)),
                            formatStudyDuration(total ?: 0)
                        )
                        val description = if (result == null) dayDescription else stringResource(
                            R.string.history_day_with_goal_status,
                            dayDescription,
                            stringResource(result.label)
                        )
                        val colors = result?.colors()
                        val selected = day == selectedDay
                        Surface(
                            modifier = Modifier.weight(1f).padding(1.dp),
                            shape = MaterialTheme.shapes.small,
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            color = colors?.first ?: when {
                                selected -> MaterialTheme.colorScheme.primaryContainer
                                total != null -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            contentColor = colors?.second ?: MaterialTheme.colorScheme.onSurface
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .selectable(selected, role = Role.RadioButton, onClick = { onSelectDay(day) })
                                    .semantics { contentDescription = description },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(day.toString(), style = MaterialTheme.typography.labelLarge)
                                if (total != null) {
                                    Text(shortDuration(total), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                }
                                result?.mark?.let {
                                    Text(stringResource(it), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private enum class GoalResult(val label: Int, val mark: Int? = null) {
    MET(R.string.history_goal_met, R.string.history_goal_met_mark),
    MISSED(R.string.history_goal_missed, R.string.history_goal_missed_mark),
    PENDING(R.string.history_goal_pending)
}

@Composable
private fun GoalResult.colors(): Pair<Color, Color>? = when (this) {
    GoalResult.MET -> if (isSystemInDarkTheme()) {
        Color(0xFF234A2B) to Color(0xFFC3F0C3)
    } else {
        Color(0xFFCDEDCB) to Color(0xFF183E1E)
    }
    GoalResult.MISSED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    GoalResult.PENDING -> null
}

@Composable
private fun GoalLegend(result: GoalResult, modifier: Modifier) {
    Text(
        text = stringResource(R.string.history_goal_legend, stringResource(requireNotNull(result.mark)), stringResource(result.label)),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun MonthAverage(elapsedDays: Int, totalMillis: Long, targetMillis: Long?, loading: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.history_month_average),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            if (elapsedDays == 0) {
                Text(stringResource(R.string.history_future_month))
            } else if (loading) {
                CircularProgressIndicator()
            } else {
                val average = totalMillis / elapsedDays
                Text(formatStudyDuration(average), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.history_average_days, elapsedDays))
                if (targetMillis == null) {
                    Text(stringResource(R.string.history_goal_required))
                } else {
                    val reached = average >= targetMillis
                    val result = if (reached) GoalResult.MET else GoalResult.MISSED
                    val label = if (reached) R.string.history_average_met else R.string.history_average_below
                    val colors = requireNotNull(result.colors())
                    Surface(color = colors.first, contentColor = colors.second, shape = MaterialTheme.shapes.small) {
                        Text(stringResource(label), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun shortDuration(millis: Long): String = when {
    millis >= 3_600_000 -> stringResource(R.string.history_short_hours, millis / 3_600_000)
    millis >= 60_000 -> stringResource(R.string.history_short_minutes, millis / 60_000)
    else -> stringResource(R.string.history_short_seconds, millis / 1_000)
}

private fun dateInMonth(monthStart: Calendar, day: Int): Date =
    (monthStart.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.time
