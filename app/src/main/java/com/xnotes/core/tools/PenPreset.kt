package com.xnotes.core.tools

import com.xnotes.core.model.Rgba

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
) {
    /** The same pen as [other] when tool and style match; the ink colour the toolbar feeds in is ignored. */
    fun matches(tool: Tool, config: ToolConfig): Boolean =
        tool == this.tool && config.copy(rgba = this.config.rgba) == this.config
}

/** The pens every install starts with, in the order the pen menu lists them. */
object BuiltInPens {
    private fun pen(id: String, name: String, tool: Tool, config: ToolConfig) = PenPreset(id, name, tool, config, builtIn = true)

    private val base get() = ToolDefaults

    val all: List<PenPreset> = listOf(
        pen("ballpoint", "Esferográfica", Tool.PEN, base.configFor(Tool.PEN).copy(baseWidth = 1.6, pressureMinFactor = 0.70)),
        pen("gel", "Gel", Tool.PEN, base.configFor(Tool.PEN).copy(baseWidth = 2.8, pressureMinFactor = 0.50)),
        pen("fountain", "Tinteiro", Tool.CALLIGRAPHY, base.configFor(Tool.CALLIGRAPHY).copy(baseWidth = 3.6, pressureMinFactor = 0.50, directionStrength = 0.45)),
        pen("fineliner", "Nanquim fino", Tool.PEN, base.configFor(Tool.PEN).copy(baseWidth = 1.0, pressureEnabled = false, pressureMinFactor = 1.0)),
        pen("marker", "Pincel atômico", Tool.PEN, base.configFor(Tool.PEN).copy(baseWidth = 6.0, pressureEnabled = false, pressureMinFactor = 1.0)),
        pen("brushpen", "Pincel", Tool.TAPER, base.configFor(Tool.TAPER).copy(baseWidth = 7.0, pressureMinFactor = 0.15, taperMinFactor = 0.0)),
        pen("correction", "Correção", Tool.PEN, base.configFor(Tool.PEN).copy(baseWidth = 2.2, colorOverride = Rgba(220, 38, 38))),
        pen("neon", "Neon", Tool.PEN, base.configFor(Tool.PEN).copy(baseWidth = 2.5, neon = true, neonStrength = 0.6)),
        pen("dashed", "Tracejada", Tool.DASHED, base.configFor(Tool.DASHED)),
        pen("hl-yellow", "Marca-texto amarelo", Tool.HIGHLIGHTER, base.configFor(Tool.HIGHLIGHTER).copy(baseWidth = 16.0, highlighterAlpha = 0.50, colorOverride = Rgba(255, 235, 59))),
        pen("hl-fine", "Marca-texto fino", Tool.HIGHLIGHTER, base.configFor(Tool.HIGHLIGHTER).copy(baseWidth = 7.0, highlighterAlpha = 0.50, colorOverride = Rgba(244, 143, 177))),
        pen("hl-pastel", "Marca-texto pastel", Tool.HIGHLIGHTER, base.configFor(Tool.HIGHLIGHTER).copy(baseWidth = 20.0, highlighterAlpha = 0.35, colorOverride = Rgba(165, 214, 167))),
        pen("pencil-hb", "Lápis HB", Tool.BRUSH, base.configFor(Tool.BRUSH).copy(baseWidth = 2.2, pressureMinFactor = 0.65, brush = Brush.PENCIL, colorOverride = Rgba(60, 60, 66))),
        pen("pencil-6b", "Lápis 6B (macio)", Tool.BRUSH, base.configFor(Tool.BRUSH).copy(baseWidth = 3.8, pressureMinFactor = 0.55, brush = Brush.PENCIL, colorOverride = Rgba(38, 38, 42))),
        pen("colored-pencil", "Lápis de cor", Tool.BRUSH, base.configFor(Tool.BRUSH).copy(baseWidth = 3.0, pressureMinFactor = 0.6, brush = Brush.PENCIL)),
        pen("crayon", "Giz de cera", Tool.BRUSH, base.configFor(Tool.BRUSH).copy(baseWidth = 7.0, pressureMinFactor = 0.75, brush = Brush.CRAYON)),
        pen("chalk", "Giz de lousa", Tool.BRUSH, base.configFor(Tool.BRUSH).copy(baseWidth = 6.0, pressureMinFactor = 0.8, brush = Brush.CHALK)),
        pen("watercolor", "Aquarela", Tool.BRUSH, base.configFor(Tool.BRUSH).copy(baseWidth = 12.0, pressureMinFactor = 0.4, brush = Brush.WATERCOLOR)),
        pen("spray", "Spray", Tool.BRUSH, base.configFor(Tool.BRUSH).copy(baseWidth = 10.0, pressureMinFactor = 0.6, brush = Brush.SPRAY)),
        pen("rainbow", "Arco-íris", Tool.BRUSH, base.configFor(Tool.BRUSH).copy(baseWidth = 4.0, pressureMinFactor = 0.5, brush = Brush.RAINBOW)),
    )
}
