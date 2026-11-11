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
    )

    fun byId(id: Int): Skin = all.firstOrNull { it.id == id } ?: all[0]
}
