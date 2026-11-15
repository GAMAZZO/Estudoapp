package tcc.estudoapp.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    @Query(
        "SELECT * FROM study_sessions " +
            "WHERE startedAtEpochMillis >= :start AND startedAtEpochMillis < :end " +
            "ORDER BY startedAtEpochMillis ASC, id ASC"
    )
    fun observeSessions(start: Long, end: Long): Flow<List<StudySession>>
}
