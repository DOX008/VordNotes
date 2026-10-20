package com.mhss.app.database.di

import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.mhss.app.database.MyBrainDatabase
import com.mhss.app.database.UserDatabaseManager
import com.mhss.app.database.migrations.MIGRATION_1_2
import com.mhss.app.database.migrations.MIGRATION_2_3
import com.mhss.app.database.migrations.MIGRATION_3_4
import com.mhss.app.database.migrations.MIGRATION_4_5
import com.mhss.app.database.migrations.MIGRATION_5_6
import com.mhss.app.database.migrations.MIGRATION_6_7
import com.mhss.app.database.migrations.MIGRATION_7_8
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single { UserDatabaseManager(androidContext()) }

    single {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        get<UserDatabaseManager>().getOrCreate(uid)
    }

    single { get<MyBrainDatabase>().noteDao() }
    single { get<MyBrainDatabase>().taskDao() }
    single { get<MyBrainDatabase>().diaryDao() }
    single { get<MyBrainDatabase>().bookmarkDao() }
    single { get<MyBrainDatabase>().alarmDao() }
}
