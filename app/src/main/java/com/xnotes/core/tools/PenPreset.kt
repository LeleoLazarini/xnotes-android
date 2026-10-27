package com.xnotes.core.tools

import com.xnotes.core.model.Rgba

/** The drawer of the material kit a pen lives in; each one is its own toolbar button. */
enum class PenFamily(val id: String) {
    PEN("pen"),
    PENCIL("pencil"),
    MARKER("marker"),
    HIGHLIGHTER("highlighter"),
    CHALK("chalk"),
    BRUSH("brush");

    companion object {
        fun fromId(id: String?): PenFamily? = entries.firstOrNull { it.id == id }

        /** Where a style belongs when nothing says so: by the tool, the brush, and how bold it is. */
        fun of(tool: Tool, config: ToolConfig): PenFamily = when (tool) {
            Tool.HIGHLIGHTER -> HIGHLIGHTER
            Tool.TAPER -> BRUSH
            Tool.BRUSH -> when (config.brush) {
                Brush.PENCIL -> PENCIL
                Brush.CRAYON, Brush.CHALK -> CHALK
                Brush.WATERCOLOR, Brush.SPRAY, Brush.RAINBOW -> BRUSH
            }
            else -> when {
                config.neon -> BRUSH
                config.baseWidth >= MARKER_WIDTH -> MARKER
                else -> PEN
            }
        }

        /** A plain pen this wide or wider reads as a marker. */
        private const val MARKER_WIDTH = 4.5
    }
}

/**
 * A named pen: a stroke tool and the style it draws with. Picking one arms [tool] with [config],
 * so a pen keeps its own colour ([ToolConfig.colorOverride]) or follows the toolbar's when it has
 * none. The built-in ones ship with the app; the user's own are kept in settings.
 */
data class PenPreset(
    val id: String,
    val name: String,
    val tool: Tool,
    val config: ToolConfig,
    val builtIn: Boolean = false,
    val family: PenFamily = PenFamily.of(tool, config),
) {
    /** The same pen as [other] when tool and style match; the ink colour the toolbar feeds in is ignored. */
    fun matches(tool: Tool, config: ToolConfig): Boolean =
        tool == this.tool && config.copy(rgba = this.config.rgba) == this.config
}

/** The pens every install starts with, drawer by drawer, in the order the menus list them. */
object BuiltInPens {
    private fun pen(family: PenFamily, id: String, name: String, tool: Tool, config: ToolConfig) =
        PenPreset(id, name, tool, config, builtIn = true, family = family)

    private fun d(tool: Tool) = ToolDefaults.configFor(tool)
    private fun pencil(width: Double, min: Double, color: Rgba?) =
        d(Tool.BRUSH).copy(baseWidth = width, pressureMinFactor = min, brush = Brush.PENCIL, colorOverride = color)
    private fun hl(width: Double, alpha: Double, color: Rgba, straight: Boolean = false) =
        d(Tool.HIGHLIGHTER).copy(baseWidth = width, highlighterAlpha = alpha, colorOverride = color, straightLine = straight)
    private fun flat(width: Double, color: Rgba? = null) =
        d(Tool.PEN).copy(baseWidth = width, pressureEnabled = false, pressureMinFactor = 1.0, colorOverride = color)

    private val F = PenFamily.PEN
    private val L = PenFamily.PENCIL
    private val M = PenFamily.MARKER
    private val H = PenFamily.HIGHLIGHTER
    private val C = PenFamily.CHALK
    private val B = PenFamily.BRUSH

