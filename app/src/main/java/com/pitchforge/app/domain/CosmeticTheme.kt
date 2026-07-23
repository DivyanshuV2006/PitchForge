package com.pitchforge.app.domain

/**
 * Cosmetic color packs unlocked by player level. Every [LEVELS_PER_THEME] levels
 * unlocks the next pack. Pure domain — no Compose dependency.
 */
enum class CosmeticTheme(
    val id: String,
    val displayName: String,
    val unlockLevel: Int,
    val blurb: String
) {
    STUDIO(
        id = "studio",
        displayName = "Studio Amber",
        unlockLevel = 1,
        blurb = "Warm charcoal stage — the default look."
    ),
    OCEAN(
        id = "ocean",
        displayName = "Deep Ocean",
        unlockLevel = 10,
        blurb = "Cool teal accents on midnight blue."
    ),
    FOREST(
        id = "forest",
        displayName = "Forest Stage",
        unlockLevel = 20,
        blurb = "Moss and cedar — calm practice booth."
    ),
    VOLCANIC(
        id = "volcanic",
        displayName = "Volcanic Ember",
        unlockLevel = 30,
        blurb = "Lava orange against basalt black."
    ),
    AURORA(
        id = "aurora",
        displayName = "Aurora Hall",
        unlockLevel = 40,
        blurb = "Icy mint glow for late-night sessions."
    ),
    MIDNIGHT(
        id = "midnight",
        displayName = "Midnight Neon",
        unlockLevel = 50,
        blurb = "Electric magenta under city lights."
    );

    fun isUnlocked(playerLevel: Int): Boolean = playerLevel >= unlockLevel

    companion object {
        const val LEVELS_PER_THEME = 10

        fun fromId(id: String?): CosmeticTheme =
            entries.firstOrNull { it.id == id } ?: STUDIO

        fun unlocked(playerLevel: Int): List<CosmeticTheme> =
            entries.filter { it.isUnlocked(playerLevel) }

        /** Next locked theme, if any — for “unlocks at level N” copy. */
        fun nextUnlock(playerLevel: Int): CosmeticTheme? =
            entries.firstOrNull { !it.isUnlocked(playerLevel) }
    }
}
