package blue.eaudouce.waybiker

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.home.HomeFragment
import blue.eaudouce.waybiker.home.TrackView
import blue.eaudouce.waybiker.map.MapGraph
import blue.eaudouce.waybiker.map.WaybikerMap
import blue.eaudouce.waybiker.util.MAX_STREET_RATING
import blue.eaudouce.waybiker.util.calcSegmentScore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

class TracksFragment : Fragment(R.layout.fragment_tracks) {
    private lateinit var waybikerMap: WaybikerMap

    companion object {
        fun newInstance(waybikerMap: WaybikerMap) : TracksFragment {
            val instance = TracksFragment()
            instance.waybikerMap = waybikerMap
            return instance
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reloadTracks()
    }

    override fun onPause() {
        super.onPause()

        view?.findViewById<ConstraintLayout>(R.id.cl_track_list)?.visibility = View.VISIBLE
        view?.findViewById<ConstraintLayout>(R.id.cl_viewing_track)?.visibility = View.INVISIBLE
        waybikerMap.mapGraph.clearHighlightedSegments()
    }

    private fun reloadTracks() {
        view?.findViewById<LinearLayout>(R.id.ll_track_list)?.removeAllViews()

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
                            view?.findViewById<ConstraintLayout>(R.id.cl_viewing_track)?.visibility = View.VISIBLE
                            view?.findViewById<TextView>(R.id.tv_viewing_track_name)?.text = track.name
                            val segments = trackPoints.zipWithNext().map { MapGraph.LinkKey.of(it.first.point, it.second.point) }
                            waybikerMap.mapGraph.highlightSegments(segments)

                            view?.findViewById<Button>(R.id.btn_viewing_track_back)?.setOnClickListener {
                                view?.findViewById<ConstraintLayout>(R.id.cl_track_list)?.visibility = View.VISIBLE
                                view?.findViewById<ConstraintLayout>(R.id.cl_viewing_track)?.visibility = View.INVISIBLE
                                waybikerMap.mapGraph.clearHighlightedSegments()
                            }
                        },
                        {
                            deleteTrack(track.track_id)
                        }
                    )
                    trackListView.addView(trackView)
                }
            }
        }
    }

    private fun deleteTrack(trackId: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    SupabaseInstance.client
                        .from("tracks")
                        .delete {
                            filter {
                                eq("track_id", trackId)
                            }
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            reloadTracks()
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
                val linkKey = MapGraph.LinkKey.of(start.point, end.point)
                val ratings = try {
                    SupabaseInstance.client
                        .from("street_ratings")
                        .select {
                            filter {
                                and {
                                    eq("start", linkKey.first)
                                    eq("end", linkKey.second)
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