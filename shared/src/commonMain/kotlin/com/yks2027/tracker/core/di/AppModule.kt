package com.yks2027.tracker.core.di

import android.content.Context
import androidx.room.Room
import com.yks2027.tracker.core.database.ChatDao
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.database.TopicDao
import com.yks2027.tracker.core.database.YksDatabase
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.SystemIstanbulClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YksDatabase =
        Room.databaseBuilder(context, YksDatabase::class.java, "yks.db")
            .addMigrations(
                YksDatabase.MIGRATION_1_2,
                YksDatabase.MIGRATION_2_3,
                YksDatabase.MIGRATION_3_4,
                YksDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun provideAiProfileDao(db: YksDatabase): com.yks2027.tracker.core.database.AiProfileDao =
        db.aiProfileDao()

    @Provides
    fun provideNoteDao(db: YksDatabase): com.yks2027.tracker.core.database.NoteDao = db.noteDao()

    @Provides
    fun provideTopicDao(db: YksDatabase): TopicDao = db.topicDao()

    @Provides
    fun provideExamDao(db: YksDatabase): ExamDao = db.examDao()

    @Provides
    fun providePlanDao(db: YksDatabase): PlanDao = db.planDao()

    @Provides
    fun provideFocusDao(db: YksDatabase): FocusDao = db.focusDao()

    @Provides
    fun provideChatDao(db: YksDatabase): ChatDao = db.chatDao()

    @Provides
    @Singleton
    fun provideClock(): IstanbulClock = SystemIstanbulClock()
}
