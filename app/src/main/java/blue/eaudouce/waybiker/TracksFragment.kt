package blue.eaudouce.waybiker

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.home.TrackView
import blue.eaudouce.waybiker.util.MAX_STREET_RATING
import blue.eaudouce.waybiker.util.calcSegmentScore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

class TracksFragment : Fragment(R.layout.fragment_tracks) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = SupabaseInstance.client.auth.currentUserOrNull() ?: return

        lifecycleScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                try {
                    SupabaseInstance.client
                        .from("tracks")
                        .select {
                            filter {
                                eq("user_id", user.id)
                            }
                        }.decodeList<Track>()
                } catch (e: Exception) {
                    null
                }
            }

            if (tracks == null)
                return@launch

            val trackListView = view?.findViewById<LinearLayout>(R.id.ll_track_list)
            if (trackListView == null)
                return@launch

            context?.let { ctx ->
                for (track in tracks) {
                    val trackPoints = fetchTrackPoints(track.track_id)
                    if (trackPoints == null)
                        continue

                    val trackScore = calcTrackScore(trackPoints)

                    val trackView = TrackView(ctx)
                    trackView.applyTrack(track, trackScore,
                        {
                            view?.findViewById<ConstraintLayout>(R.id.cl_track_list)?.visibility = View.INVISIBLE


                        },
                        {

                        }
                    )
                    trackListView.addView(trackView)
                }
            }

        }

    }

    private suspend fun fetchTrackPoints(trackId : String): List<TrackPoint>? {
        return withContext(Dispatchers.IO) {
            try {
                SupabaseInstance.client
                    .from("track_points")
                    .select {
                        filter {
                            eq("track_id", trackId)
                        }
                    }.decodeList<TrackPoint>()
            } catch (e: Exception) {
                null
            }
        }
    }

    // The track score is that of its lowest segment.
    private suspend fun calcTrackScore(trackPoints: List<TrackPoint>): Float? {
        var minSegmentScore = MAX_STREET_RATING.toFloat()
        var hasAnyScoredSegment = false

        withContext(Dispatchers.IO) {
            trackPoints.zipWithNext().forEach { (start, end) ->
                val ratings = try {
                    SupabaseInstance.client
                        .from("street_ratings")
                        .select {
                            filter {
                                and {
                                    eq("start", start.point)
                                    eq("end", end.point)
                                }
                            }
                        }.decodeList<StreetRating>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    listOf()
                }

                val score = calcSegmentScore(ratings)
                if (score != null) {
                    minSegmentScore = min(minSegmentScore, score)
                    hasAnyScoredSegment = true
                }
            }
        }

        return if (hasAnyScoredSegment) minSegmentScore else null
    }
}