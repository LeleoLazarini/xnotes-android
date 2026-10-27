package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/** A folder's purpose picture and its colour, kept in the parent folder's sidecar. */
data class FolderIcon(val id: String, val color: Rgba? = null)

/** What a folder is for, with the line picture its badge shows. */
internal class FolderPurpose(val id: String, val label: String, val icon: ImageVector)

/**
 * The purposes a folder can be given, and their pictures: Feather/Lucide line icons in the same
 * 24-unit, 2-unit-stroke style as the rest of the app, drawn in the icon colour on the badge.
 */
internal object FolderPurposes {
    private fun icon(vararg d: String): ImageVector = ImageVector.Builder("purpose", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = PathParser().parsePathString(d.joinToString(" ")).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    private fun circle(cx: Double, cy: Double, r: Double) = "M ${cx - r} $cy a $r $r 0 1 0 ${2 * r} 0 a $r $r 0 1 0 ${-2 * r} 0"
    private fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double) =
        "M ${x + r} $y h ${w - 2 * r} a $r $r 0 0 1 $r $r v ${h - 2 * r} a $r $r 0 0 1 ${-r} $r h ${-(w - 2 * r)} a $r $r 0 0 1 ${-r} ${-r} v ${-(h - 2 * r)} a $r $r 0 0 1 $r ${-r} Z"

    val all: List<FolderPurpose> = listOf(
        FolderPurpose("folder", "Pasta", XnotesIcons.folder),
        FolderPurpose("personal", "Pessoal", icon("M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", circle(12.0, 7.0, 4.0))),
        FolderPurpose("meeting", "Reuniões", icon("M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2", circle(9.0, 7.0, 4.0), "M23 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75")),
        FolderPurpose("notes", "Anotações", icon("M12 20h9", "M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z")),
        FolderPurpose("tests", "Testes", icon("M10 2v7.527a2 2 0 0 1-.211.896L4.72 20.55a1 1 0 0 0 .9 1.45h12.76a1 1 0 0 0 .9-1.45l-5.069-10.127A2 2 0 0 1 14 9.527V2", "M8.5 2h7", "M7 16h10")),
        FolderPurpose("drawing", "Desenho", icon("M12 19l7-7 3 3-7 7-3-3z", "M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z", "M2 2l7.586 7.586", circle(11.0, 11.0, 2.0))),
        FolderPurpose("study", "Estudos", icon("M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z", "M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z")),
        FolderPurpose("work", "Trabalho", icon(roundRect(2.0, 7.0, 20.0, 14.0, 2.0), "M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16")),
        FolderPurpose("projects", "Projetos", icon(circle(12.0, 12.0, 10.0), circle(12.0, 12.0, 6.0), circle(12.0, 12.0, 2.0))),
        FolderPurpose("ideas", "Ideias", XnotesIcons.idea),
        FolderPurpose("money", "Finanças", icon("M12 1v22", "M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6")),
        FolderPurpose("travel", "Viagens", icon("M3 11l19-9-9 19-2-8-8-2z")),
        FolderPurpose("health", "Saúde", icon("M22 12h-4l-3 9L9 3l-3 9H2")),
        FolderPurpose("calendar", "Agenda", icon(roundRect(3.0, 4.0, 18.0, 18.0, 2.0), "M16 2v4", "M8 2v4", "M3 10h18")),
        FolderPurpose("music", "Música", icon("M9 18V5l12-2v13", circle(6.0, 18.0, 3.0), circle(18.0, 16.0, 3.0))),
        FolderPurpose("photos", "Fotos", icon(roundRect(3.0, 3.0, 18.0, 18.0, 2.0), circle(8.5, 8.5, 1.5), "M21 15l-5-5L5 21")),
        FolderPurpose("archive", "Arquivo", icon("M21 8v13H3V8", "M1 3h22v5H1z", "M10 12h4")),
        FolderPurpose("favorites", "Favoritos", icon("M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z")),
        FolderPurpose("private", "Privado", XnotesIcons.lock),
        FolderPurpose("code", "Código", icon("M16 18l6-6-6-6", "M8 6l-6 6 6 6")),
        FolderPurpose("shopping", "Compras", icon(circle(9.0, 21.0, 1.0), circle(20.0, 21.0, 1.0), "M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6")),
        FolderPurpose("clients", "Clientes", icon("M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2", circle(8.5, 7.0, 4.0), "M17 11l2 2 4-4")),
        FolderPurpose("documents", "Documentos", icon("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6", "M16 13H8", "M16 17H8", "M10 9H8")),
        FolderPurpose("goals", "Metas", icon("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7")),
        FolderPurpose("home", "Casa", XnotesIcons.home),
        FolderPurpose("leisure", "Lazer", icon("M18 8h1a4 4 0 0 1 0 8h-1", "M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z", "M6 1v3", "M10 1v3", "M14 1v3")),
        FolderPurpose("world", "Mundo", icon(circle(12.0, 12.0, 10.0), "M2 12h20", "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z")),
    )

