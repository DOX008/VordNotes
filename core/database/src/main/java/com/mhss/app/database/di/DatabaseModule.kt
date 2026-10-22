package com.mhss.app.database.di

import com.mhss.app.database.UserDatabaseManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single { UserDatabaseManager(androidContext()) }

    single { get<UserDatabaseManager>().noteDao }
    single { get<UserDatabaseManager>().taskDao }
    single { get<UserDatabaseManager>().diaryDao }
    single { get<UserDatabaseManager>().bookmarkDao }
    single { get<UserDatabaseManager>().alarmDao }
}
