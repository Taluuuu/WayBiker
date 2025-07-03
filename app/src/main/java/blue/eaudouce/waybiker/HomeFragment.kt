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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rateStreetButton = view.findViewById<FloatingActionButton>(R.id.fab_rate_street)
        rateStreetButton.setOnClickListener {
            waybikerMap.onClickedStreet = { clickedStreet: StreetBit ->
                val nodePositions = clickedStreet.nodeIds.mapNotNull {
                    nodeId -> waybikerMap.nodePositions[nodeId]
                }

                val annotationMgr = waybikerMap.mapView.annotations.createPolylineAnnotationManager()
                val annotationOptions = PolylineAnnotationOptions()
                    .withPoints(nodePositions)
                    .withLineWidth(20.0)
                    .withLineColor("#439c32")
                annotationMgr.create(annotationOptions)
            }
        }
    }
}