    val all: List<PenPreset> = listOf(
        // Canetas
        pen(F, "ballpoint-fine", "Esferográfica fina", Tool.PEN, d(Tool.PEN).copy(baseWidth = 1.2, pressureMinFactor = 0.75)),
        pen(F, "ballpoint", "Esferográfica", Tool.PEN, d(Tool.PEN).copy(baseWidth = 1.6, pressureMinFactor = 0.70)),
        pen(F, "gel-05", "Gel 0.5", Tool.PEN, d(Tool.PEN).copy(baseWidth = 2.2, pressureMinFactor = 0.55)),
        pen(F, "gel", "Gel 0.7", Tool.PEN, d(Tool.PEN).copy(baseWidth = 3.0, pressureMinFactor = 0.50)),
        pen(F, "fountain", "Tinteiro", Tool.CALLIGRAPHY, d(Tool.CALLIGRAPHY).copy(baseWidth = 3.6, pressureMinFactor = 0.50, directionStrength = 0.45)),
        pen(F, "fountain-wide", "Tinteiro caligrafia", Tool.CALLIGRAPHY, d(Tool.CALLIGRAPHY).copy(baseWidth = 6.5, pressureMinFactor = 0.45, directionStrength = 0.70)),
        pen(F, "fineliner-01", "Nanquim 0.1", Tool.PEN, flat(0.7)),
        pen(F, "fineliner", "Nanquim 0.3", Tool.PEN, flat(1.0)),
        pen(F, "fineliner-05", "Nanquim 0.5", Tool.PEN, flat(1.6)),
        pen(F, "speed", "Caneta de velocidade", Tool.SPEED, d(Tool.SPEED).copy(baseWidth = 3.0)),
        pen(F, "dashed", "Tracejada", Tool.DASHED, d(Tool.DASHED)),
        pen(F, "correction", "Correção vermelha", Tool.PEN, d(Tool.PEN).copy(baseWidth = 2.2, colorOverride = Rgba(220, 38, 38))),
        pen(F, "review", "Revisão verde", Tool.PEN, d(Tool.PEN).copy(baseWidth = 2.2, colorOverride = Rgba(22, 128, 61))),

        // Lápis
        pen(L, "pencil-2h", "Lápis 2H (duro)", Tool.BRUSH, pencil(1.9, 0.75, Rgba(118, 118, 126))),
        pen(L, "pencil-hb", "Lápis HB", Tool.BRUSH, pencil(2.2, 0.65, Rgba(60, 60, 66))),
        pen(L, "pencil-2b", "Lápis 2B", Tool.BRUSH, pencil(2.8, 0.60, Rgba(48, 48, 54))),
        pen(L, "pencil-6b", "Lápis 6B (macio)", Tool.BRUSH, pencil(3.8, 0.55, Rgba(38, 38, 42))),
        pen(L, "mechanical", "Lapiseira 0.5", Tool.BRUSH, pencil(1.4, 0.85, Rgba(70, 70, 76))),
        pen(L, "colored-pencil", "Lápis de cor", Tool.BRUSH, pencil(3.0, 0.60, null)),
        pen(L, "colored-pencil-wide", "Lápis de cor grosso", Tool.BRUSH, pencil(4.6, 0.55, null)),

        // Canetões
        pen(M, "marker", "Pincel atômico", Tool.PEN, flat(6.0)),
        pen(M, "permanent", "Marcador permanente", Tool.PEN, d(Tool.PEN).copy(baseWidth = 4.6, pressureMinFactor = 0.85)),
        pen(M, "felt-tip", "Canetinha hidrocor", Tool.PEN, flat(3.2)),
        pen(M, "fine-marker", "Marcador fino", Tool.PEN, flat(2.4)),
        pen(M, "whiteboard", "Marcador de quadro", Tool.PEN, flat(8.5)),

        // Marca-textos
        pen(H, "hl-yellow", "Amarelo", Tool.HIGHLIGHTER, hl(16.0, 0.50, Rgba(255, 235, 59))),
        pen(H, "hl-green", "Verde", Tool.HIGHLIGHTER, hl(16.0, 0.50, Rgba(118, 255, 3))),
        pen(H, "hl-pink", "Rosa", Tool.HIGHLIGHTER, hl(16.0, 0.50, Rgba(255, 64, 129))),
        pen(H, "hl-blue", "Azul", Tool.HIGHLIGHTER, hl(16.0, 0.45, Rgba(0, 176, 255))),
        pen(H, "hl-orange", "Laranja", Tool.HIGHLIGHTER, hl(16.0, 0.50, Rgba(255, 145, 0))),
        pen(H, "hl-pastel", "Pastel lilás", Tool.HIGHLIGHTER, hl(20.0, 0.35, Rgba(206, 147, 216))),
        pen(H, "hl-fine", "Fino para sublinhar", Tool.HIGHLIGHTER, hl(7.0, 0.50, Rgba(244, 143, 177))),
        pen(H, "hl-straight", "Reto (régua)", Tool.HIGHLIGHTER, hl(16.0, 0.50, Rgba(255, 235, 59), straight = true)),

        // Gizes
        pen(C, "chalk", "Giz de lousa", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 6.0, pressureMinFactor = 0.80, brush = Brush.CHALK)),
        pen(C, "chalk-wide", "Giz colorido grosso", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 9.0, pressureMinFactor = 0.80, brush = Brush.CHALK)),
        pen(C, "charcoal", "Carvão", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 7.0, pressureMinFactor = 0.65, brush = Brush.CHALK, colorOverride = Rgba(28, 28, 30))),
        pen(C, "crayon", "Giz de cera", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 7.0, pressureMinFactor = 0.75, brush = Brush.CRAYON)),
        pen(C, "crayon-fine", "Giz de cera fino", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 4.0, pressureMinFactor = 0.75, brush = Brush.CRAYON)),
        pen(C, "pastel", "Giz pastel", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 12.0, pressureMinFactor = 0.85, brush = Brush.CRAYON)),

        // Pincéis e efeitos
        pen(B, "brushpen", "Pincel", Tool.TAPER, d(Tool.TAPER).copy(baseWidth = 7.0, pressureMinFactor = 0.15, taperMinFactor = 0.0)),
        pen(B, "brushpen-fine", "Pincel fino", Tool.TAPER, d(Tool.TAPER).copy(baseWidth = 3.5, pressureMinFactor = 0.20, taperMinFactor = 0.0)),
        pen(B, "brush-callig", "Pincel caligráfico", Tool.CALLIGRAPHY, d(Tool.CALLIGRAPHY).copy(baseWidth = 9.0, pressureMinFactor = 0.25, directionStrength = 0.80)),
        pen(B, "watercolor", "Aquarela", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 12.0, pressureMinFactor = 0.40, brush = Brush.WATERCOLOR)),
        pen(B, "watercolor-wide", "Aquarela larga", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 22.0, pressureMinFactor = 0.40, brush = Brush.WATERCOLOR)),
        pen(B, "spray", "Spray", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 10.0, pressureMinFactor = 0.60, brush = Brush.SPRAY)),
        pen(B, "spray-fine", "Spray fino", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 5.0, pressureMinFactor = 0.60, brush = Brush.SPRAY)),
        pen(B, "neon", "Neon", Tool.PEN, d(Tool.PEN).copy(baseWidth = 2.5, neon = true, neonStrength = 0.6)),
        pen(B, "neon-bold", "Neon forte", Tool.PEN, d(Tool.PEN).copy(baseWidth = 4.0, neon = true, neonStrength = 0.9)),
        pen(B, "rainbow", "Arco-íris", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 4.0, pressureMinFactor = 0.50, brush = Brush.RAINBOW)),
        pen(B, "rainbow-wide", "Arco-íris largo", Tool.BRUSH, d(Tool.BRUSH).copy(baseWidth = 10.0, pressureMinFactor = 0.50, brush = Brush.RAINBOW)),
    )

    fun of(family: PenFamily): List<PenPreset> = all.filter { it.family == family }
}

