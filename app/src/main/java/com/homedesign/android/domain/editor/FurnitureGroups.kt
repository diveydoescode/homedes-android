package com.homedesign.android.domain.editor

import com.homedesign.android.domain.model.Home
import java.util.UUID

/** F6 — group / ungroup. One fresh groupID across ≥2 pieces (web `groups.ts`). */
fun applyGroup(home: Home, ids: List<String>): Home {
    val unique = ids.distinct().filter { id -> home.furniture.any { it.id == id } }
    if (unique.size < 2) return home
    val gid = UUID.randomUUID().toString()
    val set = unique.toSet()
    return home.copy(
        furniture = home.furniture.map { p ->
            if (p.id in set) p.copy(groupID = gid) else p
        },
        furnitureRevision = home.furnitureRevision + 1,
    )
}

fun applyUngroup(home: Home, ids: List<String>): Home {
    val set = ids.toSet()
    var changed = false
    val furniture = home.furniture.map { p ->
        if (p.id !in set || p.groupID == null) p
        else {
            changed = true
            p.copy(groupID = null)
        }
    }
    if (!changed) return home
    return home.copy(furniture = furniture, furnitureRevision = home.furnitureRevision + 1)
}

fun sharedGroupID(home: Home, ids: List<String>): String? {
    if (ids.size < 2) return null
    val members = home.furniture.filter { it.id in ids }
    if (members.size < 2) return null
    val first = members.first().groupID ?: return null
    return if (members.all { it.groupID == first }) first else null
}
