package tcc.estudoapp.ui

import android.database.sqlite.SQLiteException
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import tcc.estudoapp.data.StudyDatabase
import tcc.estudoapp.data.StudySession
import java.util.UUID

@Composable
fun StudyApp(database: StudyDatabase) {
    var screen by rememberSaveable { mutableStateOf(StudyScreen.TASKS) }
    var timer by rememberSaveable(stateSaver = StudyTimer.Saver) {
        mutableStateOf(StudyTimer())
    }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val screenState = rememberSaveableStateHolder()

    BackHandler(enabled = screen != StudyScreen.TASKS) {
        if (!saving) screen = StudyScreen.TASKS
    }
    if (screen == StudyScreen.STOPWATCH) {
        StopwatchScreen(
            timer = timer,
            saving = saving,
            saved = saved,
            saveFailed = saveFailed,
            onBack = { screen = StudyScreen.TASKS },
            onStart = {
                val now = SystemClock.elapsedRealtime()
                timer = if (timer.id.isEmpty()) {
                    StudyTimer(
                        id = UUID.randomUUID().toString(),
                        startedAtEpochMillis = System.currentTimeMillis(),
                        runningSinceMillis = now
                    )
                } else {
                    timer.copy(runningSinceMillis = now)
                }
                saved = false
                saveFailed = false
            },
            onPause = { timer = timer.pause(SystemClock.elapsedRealtime()) },
            onFinish = {
                if (!saving && timer.id.isNotEmpty()) {
                    val paused = timer.pause(SystemClock.elapsedRealtime())
                    val session = StudySession(
                        id = paused.id,
                        startedAtEpochMillis = paused.startedAtEpochMillis,
                        finishedAtEpochMillis = System.currentTimeMillis()
                            .coerceAtLeast(paused.startedAtEpochMillis),
                        durationMillis = paused.accumulatedMillis
                    )
                    timer = paused
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            database.studySessionDao().insert(session)
                            timer = StudyTimer()
                            saved = true
                        } catch (_: SQLiteException) {
                            saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        )
    } else if (screen == StudyScreen.HISTORY) {
        screenState.SaveableStateProvider("history") {
            HistoryScreen(database.studySessionDao(), onBack = { screen = StudyScreen.TASKS })
        }
    } else if (screen == StudyScreen.GOALS) {
        GoalsScreen(
            database.studyGoalDao(),
            database.studySessionDao(),
            onBack = { screen = StudyScreen.TASKS }
        )
    } else {
        screenState.SaveableStateProvider("tasks") {
            TasksScreen(
                database.studyTaskDao(),
                onOpenStopwatch = { screen = StudyScreen.STOPWATCH },
                onOpenHistory = { screen = StudyScreen.HISTORY },
                onOpenGoals = { screen = StudyScreen.GOALS }
            )
        }
    }
}

private enum class StudyScreen { TASKS, STOPWATCH, HISTORY, GOALS }

internal data class StudyTimer(
    val id: String = "",
    val startedAtEpochMillis: Long = 0,
    val accumulatedMillis: Long = 0,
    val runningSinceMillis: Long = -1
) {
    val isRunning: Boolean
        get() = runningSinceMillis >= 0

    fun elapsedAt(now: Long): Long {
        val currentRun = if (isRunning) (now - runningSinceMillis).coerceAtLeast(0) else 0
        return accumulatedMillis + currentRun
    }

    fun pause(now: Long): StudyTimer =
        copy(accumulatedMillis = elapsedAt(now), runningSinceMillis = -1)

    companion object {
        val Saver = listSaver<StudyTimer, Any>(
            save = { listOf(it.id, it.startedAtEpochMillis, it.accumulatedMillis, it.runningSinceMillis) },
            restore = { StudyTimer(it[0] as String, it[1] as Long, it[2] as Long, it[3] as Long) }
        )
    }
}
