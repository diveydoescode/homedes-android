package com.homedesign.android.presentation.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.geom.AlignEdge
import com.homedesign.android.domain.geom.ArcWallGeometry
import com.homedesign.android.domain.geom.BorderKind
import com.homedesign.android.domain.geom.DistributeAxis
import com.homedesign.android.domain.geom.PlanAxis
import com.homedesign.android.domain.geom.RoomGeometry
import com.homedesign.android.domain.geom.RoomSizeMutation
import com.homedesign.android.domain.geom.RoomStyleMutation
import com.homedesign.android.domain.geom.WallStyleMutation
import com.homedesign.android.domain.geom.exteriorThicknessCM
import com.homedesign.android.domain.geom.interiorThicknessCM
import com.homedesign.android.domain.model.CeilingStyle
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.textures.FLOOR_PRESETS
import com.homedesign.android.domain.textures.TexturePreset
import com.homedesign.android.domain.textures.WALL_PRESETS
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun PropertySheetContent(
    state: EditorUiState,
    unitSystem: UnitSystem,
    onDelete: () -> Unit,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onRename: (String) -> Unit,
    onWallLength: (Double) -> Unit,
    onWallHeight: (Double) -> Unit = {},
    onWallSideColor: (side: String, hex: String) -> Unit = { _, _ -> },
    onWallSidePreset: (side: String, preset: TexturePreset) -> Unit = { _, _ -> },
    onClearWallSideTexture: (side: String) -> Unit = {},
    onWallPattern: (hatched: Boolean) -> Unit = {},
    onWallGlass: (glass: Boolean) -> Unit = {},
    onStraightenWall: () -> Unit = {},
    onAddCurvePoint: () -> Unit = {},
    onWallBaseboardEnabled: (side: String, enabled: Boolean) -> Unit = { _, _ -> },
    onWallBaseboardHeight: (side: String, heightCM: Double) -> Unit = { _, _ -> },
    onWallBaseboardThickness: (side: String, thicknessCM: Double) -> Unit = { _, _ -> },
    onFormatPainter: () -> Unit = {},
    onImportWallTexture: (side: String) -> Unit = {},
    onAddWallDimension: () -> Unit = {},
    onFloorColor: (String) -> Unit = {},
    onFloorPreset: (TexturePreset) -> Unit = {},
    onClearFloorTexture: () -> Unit = {},
    onImportFloorTexture: () -> Unit = {},
    onCeilingColor: (String) -> Unit = {},
    onCeilingPreset: (TexturePreset) -> Unit = {},
    onClearCeilingTexture: () -> Unit = {},
    onRoomBorder: (BorderKind) -> Unit = {},
    onCeilingVisible: (Boolean) -> Unit = {},
    onCeilingStyle: (CeilingStyle?) -> Unit = {},
    onRoomSize: (widthCM: Double?, depthCM: Double?) -> Unit = { _, _ -> },
    onStageRoom: () -> Unit = {},
    onOpeningWidth: (Double) -> Unit,
    onFlipHinge: () -> Unit,
    onFlipSwing: () -> Unit,
    onToggleOpeningOpen: () -> Unit = {},
    onFurnitureWidth: (Double) -> Unit,
    onFurnitureDepth: (Double) -> Unit,
    onFurnitureAngleDeg: (Double) -> Unit,
    onCopyFurniture: () -> Unit = {},
    onPasteFurniture: () -> Unit = {},
    onDuplicateFurniture: () -> Unit = {},
    onReplaceFurniture: () -> Unit = {},
    onAlign: (AlignEdge) -> Unit = {},
    onDistribute: (DistributeAxis) -> Unit = {},
    onGroupFurniture: () -> Unit = {},
    onUngroupFurniture: () -> Unit = {},
    onMirrorFurniture: (PlanAxis) -> Unit = {},
    onDimensionLength: (Double) -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val home = state.home
    val selection = state.selection
    val wall = when (selection) {
        is Selection.Wall -> home.walls.find { it.id == selection.id }
        is Selection.Endpoint -> home.walls.find { it.id == selection.wallID }
        else -> null
    }
    val room = (selection as? Selection.Room)?.let { sel -> home.rooms.find { it.id == sel.id } }
    val openingId = when (selection) {
        is Selection.Opening -> selection.id
        is Selection.OpeningHandle -> selection.id
        else -> null
    }
    val opening = openingId?.let { id -> home.doorsAndWindows.find { it.piece.id == id } }
    val furniture = (selection as? Selection.Furniture)?.let { sel ->
        home.furniture.find { it.id == sel.id }
    }
    val multi = selection as? Selection.MultiFurniture
    val dimension = (selection as? Selection.Annotation)
        ?.takeIf { !it.isLabel }
        ?.let { sel -> home.dimensionLines.find { it.id == sel.id } }
    val planLabel = (selection as? Selection.Annotation)
        ?.takeIf { it.isLabel }
        ?.let { sel -> home.labels.find { it.id == sel.id } }
    val canGroup = multi != null && multi.ids.size >= 2 &&
        com.homedesign.android.domain.editor.sharedGroupID(home, multi.ids) == null
    val canUngroup = multi != null &&
        home.furniture.any { it.id in multi.ids && it.groupID != null }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        if (!compact) {
            Text("Properties", style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
        }

        when {
            multi != null -> MultiFurniturePropertyBody(
                count = multi.ids.size,
                hasClipboard = state.hasClipboard,
                canGroup = canGroup,
                canUngroup = canUngroup,
                onAlign = onAlign,
                onDistribute = onDistribute,
                onGroup = onGroupFurniture,
                onUngroup = onUngroupFurniture,
                onMirror = onMirrorFurniture,
                onCopy = onCopyFurniture,
                onPaste = onPasteFurniture,
                onDuplicate = onDuplicateFurniture,
                onDelete = onDelete,
            )
            wall != null -> WallPropertyBody(
                wall = wall,
                unitSystem = unitSystem,
                onInterior = onInterior,
                onExterior = onExterior,
                onWallLength = onWallLength,
                onWallHeight = onWallHeight,
                onWallSideColor = onWallSideColor,
                onWallSidePreset = onWallSidePreset,
                onClearWallSideTexture = onClearWallSideTexture,
                onWallPattern = onWallPattern,
                onWallGlass = onWallGlass,
                onStraightenWall = onStraightenWall,
                onAddCurvePoint = onAddCurvePoint,
                onWallBaseboardEnabled = onWallBaseboardEnabled,
                onWallBaseboardHeight = onWallBaseboardHeight,
                onWallBaseboardThickness = onWallBaseboardThickness,
                onFormatPainter = onFormatPainter,
                onImportWallTexture = onImportWallTexture,
                onAddWallDimension = onAddWallDimension,
                onDelete = onDelete,
            )
            room != null -> RoomPropertyBody(
                room = room,
                unitSystem = unitSystem,
                onRename = onRename,
                onFloorColor = onFloorColor,
                onFloorPreset = onFloorPreset,
                onClearFloorTexture = onClearFloorTexture,
                onImportFloorTexture = onImportFloorTexture,
                onCeilingColor = onCeilingColor,
                onCeilingPreset = onCeilingPreset,
                onClearCeilingTexture = onClearCeilingTexture,
                onRoomBorder = onRoomBorder,
                onCeilingVisible = onCeilingVisible,
                onCeilingStyle = onCeilingStyle,
                onRoomSize = onRoomSize,
                onStageRoom = onStageRoom,
                onDelete = onDelete,
            )
            opening != null -> OpeningPropertyBody(
                opening = opening,
                unitSystem = unitSystem,
                hasClipboard = state.hasClipboard,
                onOpeningWidth = onOpeningWidth,
                onFlipHinge = onFlipHinge,
                onFlipSwing = onFlipSwing,
                onToggleOpen = onToggleOpeningOpen,
                onCopy = onCopyFurniture,
                onPaste = onPasteFurniture,
                onDuplicate = onDuplicateFurniture,
                onDelete = onDelete,
            )
            furniture != null -> FurniturePropertyBody(
                piece = furniture,
                unitSystem = unitSystem,
                hasClipboard = state.hasClipboard,
                onRename = onRename,
                onFurnitureWidth = onFurnitureWidth,
                onFurnitureDepth = onFurnitureDepth,
                onFurnitureAngleDeg = onFurnitureAngleDeg,
                onMirror = onMirrorFurniture,
                onCopy = onCopyFurniture,
                onPaste = onPasteFurniture,
                onDuplicate = onDuplicateFurniture,
                onReplace = onReplaceFurniture,
                onDelete = onDelete,
            )
            dimension != null -> DimensionPropertyBody(
                dim = dimension,
                unitSystem = unitSystem,
                onDimensionLength = onDimensionLength,
                onDelete = onDelete,
            )
            planLabel != null -> LabelPropertyBody(
                label = planLabel,
                onRename = onRename,
                onDelete = onDelete,
            )
            else -> {
                val title = when (selection) {
                    is Selection.Annotation -> if (selection.isLabel) "Label" else "Dimension"
                    else -> "Selection"
                }
                Text(title, style = HdTheme.typography.titleMedium, color = HdTheme.colors.ink)
                DeleteRow(onDelete)
            }
        }
    }
}

