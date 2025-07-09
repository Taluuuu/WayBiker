package blue.eaudouce.waybiker.home

import android.content.Context
import android.view.View.inflate
import android.widget.FrameLayout
import android.widget.RatingBar
import blue.eaudouce.waybiker.R

class StreetRatingBarView(context: Context) : FrameLayout(context) {
    private val ratingBar: RatingBar
    init {
        inflate(context, R.layout.view_street_rating_bar, this)
        ratingBar = findViewById(R.id.rb_street_rating)
    }

    val rating get() = ratingBar.rating
}