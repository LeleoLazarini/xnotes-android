package com.xnotes.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.core.shapes.Head
import com.xnotes.core.shapes.ShapePage
import com.xnotes.core.shapes.ShapePreset
import com.xnotes.core.shapes.ShapePresets
import com.xnotes.core.tools.ShapeConfig
import com.xnotes.core.tools.ToolConversions
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * The shape tool's popup: its chips page by page, swiped sideways, then the settings the chosen
 * chip takes. A closed chip offers a fill with its own colour, an open one a head at either end,
 * and a star or polygon a minus/plus for its count.
 */
@Composable
fun ShapeConfigPopup(editor: ToolPopupHost, onDismiss: () -> Unit) {
    var config by remember { mutableStateOf(editor.hostShapeConfig) }
    var pickingFill by remember { mutableStateOf(false) }
    fun emit(next: ShapeConfig) {
        config = next
        editor.updateShapeConfig(next)
    }
    val chip = config.chip
    val ink = editor.hostToolbarColors.getOrNull(editor.hostActiveColorIndex) ?: Rgba(0, 0, 0)

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.width(284.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
            PopupTitle(stringResource(R.string.title_shape))
            ShapePager(config, onPage = { emit(config.copy(shapePage = it)) }, onPick = { emit(config.choose(it)) })
            if (chip.counted) CountRow(config) { emit(config.copy(n = it)) }
            SliderRow(stringResource(R.string.caption_width), config.strokeWidth.toFloat(), 1f..20f) {
                emit(config.copy(strokeWidth = it.toDouble()))
            }
            if (chip.kind.isClosed) {
                ToggleRow(stringResource(R.string.caption_fill), config.fill) { emit(config.copy(fill = it)) }
                if (config.fill) {
                    FillColourRow(config.fillColor, ink, editor.hostToolbarColors, onPick = { emit(config.copy(fillColor = it)) }) {
                        pickingFill = true
                    }
                    val minPct = (ShapeConfig.FILL_ALPHA_MIN * 100).toFloat()
                    SliderRow(stringResource(R.string.caption_opacity), (config.fillAlpha * 100).toFloat(), minPct..100f) {
                        emit(config.copy(fillAlpha = (it / 100.0).coerceIn(ShapeConfig.FILL_ALPHA_MIN, ShapeConfig.FILL_ALPHA_MAX)))
                    }
                }
            } else {
                HeadRow(stringResource(R.string.caption_head_start), config.headStart, atStart = true) { emit(config.copy(headStart = it)) }
                HeadRow(stringResource(R.string.caption_head_end), config.headEnd, atStart = false) { emit(config.copy(headEnd = it)) }
            }
            ToggleRow(stringResource(R.string.caption_dashed), config.dashed) { emit(config.copy(dashed = it)) }
            if (config.dashed) {
                SliderRow(stringResource(R.string.caption_dash), config.dashLength.toFloat(), 2f..40f) { emit(config.copy(dashLength = it.toDouble())) }
                SliderRow(stringResource(R.string.caption_gap), config.dashGap.toFloat(), 2f..40f) { emit(config.copy(dashGap = it.toDouble())) }
            }
            ToggleRow(stringResource(R.string.caption_neon), config.neon) { emit(config.copy(neon = it)) }
            if (config.neon) {
                val intensity = ToolConversions.neonStrengthToIntensity(config.neonStrength).toFloat()
                SliderRow(stringResource(R.string.caption_glow_intensity), intensity, 0f..100f) {
                    emit(config.copy(neonStrength = ToolConversions.intensityToNeonStrength(it.toDouble())))
                }
            }
        }
    }
    if (pickingFill) {
        ColorPickerPopup(
            initial = config.fillColor ?: ink,
            recents = editor.hostRecentColors,
            onDismiss = { pickingFill = false },
            onPick = { emit(config.copy(fillColor = it.withAlpha(255))) },
        )
    }
}

/** The chips, [ShapePresets.COLUMNS] to a row, one family to a page, with its caption and the page dots. */
@Composable
private fun ShapePager(config: ShapeConfig, onPage: (Int) -> Unit, onPick: (ShapePreset) -> Unit) {
    val pages = ShapePresets.pages
    val state = rememberPagerState(initialPage = config.shapePage.coerceIn(0, pages.lastIndex)) { pages.size }
    LaunchedEffect(state.settledPage) { if (state.settledPage != config.shapePage) onPage(state.settledPage) }
    val palette = LocalPalette.current
    Text(
        stringResource(pageLabel(pages[state.currentPage])),
        color = palette.textDim.toComposeColor(),
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    // As tall as the fullest page, so a swipe never makes the popup jump.
    val rows = pages.maxOf { (it.presets.size + ShapePresets.COLUMNS - 1) / ShapePresets.COLUMNS }
    HorizontalPager(state = state, modifier = Modifier.fillMaxWidth().height((rows * CHIP + (rows - 1) * GAP).dp)) { page ->
        Column(verticalArrangement = Arrangement.spacedBy(GAP.dp)) {
            for (row in pages[page].presets.chunked(ShapePresets.COLUMNS)) {
                Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                    for (p in row) {
                        KindChip(ShapeIcons.of(p), stringResource(presetLabel(p.key)), selected = p.key == config.preset) {
                            onPick(p)
                        }
                    }
                }
            }
        }
    }
    if (pages.size > 1) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
            for (i in pages.indices) {
                val on = i == state.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape)
                        .background((if (on) palette.accent else palette.border).toComposeColor()),
                )
            }
        }
    }
}

