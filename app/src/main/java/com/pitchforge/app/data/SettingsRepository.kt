package com.pitchforge.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "pitchforge_settings")

/** Simple user preferences (§2.3, §2.8). Uses DataStore per §4, not Room. */
data class AppSettings(
    val activeTimbres: List<String> = listOf("piano", "sine"),
    val notificationsEnabled: Boolean = true,
    val reminderTime: String = "18:00",
    /**
     * When true, the optional second-lesson nudge fires ~10 minutes before [bedtime]
     * instead of the default afternoon/evening window.
     */
    val bedtimeEnabled: Boolean = false,
    /** Local bedtime as `HH:mm` (24h). Only used when [bedtimeEnabled] is true. */
    val bedtime: String = "22:30",
    val volume: Float = 0.8f,
    val darkMode: String = "system", // "light" | "dark" | "system"
    /** Cosmetic color pack id — see [com.pitchforge.app.domain.CosmeticTheme]. */
    val themeId: String = "studio",
    val textScale: Float = 1.0f,
    /** ISO date of the habit reminders sent today (or last send day). */
    val lastHabitReminderDate: String? = null,
    /** How many habit reminders were sent on [lastHabitReminderDate] (max 2). */
    val habitReminderCountOnDate: Int = 0,
    /** Epoch ms of the most recent habit reminder. */
    val lastHabitReminderAtMs: Long? = null,
    /** ISO date of the last spaced-review-due reminder we posted. */
    val lastReviewReminderDate: String? = null,
    /** ISO date of the last monthly AP checkup reminder we posted. */
    val lastCheckupReminderDate: String? = null,
    /** ISO date of the last second-session dose nudge we posted. */
    val lastSecondSessionReminderDate: String? = null,
    val lastGeneralizationReminderDate: String? = null,
    val lastRetentionReminderDate: String? = null
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TIMBRES = stringPreferencesKey("active_timbres")
        val NOTIFS = booleanPreferencesKey("notifications_enabled")
        val REMINDER = stringPreferencesKey("reminder_time")
        val BEDTIME_ENABLED = booleanPreferencesKey("bedtime_enabled")
        val BEDTIME = stringPreferencesKey("bedtime")
        val VOLUME = floatPreferencesKey("volume")
        val DARK = stringPreferencesKey("dark_mode")
        val THEME_ID = stringPreferencesKey("theme_id")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val LAST_HABIT_REMINDER = stringPreferencesKey("last_habit_reminder_date")
        val HABIT_REMINDER_COUNT = intPreferencesKey("habit_reminder_count_on_date")
        val LAST_HABIT_REMINDER_AT = longPreferencesKey("last_habit_reminder_at_ms")
        val LAST_REVIEW_REMINDER = stringPreferencesKey("last_review_reminder_date")
        val LAST_CHECKUP_REMINDER = stringPreferencesKey("last_checkup_reminder_date")
        val LAST_SECOND_SESSION_REMINDER = stringPreferencesKey("last_second_session_reminder_date")
        val LAST_GENERALIZATION_REMINDER = stringPreferencesKey("last_generalization_reminder_date")
        val LAST_RETENTION_REMINDER = stringPreferencesKey("last_retention_reminder_date")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        activeTimbres = (this[Keys.TIMBRES] ?: "piano,sine").split(",").filter { it.isNotBlank() },
        notificationsEnabled = this[Keys.NOTIFS] ?: true,
        reminderTime = this[Keys.REMINDER] ?: "18:00",
        bedtimeEnabled = this[Keys.BEDTIME_ENABLED] ?: false,
        bedtime = this[Keys.BEDTIME] ?: "22:30",
        volume = this[Keys.VOLUME] ?: 0.8f,
        darkMode = this[Keys.DARK] ?: "system",
        themeId = this[Keys.THEME_ID] ?: "studio",
        textScale = this[Keys.TEXT_SCALE] ?: 1.0f,
        lastHabitReminderDate = this[Keys.LAST_HABIT_REMINDER],
        habitReminderCountOnDate = this[Keys.HABIT_REMINDER_COUNT] ?: 0,
        lastHabitReminderAtMs = this[Keys.LAST_HABIT_REMINDER_AT],
        lastReviewReminderDate = this[Keys.LAST_REVIEW_REMINDER],
        lastCheckupReminderDate = this[Keys.LAST_CHECKUP_REMINDER],
        lastSecondSessionReminderDate = this[Keys.LAST_SECOND_SESSION_REMINDER],
        lastGeneralizationReminderDate = this[Keys.LAST_GENERALIZATION_REMINDER],
        lastRetentionReminderDate = this[Keys.LAST_RETENTION_REMINDER]
    )

    suspend fun current(): AppSettings = context.dataStore.data.first().toSettings()

    suspend fun setActiveTimbres(timbres: List<String>) =
        context.dataStore.edit { it[Keys.TIMBRES] = timbres.joinToString(",") }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFS] = enabled }

    suspend fun setReminderTime(time: String) =
        context.dataStore.edit { it[Keys.REMINDER] = time }

    suspend fun setBedtimeEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.BEDTIME_ENABLED] = enabled }

    suspend fun setBedtime(time: String) =
        context.dataStore.edit { it[Keys.BEDTIME] = time }

    suspend fun setVolume(volume: Float) =
        context.dataStore.edit { it[Keys.VOLUME] = volume }

    suspend fun setDarkMode(mode: String) =
        context.dataStore.edit { it[Keys.DARK] = mode }

    suspend fun setThemeId(themeId: String) =
        context.dataStore.edit { it[Keys.THEME_ID] = themeId }

    suspend fun setTextScale(scale: Float) =
        context.dataStore.edit { it[Keys.TEXT_SCALE] = scale }

    suspend fun setLastHabitReminderDate(isoDate: String) =
        context.dataStore.edit { it[Keys.LAST_HABIT_REMINDER] = isoDate }

    /** Records a habit reminder send for [today], incrementing today's count. */
    suspend fun recordHabitReminder(today: String, atMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            val prevDate = prefs[Keys.LAST_HABIT_REMINDER]
            val prevCount = prefs[Keys.HABIT_REMINDER_COUNT] ?: 0
            val newCount = if (prevDate == today) prevCount + 1 else 1
            prefs[Keys.LAST_HABIT_REMINDER] = today
            prefs[Keys.HABIT_REMINDER_COUNT] = newCount
            prefs[Keys.LAST_HABIT_REMINDER_AT] = atMs
        }
    }

    suspend fun setLastReviewReminderDate(isoDate: String) =
        context.dataStore.edit { it[Keys.LAST_REVIEW_REMINDER] = isoDate }

    suspend fun setLastCheckupReminderDate(isoDate: String) =
        context.dataStore.edit { it[Keys.LAST_CHECKUP_REMINDER] = isoDate }

    suspend fun setLastSecondSessionReminderDate(isoDate: String) =
        context.dataStore.edit { it[Keys.LAST_SECOND_SESSION_REMINDER] = isoDate }

    suspend fun setLastGeneralizationReminderDate(isoDate: String) =
        context.dataStore.edit { it[Keys.LAST_GENERALIZATION_REMINDER] = isoDate }

    suspend fun setLastRetentionReminderDate(isoDate: String) =
        context.dataStore.edit { it[Keys.LAST_RETENTION_REMINDER] = isoDate }
}
