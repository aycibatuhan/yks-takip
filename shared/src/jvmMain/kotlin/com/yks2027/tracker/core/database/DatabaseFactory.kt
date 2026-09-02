package com.yks2027.tracker.core.database

import androidx.room.RoomDatabase

/**
 * v2.0 — where the database lives and which driver opens it is a platform decision:
 * Android keeps the framework SQLite (no driver change → v1.x files open exactly as
 * before); desktop uses BundledSQLiteDriver under the user data dir. Migrations and
 * the coroutine context are applied in the shared Koin module.
 */
interface DatabaseFactory {
    fun builder(): RoomDatabase.Builder<YksDatabase>
}