/** Starting colours for each drawer's toolbar palette, before the user changes them. */
object FamilyPalettes {
    fun defaultFor(f: PenFamily, current: List<Rgba>): List<Rgba> {
        val six = when (f) {
            PenFamily.HIGHLIGHTER -> listOf(Rgba(255, 235, 59), Rgba(118, 255, 3), Rgba(255, 64, 129), Rgba(0, 176, 255), Rgba(255, 145, 0), Rgba(206, 147, 216))
            PenFamily.PENCIL -> listOf(Rgba(40, 40, 45), Rgba(110, 110, 118), Rgba(190, 40, 40), Rgba(40, 80, 170), Rgba(40, 120, 70), Rgba(120, 80, 40))
            PenFamily.CHALK -> listOf(Rgba(245, 245, 240), Rgba(250, 220, 90), Rgba(240, 140, 170), Rgba(120, 180, 240), Rgba(140, 210, 150), Rgba(245, 160, 80))
            PenFamily.BRUSH -> listOf(Rgba(17, 24, 39), Rgba(220, 38, 38), Rgba(37, 99, 235), Rgba(22, 163, 74), Rgba(234, 179, 8), Rgba(147, 51, 234))
            else -> return current
        }
        // Keep the palette's full length: only the first six slots change.
        return six + current.drop(six.size)
    }
}
