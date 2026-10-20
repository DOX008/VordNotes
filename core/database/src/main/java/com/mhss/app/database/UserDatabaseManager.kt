package com.mhss.app.database

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.ConcurrentHashMap

class UserDatabaseManager(
    private val context: Context
) {
    private val databases = ConcurrentHashMap<String, MyBrainDatabase>()

    fun databaseForCurrentUser(): MyBrainDatabase {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: "guest"
        return getOrCreate(uid)
    }

    fun getOrCreate(uid: String): MyBrainDatabase {
        return databases.getOrPut(uid) {
            Room.databaseBuilder(
                context.applicationContext,
                MyBrainDatabase::class.java,
                MyBrainDatabase.databaseName(uid)
            )
                .addMigrations(
                    com.mhss.app.database.migrations.MIGRATION_1_2,
                    com.mhss.app.database.migrations.MIGRATION_2_3,
                    com.mhss.app.database.migrations.MIGRATION_3_4,
                    com.mhss.app.database.migrations.MIGRATION_4_5,
                    com.mhss.app.database.migrations.MIGRATION_5_6,
                    com.mhss.app.database.migrations.MIGRATION_6_7,
                    com.mhss.app.database.migrations.MIGRATION_7_8
                )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
