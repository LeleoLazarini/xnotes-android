package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import com.xnotes.core.model.Rgba
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnotes.R
import kotlinx.coroutines.launch
import com.xnotes.core.tools.PenFamily
import com.xnotes.core.tools.Tool
import androidx.compose.ui.res.vectorResource
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * The grouped paged bar: related tools fold into one button each, so the whole bar fits a tablet
 * without scrolling. A group button shows the group's last-used tool; tapping it arms that tool,
 * and tapping it again while armed opens the group's list (plus that tool's settings).
 */
internal enum class ToolGroup(val tools: List<Tool>) {
    PENS(listOf(Tool.PEN, Tool.CALLIGRAPHY, Tool.TAPER, Tool.SPEED, Tool.DASHED, Tool.BRUSH)),
    SELECTION(listOf(Tool.LASSO, Tool.SELECT, Tool.PAN, Tool.SCREENSHOT)),
    INSERT(listOf(Tool.SHAPE, Tool.TEXT, Tool.TEXT_BOX, Tool.MARKUP)),
}

/** The last tool used in each group, for the whole session, so switching notes keeps it. */
private val lastInGroup = mutableStateMapOf<ToolGroup, Tool>()

private fun groupOf(tool: Tool): ToolGroup? = ToolGroup.entries.firstOrNull { tool in it.tools }

/** What the bar's gliding circle follows: the group button for a grouped tool, else the tool. */
internal fun glideKeyFor(tool: Tool): Any = groupOf(tool) ?: tool

/** The grouped bar's circle: on an ink tool it sits under that pen's kit drawer. */
internal fun glideKeyFor(editor: Editor): Any = editor.activeFamily() ?: glideKeyFor(editor.tool)

private fun configurable(tool: Tool) =
    tool.isStroke || tool == Tool.SHAPE || tool == Tool.ERASER || tool == Tool.SELECT || tool == Tool.TEXT || tool == Tool.MARKUP

/** A small filled triangle in the button's lower-right corner. */
internal fun DrawScope.drawGroupWedge(color: Color) {
    val s = 5.dp.toPx()
    val inset = 5.dp.toPx()
    val right = size.width - inset
    val bottom = size.height - inset
    val path = Path().apply {
        moveTo(right, bottom - s)
        lineTo(right, bottom)
        lineTo(right - s, bottom)
        close()
    }
    drawPath(path, color)
}

@Composable
internal fun GroupedBarContent(
    editor: Editor,
    toolIcons: Map<Tool, ImageVector>,
    configForTool: Tool?,
    setConfigForTool: (Tool?) -> Unit,
    switcherIndex: Int?,
    setSwitcherIndex: (Int?) -> Unit,
    onRename: () -> Unit,
    onOpenBackstage: () -> Unit,
    onInsertImage: () -> Unit,
    onAddStickers: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onImportTemplate: () -> Unit,
) {
    ToolbarIcon(XnotesIcons.prev, stringResource(R.string.toolbar_home)) { onOpenBackstage() }
    Separator()
    PenFamily.entries.forEach { FamilyButton(editor, it, toolIcons, configForTool, setConfigForTool) }
    ToolButton(editor, Tool.ERASER, KitIcons.eraser, configForTool, setConfigForTool, colored = true)
    ToolGroupButton(editor, ToolGroup.SELECTION, toolIcons, configForTool, setConfigForTool)
    Separator()
    CompactColors(editor, switcherIndex, setSwitcherIndex)
    Separator()
    InsertGroupButton(editor, toolIcons, configForTool, setConfigForTool, onInsertImage, onAddStickers)
    PostItButton(editor)
    StylesButton(editor, onImportTemplate)
    Separator()
    ToolbarIcon(XnotesIcons.undo, stringResource(R.string.undo), enabled = editor.canUndo, shortcut = Keys.UNDO) { editor.undo() }
    ToolbarIcon(XnotesIcons.redo, stringResource(R.string.redo), enabled = editor.canRedo, shortcut = Keys.REDO) { editor.redo() }
    MoreMenu(editor, onRename, onToggleFullscreen)
}

