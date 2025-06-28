package blue.eaudouce.waybiker

import android.util.Log
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.toCameraOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class WaybikerMap(mapView: MapView) {

    private val mapView = mapView
    private val annotationMgr: PolylineAnnotationManager
    private val pointAnnotationMgr: CircleAnnotationManager

    // Map of street portions' extremity nodes to their corresponding street portion.
    // There will typically be two keys to a single street portion since it has two extremities.
    private val streetPortions = HashMap<Long, StreetPortion>()

    init {
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(-73.5824535409464, 45.49576954424193))
                .pitch(0.0)
                .zoom(15.0)
                .bearing(0.0)
                .build()
        )

        annotationMgr = mapView.annotations.createPolylineAnnotationManager()
        pointAnnotationMgr = mapView.annotations.createCircleAnnotationManager()

        mapView.mapboxMap.addOnMoveListener(object : OnMoveListener {
            override fun onMove(detector: MoveGestureDetector): Boolean {
                return false
            }

            override fun onMoveBegin(detector: MoveGestureDetector) {
            }

            override fun onMoveEnd(detector: MoveGestureDetector) {
                updateStreetGeometry()
            }
        })
    }

    private fun updateStreetGeometry() {
        val bounds = mapView.mapboxMap.coordinateBoundsForCamera(mapView.mapboxMap.cameraState.toCameraOptions())

        val query = String.format("""
            [out:json][timeout:25][bbox:%.4f,%.4f,%.4f,%.4f];
            (
              way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)"];
            );
            out body;
            >;
            out skel qt;
        """, bounds.south(), bounds.west(), bounds.north(), bounds.east()).trimIndent()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val requestBody = "data=$encodedQuery"
            .toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Overpass", "Request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                annotationMgr.deleteAll()
                pointAnnotationMgr.deleteAll()

                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val elements = json.getJSONArray("elements")

                // Find node positions
                val nodePositions = HashMap<Long, Point>()
                for (i in 0 until elements.length()) {
                    val element = elements.getJSONObject(i)
                    try {
                        val elementType = element.getString("type")
                        if (elementType.equals("node")) {
                            val id = element.getLong("id")
                            val lat = element.getDouble("lat")
                            val lon = element.getDouble("lon")
                            nodePositions[id] = Point.fromLngLat(lon, lat)
                        }
                    }
                    catch (_: Exception) { }
                }

                for (i in 0 until elements.length()) {
                    val element = elements.getJSONObject(i)
                    try {
                        val elementType = element.getString("type")
                        if (elementType.equals("way")) {
                            val points = ArrayList<Point>()
                            val nodeIds = ArrayList<Long>()

                            val nodes = element.getJSONArray("nodes")
                            for (j in 0 until nodes.length()) {
                                val nodeId = nodes.getLong(j)
                                val node = nodePositions[nodeId]
                                if (node != null) {
                                    points.add(node)
                                }
                            }

                            val annotationOptions = PolylineAnnotationOptions()
                                .withPoints(points)
                                .withLineColor("#ee4e8b")
                                .withLineWidth(10.0)
                            annotationMgr.create(annotationOptions)
                            annotationMgr.addClickListener { annotation ->
                                annotationMgr.delete(annotation)
                                false
                            }

                            for (j in 0 until points.size) {
                                val pointAnnotationOptions = CircleAnnotationOptions()
                                    .withPoint(points[j])
                                    .withCircleRadius(5.0)
                                    .withCircleColor("#4eee8b")
                                pointAnnotationMgr.create(pointAnnotationOptions)
                            }
                        }
                    }
                    catch (_: Exception) { }
                }
            }
        })
    }
}