package blue.eaudouce.waybiker.home

import android.content.Context
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import blue.eaudouce.waybiker.R
import blue.eaudouce.waybiker.Track
import blue.eaudouce.waybiker.util.calcStreetColor

class TrackView(context: Context) : FrameLayout(context) {
    init {
        inflate(context, R.layout.view_track, this)
    }

    fun applyTrack(track: Track, trackRating: Float?, onPressedView: () -> Unit, onPressedDelete: () -> Unit) {
        val nameView = findViewById<TextView>(R.id.tv_track_name)
        if (nameView == null)
            return

        val trackPill = findViewById<CardView>(R.id.cv_track_status_pill)
        if (trackPill == null)
            return

        val viewTrackBtn = findViewById<Button>(R.id.btn_view_track)
        if (viewTrackBtn == null)
            return

        val deleteTrackBtn = findViewById<Button>(R.id.btn_delete_track)
        if (deleteTrackBtn == null)
            return

        viewTrackBtn.setOnClickListener { onPressedView() }
        deleteTrackBtn.setOnClickListener { onPressedDelete() }

        trackPill.setCardBackgroundColor(calcStreetColor(trackRating).toColorInt())
        nameView.text = track.name
    }
}