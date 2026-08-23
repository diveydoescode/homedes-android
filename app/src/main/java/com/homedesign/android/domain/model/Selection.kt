package com.homedesign.android.domain.model

sealed interface Selection {
    data object None : Selection
    data class Wall(val id: String) : Selection
    data class Endpoint(val wallID: String, val atStart: Boolean) : Selection
    data class Opening(val id: String) : Selection
    data class OpeningHandle(val id: String, val side: Side) : Selection
    data class Furniture(val id: String) : Selection
    data class Room(val id: String) : Selection
    data class Annotation(val id: String, val isLabel: Boolean) : Selection
    data class MultiFurniture(val ids: List<String>) : Selection

    enum class Side { Start, End }
}
