package tcc.estudoapp.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tcc.estudoapp.R
import tcc.estudoapp.data.StudySessionDao
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun HistoryScreen(sessionDao: StudySessionDao, onBack: () -> Unit) {
    val zone = TimeZone.getDefault()
    val today = remember(zone) { Calendar.getInstance(zone) }
    var month by rememberSaveable {
        mutableIntStateOf(today.get(Calendar.YEAR) * 12 + today.get(Calendar.MONTH))
    }
    var selectedDay by rememberSaveable { mutableIntStateOf(today.get(Calendar.DAY_OF_MONTH)) }
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
                    fullDate = fullDate,
                    locale = locale,
                    onSelectDay = { selectedDay = it }
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
                        val description = stringResource(
                            R.string.history_calendar_day,
                            fullDate.format(dateInMonth(monthStart, day)),
                            formatStudyDuration(total ?: 0)
                        )
                        Surface(
                            modifier = Modifier.weight(1f).padding(1.dp),
                            shape = MaterialTheme.shapes.small,
                            color = when {
                                day == selectedDay -> MaterialTheme.colorScheme.primaryContainer
                                total != null -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .selectable(day == selectedDay, role = Role.RadioButton, onClick = { onSelectDay(day) })
                                    .semantics { contentDescription = description },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(day.toString(), style = MaterialTheme.typography.labelLarge)
                                if (total != null) {
                                    Text(shortDuration(total), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
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

@Composable
private fun shortDuration(millis: Long): String = when {
    millis >= 3_600_000 -> stringResource(R.string.history_short_hours, millis / 3_600_000)
    millis >= 60_000 -> stringResource(R.string.history_short_minutes, millis / 60_000)
    else -> stringResource(R.string.history_short_seconds, millis / 1_000)
}

private fun dateInMonth(monthStart: Calendar, day: Int): Date =
    (monthStart.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.time