/** The ink [pen] lays down: its own colour, or the toolbar's active one when it follows the toolbar. */
@Composable
private fun familyInk(editor: Editor, pen: com.xnotes.core.tools.PenPreset): Color =
    (pen.config.colorOverride ?: editor.toolbarColors.getOrNull(editor.activeColorIndex) ?: pen.config.rgba).toComposeColor()

/**
 * The toolbar colours as a small block: two rows of dots in the room of about two buttons, so a
 * full palette never pushes tools off the bar. Tapping the active dot opens its colour switcher.
 */
@Composable
private fun CompactColors(editor: Editor, switcherIndex: Int?, setSwitcherIndex: (Int?) -> Unit) {
    val colors = editor.toolbarColors.take(editor.toolbarColorCount)
    val bar = LocalBar.current
    val perLine = (colors.size + 1) / 2
    val dot = (bar.button - 10.dp) / 2
    @Composable
    fun cell(i: Int) {
        val c = colors[i].toComposeColor()
        val on = i == editor.activeColorIndex
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(dot + 4.dp)) {
            Box(
                Modifier
                    .size(dot)
                    .then(if (on) Modifier.border(2.dp, LocalPalette.current.text.toComposeColor(), CircleShape) else Modifier)
                    .padding(if (on) 3.dp else 1.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(0.5.dp, Color(0x33000000), CircleShape)
                    .clickable { if (on) setSwitcherIndex(i) else editor.pickColor(i) },
            )
            if (switcherIndex == i) ColorSwitcherPopup(editor, i) { setSwitcherIndex(null) }
        }
    }
    if (bar.vertical) {
        Row { for (col in 0 until 2) Column { for (i in col * perLine until minOf(colors.size, (col + 1) * perLine)) cell(i) } }
    } else {
        Column(Modifier.padding(horizontal = 2.dp)) { for (row in 0 until 2) Row { for (i in row * perLine until minOf(colors.size, (row + 1) * perLine)) cell(i) } }
    }
}

/** The kit drawer's toolbar picture and name. */
@Composable
private fun familyIcon(f: PenFamily): ImageVector = ImageVector.vectorResource(
    when (f) {
        PenFamily.PEN -> R.drawable.ic_kit_pen
        PenFamily.PENCIL -> R.drawable.ic_kit_pencil
        PenFamily.MARKER -> R.drawable.ic_kit_marker
        PenFamily.HIGHLIGHTER -> R.drawable.ic_kit_highlighter
        PenFamily.CHALK -> R.drawable.ic_kit_chalk
        PenFamily.BRUSH -> R.drawable.ic_kit_brush
    },
)

private fun familyLabel(f: PenFamily): Int = when (f) {
    PenFamily.PEN -> R.string.kit_pens
    PenFamily.PENCIL -> R.string.kit_pencils
    PenFamily.MARKER -> R.string.kit_markers
    PenFamily.HIGHLIGHTER -> R.string.kit_highlighters
    PenFamily.CHALK -> R.string.kit_chalks
    PenFamily.BRUSH -> R.string.kit_brushes
}

/** The pen each drawer re-arms on a single tap: the last one picked from it this session. */
private val lastPenInFamily = mutableStateMapOf<PenFamily, String>()

/**
 * One drawer of the material kit. A tap arms the drawer's last pen; tapping the armed drawer
 * opens its pens, the user's own in that drawer, and the pen's settings.
 */
