package com.ncorti.kotlin.template.app.game

/** A snake appearance. Colors are ARGB ints so the engine stays free of Android/Compose types. */
class Skin(
    val id: Int,
    val name: String,
    val cost: Int,
    val head: Int,
    val tail: Int,
    val glow: Boolean,
    val rainbow: Boolean = false,
)

object Skins {
    val all: List<Skin> = listOf(
        Skin(0, "Violet Pulse", 0, 0xFFB06BFF.toInt(), 0xFF5B21B6.toInt(), false),
        Skin(1, "Aqua Flow", 0, 0xFF2DE2E6.toInt(), 0xFF0E7490.toInt(), false),
        Skin(2, "Solar Flare", 60, 0xFFFFB347.toInt(), 0xFFFF3D81.toInt(), false),
        Skin(3, "Toxic Neon", 120, 0xFF7CFF3A.toInt(), 0xFF00A86B.toInt(), true),
        Skin(4, "Ice Crystal", 180, 0xFFE8FAFF.toInt(), 0xFF4FC3F7.toInt(), true),
        Skin(5, "Magma Core", 260, 0xFFFFE94D.toInt(), 0xFFD50000.toInt(), true),
        Skin(6, "Pink Plasma", 360, 0xFFFF6EC7.toInt(), 0xFF7B2FF7.toInt(), true),
        Skin(7, "Cosmic Rainbow", 500, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), true, true),
        Skin(8, "Crimson Fang", 420, 0xFFFF5A5F.toInt(), 0xFF7A0019.toInt(), false),
        Skin(9, "Ocean Deep", 460, 0xFF4DD0FF.toInt(), 0xFF0B2E83.toInt(), true),
        Skin(10, "Golden Dragon", 700, 0xFFFFE066.toInt(), 0xFFB8860B.toInt(), true),
        Skin(11, "Shadow Viper", 650, 0xFF9AA0B5.toInt(), 0xFF14141F.toInt(), true),
        Skin(12, "Sakura Bloom", 720, 0xFFFFC2E2.toInt(), 0xFFE0489B.toInt(), true),
        Skin(13, "Emerald Mist", 780, 0xFF7DFFB3.toInt(), 0xFF006B4F.toInt(), true),
        Skin(14, "Electric Storm", 900, 0xFFFFFF7A.toInt(), 0xFF2D5BFF.toInt(), true),
        Skin(15, "Lava Lord", 1000, 0xFFFFB300.toInt(), 0xFF8B0000.toInt(), true),
        Skin(16, "Aurora", 1200, 0xFF7CFFCB.toInt(), 0xFF8E44FF.toInt(), true),
        Skin(17, "Void Walker", 1500, 0xFFD18CFF.toInt(), 0xFF05010F.toInt(), true),
        Skin(18, "Candy Rush", 1800, 0xFFFF8FD8.toInt(), 0xFF5CE1FF.toInt(), true),
        Skin(19, "Infinity Prism", 2500, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), true, true),
    )

    fun byId(id: Int): Skin = all.firstOrNull { it.id == id } ?: all[0]
}
