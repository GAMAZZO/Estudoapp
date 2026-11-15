package tcc.estudoapp.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class GoalPeriod { DAILY, WEEKLY, MONTHLY }

@Entity(tableName = "study_goals")
data class StudyGoal(
    @PrimaryKey val period: GoalPeriod,
    val targetMinutes: Long
)

@Dao
interface StudyGoalDao {
    @Query("SELECT * FROM study_goals")
    fun observeGoals(): Flow<List<StudyGoal>>

    @Upsert
    suspend fun save(goal: StudyGoal)
}