@Composable
private fun LabelPropertyBody(
    label: com.homedesign.android.domain.model.PlanLabel,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(label.id, label.text) { mutableStateOf(label.text) }
    Text("Label", style = HdTheme.typography.titleMedium, color = HdTheme.colors.ink)
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onRename(it)
        },
        label = { Text("Text") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    DeleteRow(onDelete)
}

@Composable
private fun MultiFurniturePropertyBody(
    count: Int,
    hasClipboard: Boolean,
    canGroup: Boolean,
    canUngroup: Boolean,
    onAlign: (AlignEdge) -> Unit,
    onDistribute: (DistributeAxis) -> Unit,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
    onMirror: (PlanAxis) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Text(
        "$count pieces",
        style = HdTheme.typography.titleMedium,
        color = HdTheme.colors.ink,
    )
    Text("Align", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = { onAlign(AlignEdge.Left) }) { Text("Left") }
        TextButton(onClick = { onAlign(AlignEdge.CenterX) }) { Text("Centre") }
        TextButton(onClick = { onAlign(AlignEdge.Right) }) { Text("Right") }
        TextButton(onClick = { onAlign(AlignEdge.Top) }) { Text("Top") }
        TextButton(onClick = { onAlign(AlignEdge.CenterY) }) { Text("Middle") }
        TextButton(onClick = { onAlign(AlignEdge.Bottom) }) { Text("Bottom") }
    }
    Text("Distribute", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { onDistribute(DistributeAxis.Horizontal) },
            enabled = count >= 3,
        ) { Text("Distribute H") }
        TextButton(
            onClick = { onDistribute(DistributeAxis.Vertical) },
            enabled = count >= 3,
        ) { Text("Distribute V") }
    }
    Text("Mirror", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onMirror(PlanAxis.Vertical) }) { Text("Flip L↔R") }
        TextButton(onClick = { onMirror(PlanAxis.Horizontal) }) { Text("Flip T↔B") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onGroup, enabled = canGroup) { Text("Group") }
        TextButton(onClick = onUngroup, enabled = canUngroup) { Text("Ungroup") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        TextButton(onClick = onCopy) { Text("Copy") }
        TextButton(onClick = onDuplicate) { Text("Duplicate") }
        TextButton(onClick = onPaste, enabled = hasClipboard) { Text("Paste") }
    }
    DeleteRow(onDelete)
}

