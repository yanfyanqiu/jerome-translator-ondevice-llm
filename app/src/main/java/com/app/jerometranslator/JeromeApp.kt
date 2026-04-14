package com.app.jerometranslator

import android.app.Application
import androidx.room.Room
import com.app.jerometranslator.data.AppDatabase

class JeromeApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "jerome-database",
        ).build()
    }
}
