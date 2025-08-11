package blue.eaudouce.waybiker.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.Location
import androidx.core.graphics.toColorInt
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.util.MapTiling
import blue.eaudouce.waybiker.util.MapTiling.coordinateBoundsToTileBounds
import blue.eaudouce.waybiker.util.MapTiling.forEachTile
import blue.eaudouce.waybiker.util.MapTiling.toCoordinateBounds
import com.google.gson.JsonObject
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.toCameraOptions
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import kotlin.math.cos
import kotlin.math.sqrt

class WaybikerMap(
    val mapView: MapView,
    val context: Context?
) {
    private val locationAnnotationMgr: CircleAnnotationManager
    private var locationAnnotation: CircleAnnotation? = null
    private var locationShadowAnnotation: CircleAnnotation? = null

    private val testAnnotationMgr: PolygonAnnotationManager
    private val testBorderAnnotationMgr: PolylineAnnotationManager

    val mapGraph: MapGraph
    var onLocationUpdated: ((Point) -> Unit)? = null
    var onClickedMap: ((Point) -> Unit)? = null
    private var isFirstLocationUpdate = true

    init {
//        streetAnnotationMgr = mapView.annotations.createPolylineAnnotationManager()
        locationAnnotationMgr = mapView.annotations.createCircleAnnotationManager()
        testAnnotationMgr = mapView.annotations.createPolygonAnnotationManager()
        testBorderAnnotationMgr = mapView.annotations.createPolylineAnnotationManager()

        mapView.mapboxMap.subscribeCameraChanged { refreshMap() }
        mapView.mapboxMap.addOnMapClickListener { point ->
            onClickedMap?.invoke(point)
            true
        }

        mapGraph = MapGraph(mapView)
    }

    fun onLocationUpdated(location: Location) {
        val point = Point.fromLngLat(location.longitude, location.latitude)

        if (isFirstLocationUpdate) {

            val shadowOptions = CircleAnnotationOptions()
                .withPoint(point)
                .withCircleRadius(25.0)
                .withCircleColor(Color.BLACK)
                .withCircleBlur(1.0)

            locationShadowAnnotation = locationAnnotationMgr.create(shadowOptions)

            val puckOptions = CircleAnnotationOptions()
                .withPoint(point)
                .withCircleRadius(12.0)
                .withCircleColor("#3468ed".toColorInt())
                .withCircleStrokeColor(Color.WHITE)
                .withCircleStrokeWidth(5.0)

            locationAnnotation = locationAnnotationMgr.create(puckOptions)

            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(point)
                    .pitch(0.0)
                    .zoom(16.0)
                    .bearing(0.0)
                    .build()
            )

            isFirstLocationUpdate = false
        }

        locationAnnotation?.let {
            it.point = point
            locationAnnotationMgr.update(it)
        }

        locationShadowAnnotation?.let {
            it.point = point
            locationAnnotationMgr.update(it)
        }

        onLocationUpdated?.invoke(point)

        refreshMap()
    }

    @SuppressLint("DefaultLocale")
    fun refreshMap() {
        if (isFirstLocationUpdate) {
            return
        }

        val coordBounds = mapView.mapboxMap.coordinateBoundsForCamera(mapView.mapboxMap.cameraState.toCameraOptions())
        val tileBounds = coordinateBoundsToTileBounds(coordBounds)

        // Include neighbouring tiles
        tileBounds.min.x -= 1
        tileBounds.min.y += 1
        tileBounds.max.x += 1
        tileBounds.max.y -= 1

//        tileBounds.min.x = 19372
//        tileBounds.max.x = 19373
//        tileBounds.min.y = 23447
//        tileBounds.max.y = 23446

        val tilesToLoad = ArrayList<MapTiling.MapTile>()
        tileBounds.forEachTile { tilesToLoad.add(it) }
        mapGraph.queueTileLoads(tilesToLoad)

//        testAnnotationMgr.deleteAll()
//        testBorderAnnotationMgr.deleteAll()
//        for (i in tileBounds.min.x until tileBounds.max.x) {
//            for (j in tileBounds.min.y downTo tileBounds.max.y + 1) {
//                val tile = MapTiling.MapTile(i, j)
//
//                val bounds = tile.toCoordinateBounds()
//                val points = listOf(bounds.northwest(), bounds.northeast, bounds.southeast(), bounds.southwest, bounds.northwest())
//
//                val polygonOptions = PolygonAnnotationOptions()
//                    .withPoints(listOf(points))
//                    .withFillColor(if (mapGraph.areNeighbourTilesLoaded(tile)) Color.GREEN else Color.RED)
//                    .withFillOpacity(0.1)
//                testAnnotationMgr.create(polygonOptions)
//
//                val polylineOptions = PolylineAnnotationOptions()
//                    .withPoints(points)
//                    .withLineWidth(10.0)
//                    .withLineColor(Color.BLACK)
//                testBorderAnnotationMgr.create(polylineOptions)
//            }
//        }
    }
}