@Composable
private fun FamilyButton(
    editor: Editor,
    family: PenFamily,
    toolIcons: Map<Tool, ImageVector>,
    configForTool: Tool?,
    setConfigForTool: (Tool?) -> Unit,
) {
    editor.pensVersion
    val armedHere = editor.activeFamily() == family
    val pens = editor.pens.filter { it.family == family }
    val current = pens.firstOrNull { it.id == lastPenInFamily[family] } ?: pens.firstOrNull() ?: return
    val active = editor.activePen()
    LaunchedEffect(active?.id) { active?.takeIf { it.family == family }?.let { lastPenInFamily[family] = it.id } }
    var menuOpen by remember { mutableStateOf(false) }
    var savingPen by remember { mutableStateOf(false) }
    val palette = LocalPalette.current
    Box {
        ToolbarIcon(
            KitIcons.get(family, familyInk(editor, current)),
            stringResource(familyLabel(family)),
            active = armedHere,
            glideKey = family,
            hint = if (armedHere) stringResource(R.string.tip_tap_for_group) else null,
            grouped = true,
            colored = true,
        ) {
            if (!armedHere) {
                editor.applyPen(current)
                setConfigForTool(null)
            } else {
                menuOpen = true
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Text(
                stringResource(familyLabel(family)),
                color = palette.textDim.toComposeColor(),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            var shownMine = false
            pens.forEach { pen ->
                if (!pen.builtIn && !shownMine) {
                    shownMine = true
                    MenuDivider()
                    Text(stringResource(R.string.my_pens), color = palette.textDim.toComposeColor(), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                }
                val on = pen.id == active?.id
                val ink = pen.config.colorOverride?.toComposeColor() ?: palette.textDim.toComposeColor()
                DropdownMenuItem(
                    text = { Text(pen.name, color = (if (on) palette.selectionForeground else palette.text).toComposeColor()) },
                    leadingIcon = { Icon(KitIcons.get(family, familyInk(editor, pen)), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(26.dp)) },
                    trailingIcon = if (pen.builtIn) null else { {
                        Icon(
                            XnotesIcons.close,
                            contentDescription = stringResource(R.string.delete),
                            tint = palette.textDim.toComposeColor(),
                            modifier = Modifier.size(18.dp).clickable { editor.removeUserPen(pen.id) },
                        )
                    } },
                    modifier = if (on) Modifier.background(palette.selectionBackground.toComposeColor()) else Modifier,
                    onClick = {
                        menuOpen = false
                        lastPenInFamily[family] = pen.id
                        editor.applyPen(pen)
                        setConfigForTool(null)
                    },
                )
            }
            if (armedHere) {
                MenuDivider()
                GroupMenuItem(stringResource(R.string.tool_settings), XnotesIcons.sliders) {
                    menuOpen = false
                    setConfigForTool(editor.tool)
                }
                GroupMenuItem(stringResource(R.string.save_pen), XnotesIcons.save) {
                    menuOpen = false
                    savingPen = true
                }
            }
        }
        if (armedHere && configForTool != null && configForTool == editor.tool) ToolConfigFor(editor, configForTool) { setConfigForTool(null) }
        if (savingPen) SavePenDialog(onSave = { editor.saveCurrentPen(it); savingPen = false }, onDismiss = { savingPen = false })
    }
}

/** Opens the settings popup for [tool], the same one its own button opens on the classic bar. */
@Composable
private fun ToolConfigFor(editor: Editor, tool: Tool, onDismiss: () -> Unit) {
    when (tool) {
        Tool.SHAPE -> ShapeConfigPopup(editor, onDismiss)
        Tool.ERASER -> EraserConfigPopup(editor, onDismiss)
        Tool.SELECT -> SelectConfigPopup(editor, onDismiss)
        Tool.TEXT -> TextToolConfigPopup(editor, onDismiss)
        Tool.MARKUP -> MarkupToolPopup(editor, onDismiss)
        else -> ToolConfigPopup(editor, tool, onDismiss)
    }
}

@Composable
private fun GroupMenuItem(label: String, icon: ImageVector?, active: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val tint = (if (active) palette.selectionForeground else palette.textDim).toComposeColor()
    DropdownMenuItem(
        text = { Text(label, color = if (active) tint else palette.text.toComposeColor()) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) } },
        modifier = if (active) Modifier.clip(MaterialTheme.shapes.small) else Modifier,
        onClick = onClick,
    )
}

@Composable
private fun MenuDivider() = HorizontalDivider(color = LocalPalette.current.border.toComposeColor())

@Composable
private fun ToolGroupButton(
    editor: Editor,
    group: ToolGroup,
    toolIcons: Map<Tool, ImageVector>,
    configForTool: Tool?,
    setConfigForTool: (Tool?) -> Unit,
) {
    val armed = editor.tool.takeIf { it in group.tools }
    LaunchedEffect(armed) { armed?.let { lastInGroup[group] = it } }
    val current = armed ?: lastInGroup[group] ?: group.tools.first()
    var menuOpen by remember { mutableStateOf(false) }
    var savingPen by remember { mutableStateOf(false) }
    Box {
        ToolbarIcon(
            toolIcons[current] ?: return@Box,
            stringResource(current.labelRes),
            active = armed != null,
            glideKey = group,
            hint = if (armed != null) stringResource(R.string.tip_tap_for_group) else null,
            grouped = true,
        ) {
            if (armed == null) {
                editor.selectTool(current)
                setConfigForTool(null)
            } else {
                menuOpen = true
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (group == ToolGroup.PENS) {
                PenMenuItems(editor, toolIcons) { menuOpen = false; setConfigForTool(null) }
            } else group.tools.forEach { t ->
                if (t == Tool.MARKUP && !editor.hasPdf) return@forEach
                GroupMenuItem(stringResource(t.labelRes), toolIcons[t], active = t == armed) {
                    menuOpen = false
                    editor.selectTool(t)
                    setConfigForTool(null)
                }
            }
            if (armed != null && configurable(armed)) {
                MenuDivider()
                GroupMenuItem(stringResource(R.string.tool_settings), XnotesIcons.sliders) {
                    menuOpen = false
                    setConfigForTool(armed)
                }
                if (group == ToolGroup.PENS) GroupMenuItem(stringResource(R.string.save_pen), XnotesIcons.save) {
                    menuOpen = false
                    savingPen = true
                }
            }
        }
        if (configForTool != null && configForTool in group.tools) ToolConfigFor(editor, configForTool) { setConfigForTool(null) }
        if (savingPen) SavePenDialog(onSave = { editor.saveCurrentPen(it); savingPen = false }, onDismiss = { savingPen = false })
    }
}

/**
 * The pen menu: every built-in pen, then the user's own (each removable), drawn in its own
 * colour when it has one. A pen arms its tool with its style in one tap.
 */
@Composable
private fun PenMenuItems(editor: Editor, toolIcons: Map<Tool, ImageVector>, onPicked: () -> Unit) {
    val palette = LocalPalette.current
    editor.pensVersion // re-list when a pen is saved or removed while the menu is open
    val active = editor.activePen()
    var shownMine = false
    editor.pens.forEach { pen ->
        if (!pen.builtIn && !shownMine) {
            shownMine = true
            MenuDivider()
            Text(stringResource(R.string.my_pens), color = palette.textDim.toComposeColor(), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
        val on = pen.id == active?.id
        val ink = pen.config.colorOverride?.toComposeColor() ?: palette.textDim.toComposeColor()
        DropdownMenuItem(
            text = { Text(pen.name, color = (if (on) palette.selectionForeground else palette.text).toComposeColor()) },
            leadingIcon = toolIcons[pen.tool]?.let { { Icon(it, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp)) } },
            trailingIcon = if (pen.builtIn) null else { {
                Icon(XnotesIcons.close, contentDescription = stringResource(R.string.delete), tint = palette.textDim.toComposeColor(), modifier = Modifier.size(18.dp).clickable { editor.removeUserPen(pen.id) })
            } },
            modifier = if (on) Modifier.background(palette.selectionBackground.toComposeColor()) else Modifier,
            onClick = { editor.applyPen(pen); onPicked() },
        )
    }
}

/** Names the armed pen's current style to keep it in the pen menu. */
@Composable
private fun SavePenDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_pen)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.pen_name_hint)) },
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { if (name.isNotBlank()) onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "+": shapes, text, images, stickers and the drawing aids, in one menu. */
@Composable
private fun InsertGroupButton(
    editor: Editor,
    toolIcons: Map<Tool, ImageVector>,
    configForTool: Tool?,
    setConfigForTool: (Tool?) -> Unit,
    onInsertImage: () -> Unit,
    onAddStickers: () -> Unit,
) {
    val group = ToolGroup.INSERT
    val armed = editor.tool.takeIf { it in group.tools }
    var menuOpen by remember { mutableStateOf(false) }
    var stickersOpen by remember { mutableStateOf(false) }
    Box {
        ToolbarIcon(
            armed?.let { toolIcons[it] } ?: XnotesIcons.plus,
            stringResource(R.string.group_insert),
            active = armed != null,
            glideKey = group,
            grouped = true,
        ) { menuOpen = true }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            group.tools.forEach { t ->
                if (t == Tool.MARKUP && !editor.hasPdf) return@forEach
                GroupMenuItem(stringResource(t.labelRes), toolIcons[t], active = t == armed) {
                    menuOpen = false
                    editor.selectTool(t)
                    setConfigForTool(null)
                }
            }
            MenuDivider()
            GroupMenuItem(stringResource(R.string.stickers), XnotesIcons.heart) { menuOpen = false; stickersOpen = true }
            GroupMenuItem(stringResource(R.string.insert_image_ellipsis), XnotesIcons.image) { menuOpen = false; onInsertImage() }
            GroupMenuItem(stringResource(R.string.paste_image), XnotesIcons.paste) { menuOpen = false; editor.pasteImage() }
            GroupMenuItem(stringResource(R.string.scan_document), XnotesIcons.importDoc) { menuOpen = false; editor.scanRequested = true }
            GroupMenuItem(stringResource(R.string.study_tape), XnotesIcons.lock) { menuOpen = false; editor.insertStudyTape(com.xnotes.core.model.Rgba(124, 58, 237)) }
            if (!editor.transcribing) {
                GroupMenuItem(stringResource(R.string.meeting_start), XnotesIcons.list) { menuOpen = false; editor.transcribeRequested = true }
            } else {
                GroupMenuItem(stringResource(R.string.meeting_stop), XnotesIcons.list, active = true) { menuOpen = false; editor.stopTranscription() }
            }
            if (editor.recording == null) {
                GroupMenuItem(stringResource(R.string.audio_record), XnotesIcons.clock) { menuOpen = false; editor.recordRequested = true }
            } else {
                GroupMenuItem(stringResource(R.string.audio_stop_recording), XnotesIcons.clock, active = true) { menuOpen = false; editor.stopRecording() }
            }
            MenuDivider()
            GroupMenuItem(stringResource(R.string.tool_ruler), XnotesIcons.ruler, active = editor.rulerVisible) { menuOpen = false; editor.toggleRuler() }
            GroupMenuItem(stringResource(R.string.tool_wand), XnotesIcons.magicWand, active = editor.wandEnabled) { menuOpen = false; editor.toggleWand() }
            if (armed != null && configurable(armed)) {
                MenuDivider()
                GroupMenuItem(stringResource(R.string.tool_settings), XnotesIcons.sliders) { menuOpen = false; setConfigForTool(armed) }
            }
        }
        if (stickersOpen) StickersMenu(editor, onAddStickers) { stickersOpen = false }
        if (configForTool != null && configForTool in group.tools) ToolConfigFor(editor, configForTool) { setConfigForTool(null) }
    }
}

