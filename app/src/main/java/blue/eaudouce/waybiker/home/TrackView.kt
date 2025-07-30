package blue.eaudouce.waybiker.home

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import blue.eaudouce.waybiker.R
import blue.eaudouce.waybiker.Track

class TrackView(context: Context) : FrameLayout(context) {
    init {
        inflate(context, R.layout.view_track, this)
    }

    fun applyTrack(track: Track) {
        val nameView = findViewById<TextView>(R.id.tv_track_name)
        if (nameView == null)
            return

        nameView.text = track.name
    }
}