    fun of(id: String?): FolderPurpose = all.firstOrNull { it.id == id } ?: all.first()
}

/** The colours offered for a folder and its icon: a bright set, then the neutrals. */
internal val FOLDER_COLORS = listOf(
    Rgba(37, 99, 235), Rgba(14, 165, 233), Rgba(16, 185, 129), Rgba(132, 204, 22), Rgba(245, 158, 11), Rgba(249, 115, 22),
    Rgba(239, 68, 68), Rgba(236, 72, 153), Rgba(139, 92, 246), Rgba(100, 116, 139), Rgba(31, 41, 55), Rgba(255, 255, 255),
)

/** A folder with no colour of its own: a calm slate blue. */
private val DEFAULT_FOLDER = Rgba(91, 112, 160)

/**
 * The folder's badge: a rounded square in the folder colour, lit from the top, with the purpose
 * picture in the icon colour (white unless chosen).
 */
@Composable
internal fun FolderBadge(color: Rgba?, icon: FolderIcon?, size: Dp, modifier: Modifier = Modifier) {
    val base = (color ?: DEFAULT_FOLDER).toComposeColor()
    val ink = icon?.color?.toComposeColor() ?: Color.White
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.18f), base, lerp(base, Color.Black, 0.12f))))
            .border(0.5.dp, Color(0x22000000), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(FolderPurposes.of(icon?.id).icon, contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.52f))
    }
}

/** A row of colour dots for [selected], with a "none" choice when [allowNone]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(selected: Rgba?, allowNone: Boolean, onPick: (Rgba?) -> Unit) {
    val palette = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowNone) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .border(if (selected == null) 2.5.dp else 1.dp, if (selected == null) palette.accent.toComposeColor() else palette.border.toComposeColor(), CircleShape)
                    .clickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { Text("—", color = palette.textDim.toComposeColor(), fontSize = 12.sp) }
        }
        FOLDER_COLORS.forEach { c ->
            val on = c == selected
            Box(
                Modifier.size(30.dp)
                    .border(if (on) 2.5.dp else 0.dp, if (on) palette.text.toComposeColor() else Color.Transparent, CircleShape)
                    .padding(if (on) 4.dp else 1.dp)
                    .clip(CircleShape)
                    .background(c.toComposeColor())
                    .border(0.5.dp, Color(0x33000000), CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

/**
 * Choosing how a folder looks: its purpose picture, the folder colour and the picture's colour,
 * with a live badge on top. Used on its own (Folder look…) and inside the New folder dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolderLookPicker(
    color: Rgba?,
    icon: FolderIcon?,
    onColor: (Rgba?) -> Unit,
    onIcon: (FolderIcon) -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FolderBadge(color, icon, 56.dp)
            Spacer(Modifier.size(12.dp))
            Text(FolderPurposes.of(icon?.id).label, color = palette.text.toComposeColor(), fontSize = 16.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.folder_purpose), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FolderPurposes.all.forEach { p ->
                val on = p.id == (icon?.id ?: "folder")
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background((if (on) palette.selectionBackground else palette.surface).toComposeColor())
                        .clickable { onIcon(FolderIcon(p.id, icon?.color)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(p.icon, p.label, tint = (if (on) palette.selectionForeground else palette.text).toComposeColor(), modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.folder_colour), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ColorRow(color, allowNone = true, onPick = onColor)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.folder_icon_colour), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ColorRow(icon?.color, allowNone = true) { c -> onIcon(FolderIcon(icon?.id ?: "folder", c)) }
    }
}

/** The Folder look… dialog for an existing folder. */
@Composable
internal fun FolderLookDialog(name: String, color: Rgba?, icon: FolderIcon?, onSave: (Rgba?, FolderIcon?) -> Unit, onDismiss: () -> Unit) {
    var c by remember { mutableStateOf(color) }
    var i by remember { mutableStateOf(icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = { FolderLookPicker(c, i, onColor = { c = it }, onIcon = { i = it }) },
        confirmButton = { TextButton(onClick = { onSave(c, i) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A notebook's cover: its colour, lit from the top, a darker spine, and its picture in the middle. */
@Composable
internal fun NoteCover(color: Rgba?, icon: FolderIcon?, modifier: Modifier = Modifier) {
    val base = (color ?: Rgba(91, 112, 160)).toComposeColor()
    val ink = icon?.color?.toComposeColor() ?: Color.White
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.16f), base, lerp(base, Color.Black, 0.14f)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).size(maxWidth * 0.08f, maxHeight).background(lerp(base, Color.Black, 0.25f)))
        Icon(FolderPurposes.of(icon?.id).icon, contentDescription = null, tint = ink, modifier = Modifier.size(minOf(maxWidth, maxHeight) * 0.42f))
    }
}
