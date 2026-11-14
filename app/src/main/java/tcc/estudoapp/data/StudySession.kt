package tcc.estudoapp.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val durationMillis: Long
)

@Dao
interface StudySessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: StudySession)
}
