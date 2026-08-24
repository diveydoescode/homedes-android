package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.CeilingStyle
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.WallTexture
import com.homedesign.android.domain.textures.BORDER_PRESETS
import com.homedesign.android.domain.textures.BORDER_WIDTH_CM
import com.homedesign.android.domain.textures.findPreset
import com.homedesign.android.domain.textures.textureFromPreset

enum class BorderKind { None, Tile, Stone, Walnut }

/**
 * Floor / ceiling / border styling. Port of web `RoomStyleMutation.ts`.
 * A non-nil colour clears that surface's texture.
 */
object RoomStyleMutation {
    private fun update(
        rooms: List<Room>,
        roomID: String,
        transform: (Room) -> Room,
    ): List<Room> {
        if (rooms.none { it.id == roomID }) return rooms.toList()
        return rooms.map { room -> if (room.id == roomID) transform(room) else room }
    }

    fun setFloorColor(roomID: String, color: String?, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { room ->
            room.copy(
                floorColor = color,
                floorTexture = if (color != null) null else room.floorTexture,
            )
        }

    fun setCeilingColor(roomID: String, color: String?, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { room ->
            room.copy(
                ceilingColor = color,
                ceilingTexture = if (color != null) null else room.ceilingTexture,
            )
        }

    fun setFloorTexture(roomID: String, texture: WallTexture, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { it.copy(floorTexture = texture, floorColor = null) }

    fun setCeilingTexture(roomID: String, texture: WallTexture, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { it.copy(ceilingTexture = texture, ceilingColor = null) }

    fun clearFloorTexture(roomID: String, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { it.copy(floorTexture = null) }

    fun clearCeilingTexture(roomID: String, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { it.copy(ceilingTexture = null) }

    fun setCeilingVisible(roomID: String, visible: Boolean, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { it.copy(ceilingVisible = visible) }

    fun setCeilingStyle(roomID: String, style: CeilingStyle?, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { room ->
            room.copy(ceilingStyle = if (style == CeilingStyle.Flat) null else style)
        }

    fun setBorder(roomID: String, kind: BorderKind, rooms: List<Room>): List<Room> =
        update(rooms, roomID) { room ->
            if (kind == BorderKind.None) {
                room.copy(
                    borderWidthCM = null,
                    borderTexture = null,
                    borderColor = null,
                )
            } else {
                val key = when (kind) {
                    BorderKind.Tile -> "tile"
                    BorderKind.Stone -> "stone"
                    BorderKind.Walnut -> "walnut"
                    BorderKind.None -> error("unreachable")
                }
                val handle = BORDER_PRESETS[key]
                val preset = handle?.let { findPreset(it) }
                room.copy(
                    borderWidthCM = BORDER_WIDTH_CM,
                    borderTexture = preset?.let { textureFromPreset(it) },
                    borderColor = null,
                )
            }
        }

    fun borderKindOf(room: Room): BorderKind =
        when (room.borderTexture?.image) {
            "preset:tile-grey" -> BorderKind.Tile
            "preset:stone-rect" -> BorderKind.Stone
            "preset:wood-walnut" -> BorderKind.Walnut
            else -> BorderKind.None
        }
}
