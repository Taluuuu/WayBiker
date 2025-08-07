package blue.eaudouce.waybiker

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class StreetRating(
    val start: Long,
    val end: Long,
    val rating: Short,
    val user_id: String,
    val tile_x: Int,
    val tile_y: Int,
    val timestamp: Instant?
)

@Serializable
data class Track(
    val track_id: String,
    val name: String,
    val user_id: String
)

@Serializable
data class TrackPoint(
    val track_id: String,
    val point_index: Int,
    val point: Long
)