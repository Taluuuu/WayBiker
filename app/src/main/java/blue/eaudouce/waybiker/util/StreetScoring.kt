package blue.eaudouce.waybiker.util

import android.graphics.Color
import blue.eaudouce.waybiker.StreetRating
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.monthsUntil
import kotlin.math.pow

const val MAX_STREET_RATING = 5
const val NUM_CONSIDERED_RATINGS = 5

fun calcStreetColor(rating: Float?): String {
    if (rating == null)
        return "#9c9c9c"

    val streetColorValues = arrayListOf(
        Pair(0.0f, Color.RED),
        Pair(0.5f, Color.YELLOW),
        Pair(1.0f, Color.GREEN)
    )

    val alpha = rating / MAX_STREET_RATING // TODO: Magic number bad
    val colorRange = streetColorValues
        .zipWithNext()
        .find { (minColor, maxColor) ->
            alpha >= minColor.first && alpha <= maxColor.first
        } ?: return "#9c9c9c"

    val (range0, range1) = colorRange
    val (a0, c0) = range0
    val (a1, c1) = range1

    val withinRangeAlpha = (alpha - a0) / (a1 - a0)

    val r0 = Color.red(c0)
    val g0 = Color.green(c0)
    val b0 = Color.blue(c0)

    val r1 = Color.red(c1)
    val g1 = Color.green(c1)
    val b1 = Color.blue(c1)

    val finalRed = (r0 + (r1 - r0) * withinRangeAlpha) / 255.0f
    val finalGreen = (g0 + (g1 - g0) * withinRangeAlpha) / 255.0f
    val finalBlue = (b0 + (b1 - b0) * withinRangeAlpha) / 255.0f

    return String.format("#%06X", 0xFFFFFF and Color.rgb(finalRed, finalGreen, finalBlue))
}

fun calcSegmentScore(ratings: List<StreetRating>): Float? {
    if (ratings.isEmpty())
        return null

    val consideredRatings = ratings
        .sortedBy { it.timestamp }
        .takeLast(NUM_CONSIDERED_RATINGS)

    var totalWeight = 0.0f
    var weightedSum = 0.0f

    // Every new rating is worth double the last.
    for ((i, rating) in consideredRatings.withIndex()) {
        val weight = 2.0f.pow(i)
        totalWeight += weight
        weightedSum += rating.rating * weight
    }

    return if (totalWeight > 0) weightedSum / totalWeight else null
}

fun isRatingFading(ratings: List<StreetRating>): Boolean {
    return ratings.any {
        (it.timestamp?.monthsUntil(Clock.System.now(), TimeZone.currentSystemDefault()) ?: 1) > 0
    }
}