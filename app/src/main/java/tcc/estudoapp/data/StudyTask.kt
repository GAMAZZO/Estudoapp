package tcc.estudoapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskPriority {
    LOW, MEDIUM, HIGH
}

@Entity(tableName = "study_tasks")
data class StudyTask(
    val title: String,
    val dueDateUtcMillis: Long,
    val priority: TaskPriority,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "0") val isCompleted: Boolean = false
)