@Composable
private fun DimensionPropertyBody(
    dim: com.homedesign.android.domain.model.DimensionLine,
    unitSystem: UnitSystem,
    onDimensionLength: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    val lengthCm = hypot(dim.xEnd - dim.xStart, dim.yEnd - dim.yStart)
    Text("Dimension", style = HdTheme.typography.titleMedium, color = HdTheme.colors.ink)
    NumberRow(
        label = "Length",
        valueCm = lengthCm,
        onChangeCm = onDimensionLength,
        minCm = 1.0,
        maxCm = 10_000.0,
        unit = unitSystem,
        stepCm = 1.0,
    )
    DeleteRow(onDelete)
}

@Composable
private fun WallPropertyBody(
    wall: com.homedesign.android.domain.model.Wall,
    unitSystem: UnitSystem,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onWallLength: (Double) -> Unit,
    onWallHeight: (Double) -> Unit,
    onWallSideColor: (side: String, hex: String) -> Unit,
    onWallSidePreset: (side: String, preset: TexturePreset) -> Unit,
    onClearWallSideTexture: (side: String) -> Unit,
    onWallPattern: (hatched: Boolean) -> Unit,
    onWallGlass: (glass: Boolean) -> Unit,
    onStraightenWall: () -> Unit,
    onAddCurvePoint: () -> Unit,
    onWallBaseboardEnabled: (side: String, enabled: Boolean) -> Unit,
    onWallBaseboardHeight: (side: String, heightCM: Double) -> Unit,
    onWallBaseboardThickness: (side: String, thicknessCM: Double) -> Unit,
    onFormatPainter: () -> Unit,
    onImportWallTexture: (side: String) -> Unit,
    onAddWallDimension: () -> Unit,
    onDelete: () -> Unit,
) {
    var finishSide by remember(wall.id) { mutableStateOf("left") }
    val lengthCm = hypot(wall.endX - wall.startX, wall.endY - wall.startY)
    val curved = ArcWallGeometry.isCurved(wall)
    val breakCount = wall.curveProfile?.breaks?.size ?: 0
    val hatched = WallStyleMutation.isHatched(wall)
    val glass = WallStyleMutation.isGlass(wall)
    val sideColor = if (finishSide == "right") wall.rightSideColor else wall.leftSideColor
    val sideTexture = if (finishSide == "right") wall.rightSideTexture else wall.leftSideTexture
    val sideBaseboard =
        if (finishSide == "right") wall.rightSideBaseboard else wall.leftSideBaseboard
    val curveDeg = ((wall.arcExtent ?: 0.0) * 180.0 / kotlin.math.PI).roundToInt()

    Text("Wall", style = HdTheme.typography.titleMedium, color = HdTheme.colors.ink)
    NumberRow(
        label = "Length",
        valueCm = lengthCm,
        onChangeCm = onWallLength,
        minCm = 1.0,
        maxCm = 4000.0,
        unit = unitSystem,
        stepCm = 5.0,
        enabled = !curved,
    )
    NumberRow(
        label = "Height",
        valueCm = wall.height,
        onChangeCm = onWallHeight,
        minCm = 20.0,
        maxCm = 600.0,
        unit = unitSystem,
        stepCm = 5.0,
    )
    Text(
        text = "Thick ${UnitFormat.length(wall.thickness, unitSystem)}",
        style = HdTheme.typography.bodySmall,
        color = HdTheme.colors.stone,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = kotlin.math.abs(wall.thickness - interiorThicknessCM) < 0.1,
            onClick = onInterior,
            label = { Text("Interior ${UnitFormat.length(interiorThicknessCM, unitSystem)}") },
        )
        FilterChip(
            selected = kotlin.math.abs(wall.thickness - exteriorThicknessCM) < 0.1,
            onClick = onExterior,
            label = { Text("Exterior ${UnitFormat.length(exteriorThicknessCM, unitSystem)}") },
        )
    }

    Text("Curve", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            when {
                breakCount > 0 -> "$breakCount break${if (breakCount == 1) "" else "s"} (drag on plan)"
                curved -> "$curveDeg° (drag bow on plan)"
                else -> "Straight"
            },
            style = HdTheme.typography.bodySmall,
            color = HdTheme.colors.architectGray,
        )
        if (curved || breakCount > 0) {
            TextButton(onClick = onStraightenWall) { Text("Straighten") }
        }
    }
    TextButton(onClick = onAddCurvePoint) { Text("Add curve point") }
    TextButton(onClick = onAddWallDimension) { Text("Add dimension") }

    Text("Finish", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = finishSide == "left",
            onClick = { finishSide = "left" },
            label = { Text("Left") },
        )
        FilterChip(
            selected = finishSide == "right",
            onClick = { finishSide = "right" },
            label = { Text("Right") },
        )
    }
    Text("Paint", style = HdTheme.typography.labelSmall, color = HdTheme.colors.stone)
    PaintRow(
        selectedHex = sideColor,
        onPick = { onWallSideColor(finishSide, it) },
    )
    Text("Texture", style = HdTheme.typography.labelSmall, color = HdTheme.colors.stone)
    PresetGrid(
        presets = WALL_PRESETS,
        selectedHandle = sideTexture?.image,
        onPick = { onWallSidePreset(finishSide, it) },
    )
    TextButton(onClick = { onImportWallTexture(finishSide) }) { Text("Import photo…") }
    if (sideTexture != null) {
        AppliedTextureRow(
            label = sideTexture.name ?: sideTexture.image ?: "Texture",
            onRemove = { onClearWallSideTexture(finishSide) },
        )
    }

    Text("Baseboard", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    FilterChip(
        selected = sideBaseboard != null,
        onClick = { onWallBaseboardEnabled(finishSide, sideBaseboard == null) },
        label = { Text(if (sideBaseboard != null) "On" else "Off") },
    )
    if (sideBaseboard != null) {
        NumberRow(
            label = "BB height",
            valueCm = sideBaseboard.height ?: 7.0,
            onChangeCm = { onWallBaseboardHeight(finishSide, it) },
            minCm = 2.0,
            maxCm = 200.0,
            unit = unitSystem,
            stepCm = 1.0,
        )
        NumberRow(
            label = "BB thick",
            valueCm = sideBaseboard.thickness ?: 1.6,
            onChangeCm = { onWallBaseboardThickness(finishSide, it) },
            minCm = 0.5,
            maxCm = 20.0,
            unit = unitSystem,
            stepCm = 0.5,
        )
    }

    Text("Material", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !hatched && !glass,
            onClick = {
                if (hatched) onWallPattern(false)
                if (glass) onWallGlass(false)
            },
            label = { Text("Solid") },
        )
        FilterChip(
            selected = hatched,
            onClick = { onWallPattern(!hatched) },
            label = { Text("Hatch") },
        )
        FilterChip(
            selected = glass,
            onClick = { onWallGlass(!glass) },
            label = { Text("Glass") },
        )
    }

    TextButton(onClick = onFormatPainter) { Text("Format painter") }

    DeleteRow(onDelete)
}