/** The six sticky-note colours, pastel so ink and text stay readable on them. */
private val POST_IT_COLORS = listOf(
    Rgba(255, 241, 118), // amarelo
    Rgba(255, 204, 128), // laranja
    Rgba(248, 187, 208), // rosa
    Rgba(209, 196, 233), // lilás
    Rgba(179, 229, 252), // azul
    Rgba(200, 230, 201), // verde
)

/** Drops a sticky note in the chosen colour where the user is looking and starts typing in it. */
@Composable
private fun PostItButton(editor: Editor) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarIcon(KitIcons.postIt, stringResource(R.string.post_it), grouped = true, colored = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                stringResource(R.string.post_it_pick_colour),
                color = LocalPalette.current.textDim.toComposeColor(),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                POST_IT_COLORS.forEach { c ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.toComposeColor())
                            .border(1.dp, LocalPalette.current.border.toComposeColor(), RoundedCornerShape(6.dp))
                            .clickable { open = false; editor.insertPostIt(c) },
                    )
                }
            }
        }
    }
}

/** "3 / 12", tapping it jumps to a page. */
@Composable
private fun PageCounterButton(editor: Editor) {
    var jumpOpen by remember { mutableStateOf(false) }
    Box {
        Tooltip(stringResource(R.string.title_go_to_page)) {
            PageCounter(
                editor.pageIndex + 1,
                editor.pageCount,
                Modifier.clip(MaterialTheme.shapes.extraSmall).clickable { jumpOpen = true },
            )
        }
        if (jumpOpen) PageJumpPopup(editor) { jumpOpen = false }
    }
}