/** The fill's colour: the outline's own, any toolbar swatch, or one picked freely. */
@Composable
private fun FillColourRow(chosen: Rgba?, ink: Rgba, swatches: List<Rgba>, onPick: (Rgba?) -> Unit, onCustom: () -> Unit) {
    val palette = LocalPalette.current
    Text(stringResource(R.string.caption_fill_colour), color = palette.text.toComposeColor(), fontSize = 12.sp)
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Swatch(ink, selected = chosen == null, label = stringResource(R.string.fill_match_outline), mark = true) { onPick(null) }
        for (c in swatches.distinct().take(MAX_SWATCHES)) {
            Swatch(c.withAlpha(255), selected = chosen == c.withAlpha(255), label = null) { onPick(c.withAlpha(255)) }
        }
        val custom = chosen?.takeIf { c -> swatches.none { it.withAlpha(255) == c } }
        Swatch(custom ?: palette.surface, selected = custom != null, label = null, plus = true, onClick = onCustom)
    }
}

@Composable
private fun Swatch(color: Rgba, selected: Boolean, label: String?, mark: Boolean = false, plus: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val body = @Composable {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(color.toComposeColor())
                .border(if (selected) 2.dp else 1.dp, (if (selected) palette.accent else palette.border).toComposeColor(), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (mark || plus) {
                Icon(
                    if (mark) XnotesIcons.link else XnotesIcons.plus,
                    contentDescription = label,
                    tint = (if (color.luminance() > 0.55) Rgba(0, 0, 0) else Rgba(255, 255, 255)).toComposeColor(),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
    if (label != null) Tooltip(label) { body() } else body()
}

/** One end's head: a chip for each, drawn on a short line. */
@Composable
private fun HeadRow(caption: String, chosen: Head, atStart: Boolean, onPick: (Head) -> Unit) {
    Text(caption, color = LocalPalette.current.text.toComposeColor(), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
        for (h in Head.entries) KindChip(ShapeIcons.head(h, atStart), stringResource(headLabel(h)), selected = h == chosen) { onPick(h) }
    }
}

/** A star's points or a polygon's sides, a step at a time within what the kind can draw. */
@Composable
private fun CountRow(config: ShapeConfig, onChange: (Int) -> Unit) {
    val range = config.chip.kind.nRange ?: return
    val palette = LocalPalette.current
    val count = config.count.coerceIn(range.first, range.last)
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (config.chip.kind.countsPoints) R.string.caption_points else R.string.caption_sides),
            color = palette.text.toComposeColor(), fontSize = 12.sp, modifier = Modifier.width(120.dp),
        )
        StepButton("−", stringResource(R.string.count_fewer), enabled = count > range.first) { onChange(count - 1) }
        Text("$count", color = palette.text.toComposeColor(), fontSize = 14.sp, modifier = Modifier.width(36.dp).padding(horizontal = 8.dp))
        StepButton("+", stringResource(R.string.count_more), enabled = count < range.last) { onChange(count + 1) }
    }
}

@Composable
private fun StepButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Tooltip(label) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .border(1.dp, palette.border.toComposeColor(), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = (if (enabled) palette.text else palette.textDim).toComposeColor(), fontSize = 16.sp)
        }
    }
}

@StringRes
private fun pageLabel(page: ShapePage): Int = when (page.key) {
    else -> R.string.shape_page_basic
}

@StringRes
internal fun presetLabel(key: String): Int = when (key) {
    "line" -> R.string.shape_line
    "arrow" -> R.string.shape_arrow
    "rectangle" -> R.string.shape_rectangle
    "rounded_rect" -> R.string.shape_rounded_rect
    "square" -> R.string.shape_square
    "capsule" -> R.string.shape_capsule
    "ellipse" -> R.string.shape_ellipse
    "circle" -> R.string.shape_circle
    "triangle" -> R.string.shape_triangle
    "right_triangle" -> R.string.shape_right_triangle
    "diamond" -> R.string.shape_diamond
    "parallelogram" -> R.string.shape_parallelogram
    "trapezoid" -> R.string.shape_trapezoid
    "pentagon" -> R.string.shape_pentagon
    "hexagon" -> R.string.shape_hexagon
    "octagon" -> R.string.shape_octagon
    "star" -> R.string.shape_star
    "cross" -> R.string.shape_cross
    "heart" -> R.string.shape_heart
    "cloud" -> R.string.shape_cloud
    "ring" -> R.string.shape_ring
    "pie" -> R.string.shape_pie
    "arc" -> R.string.shape_arc
    ShapePresets.FREE_POLYGON -> R.string.shape_free_polygon
    else -> R.string.shape_polygon
}

@StringRes
private fun headLabel(head: Head): Int = when (head) {
    Head.NONE -> R.string.head_none
    Head.OPEN -> R.string.head_open
    Head.FILLED -> R.string.head_filled
    Head.HOLLOW_TRIANGLE -> R.string.head_hollow_triangle
    Head.FILLED_DIAMOND -> R.string.head_filled_diamond
    Head.HOLLOW_DIAMOND -> R.string.head_hollow_diamond
}

/** A chip is 38 dp across; six of them and their gaps fill the popup's 256 dp. */
private const val CHIP = 38
private const val GAP = 5

/** Toolbar swatches shown beside Match and the free pick, so the row stays one line. */
private const val MAX_SWATCHES = 5
