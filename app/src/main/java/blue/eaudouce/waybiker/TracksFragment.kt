package blue.eaudouce.waybiker

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.home.TrackView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

            if (tracks != null) {
                context?.let { ctx ->
                    val trackListView = view?.findViewById<LinearLayout>(R.id.ll_track_list)
                    if (trackListView != null) {
                        for (track in tracks) {
                            val trackView = TrackView(ctx)
                            trackView.applyTrack(track)
                            trackListView.addView(trackView)
                        }
                    }
                }
            }
        }
    }
}