private enum class MorePopup { MARGINS, VIEW, ZOOM, TRANSCRIBE, JUMP }

/** Everything used now and then: margins, view, zoom and fit, side panel, full screen, rename. */
@Composable
private fun MoreMenu(editor: Editor, onRename: () -> Unit, onToggleFullscreen: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<MorePopup?>(null) }
    Box {
        ToolbarIcon(XnotesIcons.more, stringResource(R.string.more_options), grouped = true) { menuOpen = true }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            GroupMenuItem(stringResource(R.string.eyedropper), XnotesIcons.palette) { menuOpen = false; editor.eyedropperActive = true }
            GroupMenuItem(stringResource(R.string.insert_contents), XnotesIcons.contents) { menuOpen = false; editor.insertContentsPage() }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            GroupMenuItem(stringResource(R.string.update_check), XnotesIcons.download) { menuOpen = false; scope.launch { Updater.check(ctx, manual = true) } }
            val fingerDraws = editor.preferences.fingerDraws
            GroupMenuItem(stringResource(R.string.finger_draws_toggle), XnotesIcons.pan, active = fingerDraws) { menuOpen = false; editor.applyPreferences(editor.preferences.copy(fingerDraws = !fingerDraws)) }
            // Book-style turning versus one long scroll, for this note.
            val flipping = !editor.viewSettings.verticalScroll
            GroupMenuItem(stringResource(R.string.turn_pages_like_book), XnotesIcons.pages, active = flipping) {
                menuOpen = false
                editor.updateViewOverrides(editor.viewOverrides.copy(verticalScroll = flipping))
            }
            GroupMenuItem(stringResource(R.string.go_to_page_n, editor.pageIndex + 1, editor.pageCount), XnotesIcons.pages) { menuOpen = false; popup = MorePopup.JUMP }
            GroupMenuItem(stringResource(R.string.rename_note), XnotesIcons.edit) { menuOpen = false; onRename() }
            GroupMenuItem(stringResource(R.string.side_panel), XnotesIcons.sidebar, active = editor.sidebarVisible) { menuOpen = false; editor.toggleSidebar() }
            MenuDivider()
            GroupMenuItem(stringResource(R.string.toolbar_margins), XnotesIcons.margins) { menuOpen = false; popup = MorePopup.MARGINS }
            GroupMenuItem(stringResource(R.string.toolbar_view), XnotesIcons.view) { menuOpen = false; popup = MorePopup.VIEW }
            MenuDivider()
            GroupMenuItem(stringResource(R.string.title_zoom) + " (${editor.zoomPercent}%)", XnotesIcons.zoomIn) { menuOpen = false; popup = MorePopup.ZOOM }
            GroupMenuItem(stringResource(R.string.fit_page), XnotesIcons.fit) { menuOpen = false; editor.fitPage() }
            GroupMenuItem(stringResource(R.string.fit_width), XnotesIcons.fit) { menuOpen = false; editor.fitWidth() }
            GroupMenuItem(
                stringResource(if (editor.zoomLocked) R.string.unlock_zoom else R.string.lock_zoom),
                if (editor.zoomLocked) XnotesIcons.lock else XnotesIcons.unlock,
                active = editor.zoomLocked,
            ) { menuOpen = false; editor.toggleZoomLock() }
            MenuDivider()
            GroupMenuItem(stringResource(R.string.toolbar_fullscreen), XnotesIcons.fullscreen) { menuOpen = false; onToggleFullscreen() }
            // One tap between light and dark; System and OLED stay in Preferences.
            val dark = LocalPalette.current.isDark
            GroupMenuItem(stringResource(R.string.dark_theme), XnotesIcons.palette, active = dark) {
                menuOpen = false
                // The full apply: it rebuilds the chrome palette as well as the page, so menus turn too.
                editor.applyPreferences(editor.preferences.copy(uiAppearance = if (dark) "light" else "dark"))
            }
        }
        when (popup) {
            MorePopup.MARGINS -> MarginsPopup(editor) { popup = null }
            MorePopup.VIEW -> ViewMenuPopup(editor) { popup = null }
            MorePopup.ZOOM -> ZoomMenuPopup(editor) { popup = null }
            MorePopup.TRANSCRIBE -> TranscriptionTestDialog { popup = null }
            MorePopup.JUMP -> PageJumpPopup(editor) { popup = null }
            null -> Unit
        }
    }
}