@Composable
private fun RoomPropertyBody(
    room: com.homedesign.android.domain.model.Room,
    unitSystem: UnitSystem,
    onRename: (String) -> Unit,
    onFloorColor: (String) -> Unit,
    onFloorPreset: (TexturePreset) -> Unit,
    onClearFloorTexture: () -> Unit,
    onImportFloorTexture: () -> Unit,
    onCeilingColor: (String) -> Unit,
    onCeilingPreset: (TexturePreset) -> Unit,
    onClearCeilingTexture: () -> Unit,
    onRoomBorder: (BorderKind) -> Unit,
    onCeilingVisible: (Boolean) -> Unit,
    onCeilingStyle: (CeilingStyle?) -> Unit,
    onRoomSize: (widthCM: Double?, depthCM: Double?) -> Unit,
    onStageRoom: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(room.id, room.name) { mutableStateOf(room.name.orEmpty()) }
    val areaM2 = RoomGeometry.polygonArea(room) / 10_000.0
    val size = RoomSizeMutation.boundingSize(room)
    val border = RoomStyleMutation.borderKindOf(room)
    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            onRename(it)
        },
        label = { Text("Name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Text(
        text = "Area ${UnitFormat.area(areaM2, unitSystem)}",
        style = HdTheme.typography.bodyMedium,
        color = HdTheme.colors.stone,
    )

    Text("Size", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    NumberRow(
        label = "Width",
        valueCm = size.width,
        onChangeCm = { onRoomSize(it, null) },
        minCm = 30.0,
        maxCm = 10_000.0,
        unit = unitSystem,
        stepCm = 5.0,
    )
    NumberRow(
        label = "Depth",
        valueCm = size.depth,
        onChangeCm = { onRoomSize(null, it) },
        minCm = 30.0,
        maxCm = 10_000.0,
        unit = unitSystem,
        stepCm = 5.0,
    )

    Text("Floor border", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = border == BorderKind.None,
            onClick = { onRoomBorder(BorderKind.None) },
            label = { Text("None") },
        )
        FilterChip(
            selected = border == BorderKind.Tile,
            onClick = { onRoomBorder(BorderKind.Tile) },
            label = { Text("Tile") },
        )
        FilterChip(
            selected = border == BorderKind.Stone,
            onClick = { onRoomBorder(BorderKind.Stone) },
            label = { Text("Stone") },
        )
        FilterChip(
            selected = border == BorderKind.Walnut,
            onClick = { onRoomBorder(BorderKind.Walnut) },
            label = { Text("Walnut") },
        )
    }

    Text("Floor", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Text("Paint", style = HdTheme.typography.labelSmall, color = HdTheme.colors.stone)
    PaintRow(selectedHex = room.floorColor, onPick = onFloorColor)
    Text("Presets", style = HdTheme.typography.labelSmall, color = HdTheme.colors.stone)
    PresetGrid(
        presets = FLOOR_PRESETS,
        selectedHandle = room.floorTexture?.image,
        onPick = onFloorPreset,
    )
    TextButton(onClick = onImportFloorTexture) { Text("Import photo…") }
    if (room.floorTexture != null) {
        AppliedTextureRow(
            label = room.floorTexture.name ?: room.floorTexture.image ?: "Floor texture",
            onRemove = onClearFloorTexture,
        )
    }

    TextButton(onClick = onStageRoom) { Text("Stage this room") }

    Text("Ceiling", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = room.ceilingVisible,
            onClick = { onCeilingVisible(!room.ceilingVisible) },
            label = { Text(if (room.ceilingVisible) "Shown" else "Hidden") },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val style = room.ceilingStyle ?: CeilingStyle.Flat
        FilterChip(
            selected = style == CeilingStyle.Flat || room.ceilingStyle == null,
            onClick = { onCeilingStyle(CeilingStyle.Flat) },
            label = { Text("Flat") },
        )
        FilterChip(
            selected = style == CeilingStyle.Coffered,
            onClick = { onCeilingStyle(CeilingStyle.Coffered) },
            label = { Text("Coffered") },
        )
        FilterChip(
            selected = style == CeilingStyle.StainedGlass,
            onClick = { onCeilingStyle(CeilingStyle.StainedGlass) },
            label = { Text("Stained glass") },
        )
        FilterChip(
            selected = style == CeilingStyle.Dropped,
            onClick = { onCeilingStyle(CeilingStyle.Dropped) },
            label = { Text("Dropped") },
        )
    }
    Text("Paint", style = HdTheme.typography.labelSmall, color = HdTheme.colors.stone)
    PaintRow(selectedHex = room.ceilingColor, onPick = onCeilingColor)
    Text("Presets", style = HdTheme.typography.labelSmall, color = HdTheme.colors.stone)
    PresetGrid(
        presets = FLOOR_PRESETS,
        selectedHandle = room.ceilingTexture?.image,
        onPick = onCeilingPreset,
    )
    if (room.ceilingTexture != null) {
        AppliedTextureRow(
            label = room.ceilingTexture.name ?: room.ceilingTexture.image ?: "Ceiling texture",
            onRemove = onClearCeilingTexture,
        )
    }

    DeleteRow(onDelete)
}

@Composable
private fun OpeningPropertyBody(
    opening: com.homedesign.android.domain.model.HomeDoorOrWindow,
    unitSystem: UnitSystem,
    hasClipboard: Boolean,
    onOpeningWidth: (Double) -> Unit,
    onFlipHinge: () -> Unit,
    onFlipSwing: () -> Unit,
    onToggleOpen: () -> Unit = {},
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Text(
        opening.piece.name?.trim()?.ifBlank { null } ?: "Opening",
        style = HdTheme.typography.titleMedium,
        color = HdTheme.colors.ink,
    )
    NumberRow(
        label = "Width",
        valueCm = opening.piece.width,
        onChangeCm = onOpeningWidth,
        minCm = 30.0,
        maxCm = 400.0,
        unit = unitSystem,
        stepCm = 1.0,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onFlipHinge) { Text("Flip hinge") }
        TextButton(onClick = onFlipSwing) { Text("Flip swing") }
    }
    TextButton(onClick = onToggleOpen) {
        Text(if (opening.isOpen) "Close" else "Open")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        TextButton(onClick = onCopy) { Text("Copy") }
        TextButton(onClick = onDuplicate) { Text("Duplicate") }
        TextButton(onClick = onPaste, enabled = hasClipboard) { Text("Paste") }
    }
    DeleteRow(onDelete)
}

