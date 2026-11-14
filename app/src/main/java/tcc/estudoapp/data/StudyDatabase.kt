package tcc.estudoapp.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks ORDER BY dueDateUtcMillis ASC, id ASC")
    fun observeTasks(): Flow<List<StudyTask>>

    @Insert
    suspend fun insert(task: StudyTask)

    @Query("UPDATE study_tasks SET isCompleted = NOT isCompleted WHERE id = :taskId")
    suspend fun toggleCompletion(taskId: Long)
}

@Database(
    entities = [StudyTask::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyTaskDao(): StudyTaskDao
}
