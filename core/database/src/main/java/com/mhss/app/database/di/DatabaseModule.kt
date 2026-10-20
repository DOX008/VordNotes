package com.mhss.app.database.di

import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.mhss.app.database.MyBrainDatabase
import com.mhss.app.database.UserDatabaseManager
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