@Composable
private fun FurniturePropertyBody(
    piece: com.homedesign.android.domain.model.HomePieceOfFurniture,
    unitSystem: UnitSystem,
    hasClipboard: Boolean,
    onRename: (String) -> Unit,
    onFurnitureWidth: (Double) -> Unit,
    onFurnitureDepth: (Double) -> Unit,
    onFurnitureAngleDeg: (Double) -> Unit,
    onMirror: (PlanAxis) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDuplicate: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(piece.id, piece.name) { mutableStateOf(piece.name.orEmpty()) }
    val deg = round(piece.angle * 180.0 / kotlin.math.PI)
    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            onRename(it)
        },
        label = { Text("Name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    NumberRow(
        label = "Width",
        valueCm = piece.width,
        onChangeCm = onFurnitureWidth,
        minCm = 1.0,
        maxCm = 10_000.0,
        unit = unitSystem,
    )
    NumberRow(
        label = "Depth",
        valueCm = piece.depth,
        onChangeCm = onFurnitureDepth,
        minCm = 1.0,
        maxCm = 10_000.0,
        unit = unitSystem,
    )
    NumberRow(
        label = "Angle °",
        valueCm = deg,
        onChangeCm = onFurnitureAngleDeg,
        minCm = -360.0,
        maxCm = 360.0,
        unit = null,
        stepCm = 1.0,
    )
    Text("Mirror", style = HdTheme.typography.labelMedium, color = HdTheme.colors.stone)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onMirror(PlanAxis.Vertical) }) { Text("Flip L↔R") }
        TextButton(onClick = { onMirror(PlanAxis.Horizontal) }) { Text("Flip T↔B") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        TextButton(onClick = onCopy) { Text("Copy") }
        TextButton(onClick = onDuplicate) { Text("Duplicate") }
        TextButton(onClick = onPaste, enabled = hasClipboard) { Text("Paste") }
    }
    TextButton(onClick = onReplace) { Text("Replace with…") }
    DeleteRow(onDelete)
}

@Composable
private fun DeleteRow(onDelete: () -> Unit) {
    TextButton(onClick = onDelete) {
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = HdTheme.colors.destructive)
        Spacer(Modifier.width(8.dp))
        Text("Delete", color = HdTheme.colors.destructive)
    }
}
