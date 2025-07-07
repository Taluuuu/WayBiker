package blue.eaudouce.waybiker

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import blue.eaudouce.waybiker.map.StreetBit
import blue.eaudouce.waybiker.map.WaybikerMap
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager

class HomeFragment(
    private val waybikerMap: WaybikerMap
) : Fragment(R.layout.fragment_home) {

    private val mapAction = MapAction_RateStreet(waybikerMap)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rateStreetButton = view.findViewById<FloatingActionButton>(R.id.fab_rate_street)
        rateStreetButton.setOnClickListener {
            mapAction.start()
        }
    }
}