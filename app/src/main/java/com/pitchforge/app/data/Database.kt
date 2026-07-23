package com.pitchforge.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        PitchProgressEntity::class,
        NoteStatEntity::class,
        LessonSessionEntity::class,
        QuestionAttemptEntity::class,
        GeneralizationProbeEntity::class,
        RetentionCheckEntity::class,
        DailyMissionEntity::class,
        ApCheckupEntity::class,
        SettingsEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class PitchForgeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun pitchProgressDao(): PitchProgressDao
    abstract fun noteStatDao(): NoteStatDao
    abstract fun lessonSessionDao(): LessonSessionDao
    abstract fun questionAttemptDao(): QuestionAttemptDao
    abstract fun generalizationProbeDao(): GeneralizationProbeDao
    abstract fun retentionCheckDao(): RetentionCheckDao
    abstract fun dailyMissionDao(): DailyMissionDao
    abstract fun apCheckupDao(): ApCheckupDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: PitchForgeDatabase? = null

        /**
         * Explicit migration list (§3): the app ships versioned from day one and never uses
         * fallbackToDestructiveMigration in production. New schema versions must append a
         * Migration here so user progress is preserved across upgrades.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // v1 -> v2: track the count of correct-within-deadline naming attempts so mastery
            // can be evaluated as a perfect record (100%) rather than an EMA threshold.
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE pitch_progress ADD COLUMN correctWithinDeadlineCount INTEGER NOT NULL DEFAULT 0"
                    )
                }
            },
            // v2 -> v3: spaced-repetition review scheduling for mastered notes.
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE pitch_progress ADD COLUMN reviewIntervalDays INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE pitch_progress ADD COLUMN nextReviewAt INTEGER"
                    )
                }
            },
            // v3 -> v4: daily-mission XP rewards + cumulative mission XP on the user.
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE daily_missions ADD COLUMN xpAwarded INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE users ADD COLUMN missionXp INTEGER NOT NULL DEFAULT 0"
                    )
                }
            },
            // v4 -> v5: monthly AP checkup results (measurement only).
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ap_checkups (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            userId INTEGER NOT NULL,
                            completedAt INTEGER NOT NULL,
                            questionCount INTEGER NOT NULL,
                            correctCount INTEGER NOT NULL,
                            accuracy REAL NOT NULL,
                            avgErrorSemitones REAL NOT NULL
                        )
                        """.trimIndent()
                    )
                }
            }
        )

        fun getInstance(context: Context): PitchForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PitchForgeDatabase::class.java,
                    "pitchforge.db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
