package tcc.estudoapp

import android.app.Application
import androidx.room.Room
import tcc.estudoapp.data.StudyDatabase

class EstudoApplication : Application() {
    val database: StudyDatabase by lazy {
        Room.databaseBuilder(this, StudyDatabase::class.java, "estudo.db").build()
    }
}
