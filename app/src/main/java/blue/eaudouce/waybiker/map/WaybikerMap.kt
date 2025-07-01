package blue.eaudouce.waybiker.map

import android.util.Log
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
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
import org.json.JSONArray
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

    private fun getNodePositions(elements: JSONArray): HashMap<Long, Point> {

        val nodePositions = HashMap<Long, Point>()
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            try {
                val elementType = element.getString("type")
                if (!elementType.equals("node"))
                    continue

                val id = element.getLong("id")
                val lat = element.getDouble("lat")
                val lon = element.getDouble("lon")
                nodePositions[id] = Point.fromLngLat(lon, lat)
            }
            catch (_: Exception) { }
        }

        return nodePositions
    }

    private fun makeMapGraph(elements: JSONArray) {
        val nodePositions = getNodePositions(elements)

        // Each node is associated with the list of streets passing through.
        // If a node has one street, it is not an intersection. If it has more
        // than one, it is an intersection linking all the street names.
        val nodeUsers = HashMap<Long, HashSet<String>>()

        val streetNodes = HashMap<String, ArrayList<ArrayList<Long>>>()
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            try {
                val elementType = element.getString("type")
                if (!elementType.equals("way"))
                    continue

                val nodes = element.getJSONArray("nodes")
                val tags = element.getJSONObject("tags")
                val streetName = tags.getString("name")

                val wayNodeIds = ArrayList<Long>()
                for (j in 0 until nodes.length()) {
                    val nodeId = nodes.getLong(j)
                    nodeUsers.getOrPut(nodeId) { HashSet() }.add(streetName)
                    wayNodeIds.add(nodeId)
                }

                streetNodes.getOrPut(streetName) { ArrayList() }.add(wayNodeIds)
            }
            catch (_: Exception) { }
        }

        // Combine all consecutive nodes of a street into a single list (or multiple lists
        // in case of a divergence)
        val fullStreetNodes = HashMap<String, ArrayList<ArrayList<Long>>>()
        for ((streetName, nodes) in streetNodes)
        {
            while (nodes.isNotEmpty())
            {
                val nodesToPlace = nodes[0]
                val s1 = nodesToPlace.first()
                val e1 = nodesToPlace.last()

                var hasMoved = false
                for (i in 1 until nodes.size)
                {
                    val nodesToCompare = nodes[i]
                    val s2 = nodesToCompare.first()
                    if (e1 == s2)
                    {
                        nodesToPlace.addAll(nodesToCompare.subList(1, nodesToCompare.size))
                        nodes.removeAt(i)
                        hasMoved = true
                        break
                    }
                }

                for (i in 1 until nodes.size)
                {
                    val nodesToCompare = nodes[i]
                    val e2 = nodesToCompare.last()
                    if (s1 == e2)
                    {
                        nodesToPlace.addAll(0, nodesToCompare.subList(0, nodesToCompare.size - 1))
                        nodes.removeAt(i)
                        hasMoved = true
                        break
                    }
                }

                if (!hasMoved)
                {
                    fullStreetNodes.getOrPut(streetName) { ArrayList() }.add(nodesToPlace)
                    nodes.removeAt(0)
                }
            }
        }

        // TODO: Create graph
        for ((_, street) in fullStreetNodes)
        {
            for (nodes in street) {
                val polyLinePoints = ArrayList<Point>()
                for (nodeId in nodes) {
                    val nodePos = nodePositions[nodeId]
                    if (nodePos != null)
                        polyLinePoints.add(nodePos)
                }

                val annotationOptions = PolylineAnnotationOptions()
                    .withPoints(polyLinePoints)
                    .withLineColor("#ee4e8b")
                    .withLineWidth(10.0)
                annotationMgr.create(annotationOptions)
                annotationMgr.addClickListener { annotation ->
                    annotationMgr.delete(annotation)
                    false
                }

                for (nodeId in nodes) {
                    if (nodeUsers.getOrElse(nodeId) { HashSet() }.size > 1) {
                        val nodePos = nodePositions[nodeId]
                        if (nodePos != null) {
                            // This node is an intersection
                            val pointAnnotationOptions = CircleAnnotationOptions()
                                .withPoint(nodePos)
                                .withCircleRadius(5.0)
                                .withCircleColor("#4eee8b")

                            pointAnnotationMgr.create(pointAnnotationOptions)
                        }
                    }
                }
            }
        }
    }

    private fun updateStreetGeometry() {
        val bounds = mapView.mapboxMap.coordinateBoundsForCamera(mapView.mapboxMap.cameraState.toCameraOptions())

        val query = String.format("""
            [out:json][timeout:25][bbox:%.4f,%.4f,%.4f,%.4f];
            (
              way["highway"~"^(trunk|primary|secondary|tertiary|unclassified|residential)"];
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

                makeMapGraph(elements)
            }
        })
    }
}