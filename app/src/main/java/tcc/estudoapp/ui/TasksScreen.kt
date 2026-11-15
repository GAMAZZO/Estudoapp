package tcc.estudoapp.ui

import android.app.DatePickerDialog
import android.database.sqlite.SQLiteException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tcc.estudoapp.R
import tcc.estudoapp.data.StudyTask
import tcc.estudoapp.data.StudyTaskDao
import tcc.estudoapp.data.TaskPriority
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@Composable
fun TasksScreen(taskDao: StudyTaskDao, onOpenStopwatch: () -> Unit, onOpenHistory: () -> Unit) {
    var creatingTask by rememberSaveable { mutableStateOf(false) }
    var taskIdToEdit by rememberSaveable { mutableStateOf<Long?>(null) }
    val taskFlow = remember(taskDao) { taskDao.observeTasks() }
    val tasks by taskFlow.collectAsStateWithLifecycle(initialValue = null)
    val taskToEdit = tasks?.firstOrNull { it.id == taskIdToEdit }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val completionError = stringResource(R.string.task_completion_error)
    val deleteError = stringResource(R.string.task_delete_error)

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { contentPadding ->
        val modifier = Modifier.fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
        if (creatingTask || taskToEdit != null) {
            TaskForm(
                modifier = modifier,
                taskToEdit = taskToEdit,
                onSave = { title, dueDate, priority ->
                    if (taskToEdit == null) {
                        taskDao.insert(StudyTask(title, dueDate, priority))
                        true
                    } else {
                        taskDao.updateTask(taskToEdit.id, title, dueDate, priority) == 1
                    }
                },
                onClose = {
                    creatingTask = false
                    taskIdToEdit = null
                }
            )
        } else if (taskIdToEdit != null && tasks == null) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        } else {
            TaskList(
                modifier,
                tasks,
                onOpenStopwatch = onOpenStopwatch,
                onOpenHistory = onOpenHistory,
                onNewTask = {
                    taskIdToEdit = null
                    creatingTask = true
                },
                onEdit = { taskIdToEdit = it },
                onToggleCompletion = { taskId ->
                    scope.launch {
                        try {
                            taskDao.toggleCompletion(taskId)
                        } catch (_: SQLiteException) {
                            snackbarHostState.showSnackbar(completionError)
                        }
                    }
                },
                onDelete = { taskId ->
                    scope.launch {
                        try {
                            taskDao.deleteTask(taskId)
                        } catch (_: SQLiteException) {
                            snackbarHostState.showSnackbar(deleteError)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun TaskList(
    modifier: Modifier,
    tasks: List<StudyTask>?,
    onOpenStopwatch: () -> Unit,
    onOpenHistory: () -> Unit,
    onNewTask: () -> Unit,
    onEdit: (Long) -> Unit,
    onToggleCompletion: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var taskIdToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val taskToDelete = tasks?.firstOrNull { it.id == taskIdToDelete }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Text(
                text = stringResource(R.string.tasks_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewTask, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.task_new))
                }
                OutlinedButton(onClick = onOpenStopwatch, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.stopwatch_title))
                }
            }
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.history_title))
            }
        }
        if (tasks?.isEmpty() == true) {
            item {
                Text(
                    text = stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(tasks.orEmpty(), key = { it.id }) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.task_due_date, formatDueDate(task.dueDateUtcMillis)))
                    Text(stringResource(R.string.task_priority_label, stringResource(task.priority.label)))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = task.isCompleted,
                                role = Role.Checkbox,
                                onValueChange = { onToggleCompletion(task.id) }
                            )
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = task.isCompleted, onCheckedChange = null)
                        Text(stringResource(R.string.task_completed))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(task.id) }) {
                            Text(stringResource(R.string.task_edit))
                        }
                        TextButton(onClick = { taskIdToDelete = task.id }) {
                            Text(
                                text = stringResource(R.string.task_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskIdToDelete = null },
            title = { Text(stringResource(R.string.task_delete_title)) },
            text = { Text(stringResource(R.string.task_delete_confirmation, taskToDelete.title)) },
            dismissButton = {
                TextButton(onClick = { taskIdToDelete = null }) {
                    Text(stringResource(R.string.task_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    taskIdToDelete = null
                    onDelete(taskToDelete.id)
                }) {
                    Text(
                        text = stringResource(R.string.task_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }
}

@Composable
private fun TaskForm(
    modifier: Modifier,
    taskToEdit: StudyTask?,
    onSave: suspend (String, Long, TaskPriority) -> Boolean,
    onClose: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(taskToEdit?.title.orEmpty()) }
    var dueDate by rememberSaveable { mutableStateOf(taskToEdit?.dueDateUtcMillis) }
    var priority by rememberSaveable {
        mutableStateOf(taskToEdit?.priority ?: TaskPriority.MEDIUM)
    }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pickerTheme = if (isSystemInDarkTheme()) {
        android.R.style.Theme_Material_Dialog_Alert
    } else {
        android.R.style.Theme_Material_Light_Dialog_Alert
    }

    BackHandler { if (!saving) onClose() }

    Column(
        modifier = modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(
                if (taskToEdit == null) R.string.task_new else R.string.task_edit_title
            ),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            label = { Text(stringResource(R.string.task_title)) },
            singleLine = true
        )
        OutlinedButton(
            onClick = {
                val selected = Calendar.getInstance()
                dueDate?.let { millis ->
                    val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    utcDate.timeInMillis = millis
                    selected.set(
                        utcDate.get(Calendar.YEAR),
                        utcDate.get(Calendar.MONTH),
                        utcDate.get(Calendar.DAY_OF_MONTH)
                    )
                }
                DatePickerDialog(
                    context,
                    pickerTheme,
                    { _, year, month, day ->
                        val date = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        date.clear()
                        date.set(year, month, day)
                        dueDate = date.timeInMillis
                    },
                    selected.get(Calendar.YEAR),
                    selected.get(Calendar.MONTH),
                    selected.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            enabled = !saving
        ) {
            val date = dueDate
            Text(
                if (date == null) stringResource(R.string.task_select_due_date)
                else stringResource(R.string.task_due_date, formatDueDate(date))
            )
        }
        Text(stringResource(R.string.task_priority), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            TaskPriority.entries.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = priority == option,
                        onClick = { priority = option },
                        enabled = !saving,
                        role = Role.RadioButton
                    ).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = priority == option, onClick = null, enabled = !saving)
                    Text(stringResource(option.label))
                }
            }
        }
        if (saveFailed) {
            Text(stringResource(R.string.task_save_error), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose, enabled = !saving) {
                Text(stringResource(R.string.task_cancel))
            }
            Button(
                onClick = {
                    val date = dueDate
                    if (!saving && title.isNotBlank() && date != null) {
                        val trimmedTitle = title.trim()
                        val selectedPriority = priority
                        saving = true
                        saveFailed = false
                        scope.launch {
                            try {
                                if (onSave(trimmedTitle, date, selectedPriority)) {
                                    onClose()
                                } else {
                                    saveFailed = true
                                }
                            } catch (_: SQLiteException) {
                                saveFailed = true
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
                enabled = title.isNotBlank() && dueDate != null && !saving
            ) {
                Text(stringResource(R.string.task_save))
            }
        }
    }
}

private val TaskPriority.label: Int
    get() = when (this) {
        TaskPriority.LOW -> R.string.task_priority_low
        TaskPriority.MEDIUM -> R.string.task_priority_medium
        TaskPriority.HIGH -> R.string.task_priority_high
    }

private fun formatDueDate(millis: Long): String {
    val formatter = DateFormat.getDateInstance(DateFormat.SHORT)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(millis))
}
