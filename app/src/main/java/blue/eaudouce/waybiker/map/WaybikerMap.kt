package blue.eaudouce.waybiker.map

import android.annotation.SuppressLint
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.Annotation
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.OnCircleAnnotationDragListener
import com.mapbox.maps.plugin.annotation.generated.OnPolylineAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.OnPolylineAnnotationDragListener
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
import kotlin.math.cos
import kotlin.math.sqrt

class WaybikerMap(
    val mapView: MapView
) {

    // TEMP: Draw debug stuff
    private val annotationMgr: PolylineAnnotationManager

    // Map graph data
    val graphLinks = ArrayList<StreetBit>()
    val graphNodes = HashMap<Long, Intersection>()
    val nodePositions = HashMap<Long, Point>()

    var onClickedStreet: ((StreetBit) -> (Unit))? = null

    init {
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(-73.5824535409464, 45.49576954424193))
                .pitch(0.0)
                .zoom(16.0)
                .bearing(0.0)
                .build()
        )

        annotationMgr = mapView.annotations.createPolylineAnnotationManager()

        mapView.mapboxMap.addOnMoveListener(object : OnMoveListener {
            override fun onMove(detector: MoveGestureDetector): Boolean { return false }
            override fun onMoveBegin(detector: MoveGestureDetector) {}
            override fun onMoveEnd(detector: MoveGestureDetector) { refreshMap() }
        })
    }

    fun getConnectedIntersections(nodeId: Long): ArrayList<Long> {
        val result = ArrayList<Long>()
        for (link in graphLinks) {
            if (link.connectsIntersection(nodeId)) {
                result.add(link.getOtherEnd(nodeId))
            }
        }

        return result
    }

    fun getNodePosition(nodeId: Long): Point {
        return nodePositions[nodeId]?: Point.fromLngLat(0.0, 0.0)
    }

    private fun updateNodePositions(elements: JSONArray) {

        nodePositions.clear()
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
    }

    data class FullStreetData(
        val name: String,
        val points: ArrayList<Long>
    )

    private fun getFullStreetNodesByName(elements: JSONArray): ArrayList<FullStreetData>
    {
        // Array of all ways corresponding to a street name
        val streetBitsByName = HashMap<String, ArrayList<ArrayList<Long>>>()
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
                    wayNodeIds.add(nodeId)
                }

                streetBitsByName.getOrPut(streetName) { ArrayList() }.add(wayNodeIds)
            }
            catch (_: Exception) { }
        }

        val result = ArrayList<FullStreetData>()
        for ((streetName, nodes) in streetBitsByName)
        {
            while (nodes.isNotEmpty())
            {
                val nodesToPlace = nodes[0]
                val s1 = nodesToPlace.first()
                val e1 = nodesToPlace.last()

                var foundAnyConsecutiveNodes = false

                // Compare end of nodesToPlace to start of other ways
                for (i in 1 until nodes.size)
                {
                    val nodesToCompare = nodes[i]
                    val s2 = nodesToCompare.first()
                    if (e1 == s2)
                    {
                        nodesToPlace.addAll(nodesToCompare.subList(1, nodesToCompare.size))
                        nodes.removeAt(i)
                        foundAnyConsecutiveNodes = true
                        break
                    }
                }

                // Compare start of nodesToPlace to end of other ways
                for (i in 1 until nodes.size)
                {
                    val nodesToCompare = nodes[i]
                    val e2 = nodesToCompare.last()
                    if (s1 == e2)
                    {
                        nodesToPlace.addAll(0, nodesToCompare.subList(0, nodesToCompare.size - 1))
                        nodes.removeAt(i)
                        foundAnyConsecutiveNodes = true
                        break
                    }
                }

                if (!foundAnyConsecutiveNodes)
                {
                    result.add(FullStreetData(streetName, nodesToPlace))
                    nodes.removeAt(0)
                }
            }
        }

        return result
    }

    private fun findIntersectionNodes(streetData: ArrayList<FullStreetData>): HashSet<Long> {
        // Each node is associated with the list of streets passing through.
        // If a node has one street, it is not an intersection. If it has more
        // than one, it is an intersection linking all the street names.
        val nodeUsers = HashMap<Long, HashSet<String>>()

        for ((streetName, nodes) in streetData) {
            for (nodeId in nodes) {
                nodeUsers.getOrPut(nodeId) { HashSet() }.add(streetName)
            }
        }

        val result = HashSet<Long>()
        for ((nodeId, passingStreets) in nodeUsers) {
            if (passingStreets.size > 1) {
                result.add(nodeId)
            }
        }

        return result
    }

    private fun calcPointDistanceSqr(p1: Point, p2: Point): Double {
        // From ChatGPT
        val w1 = Math.toRadians(p1.latitude())
        val w2 = Math.toRadians(p2.latitude())
        val dw = w2 - w1
        val dl = Math.toRadians(p2.longitude() - p1.longitude())
        val R = 6371000.0  // Earth's radius in meters
        val x = dl * cos((w1 + w2) / 2)
        val y = dw
        return sqrt(x * x + y * y) * R
    }

    fun findLinkBetween(intersection0: Long, intersection1: Long): StreetBit? {
        return graphLinks.find { streetBit ->
            streetBit.connectsIntersection(intersection0) &&
            streetBit.connectsIntersection(intersection1)
        }
    }

    fun findNearestIntersectionToPoint(point: Point, intersections: List<Long>): Long {
        var minDistance = Double.MAX_VALUE
        var nearestNodeId = 0L
        for (nodeId in intersections) {
            val nodePoint = getNodePosition(nodeId)
            val distance = calcPointDistanceSqr(point, nodePoint)
            if (distance < minDistance) {
                minDistance = distance
                nearestNodeId = nodeId
            }
        }

        return nearestNodeId
    }

    fun findNearestIntersectionToPoint(point: Point): Long {
        return findNearestIntersectionToPoint(point, graphNodes.map { v -> v.key })
    }

    private fun makeMapGraph(elements: JSONArray) {
        updateNodePositions(elements)
        val fullStreetNodes = getFullStreetNodesByName(elements)
        val intersectionNodes = findIntersectionNodes(fullStreetNodes)

        // Add intersections to the graph
        graphNodes.clear()
        for (nodeId in intersectionNodes) {
            graphNodes[nodeId] = Intersection(nodeId)
        }

        // Create links between nodes
        graphLinks.clear()
        for ((_, nodeIds) in fullStreetNodes) {
            var prevIntersectionId: Long? = null
            val segmentNodeIds = ArrayList<Long>()
            for (nodeId in nodeIds) {
                if (intersectionNodes.contains(nodeId)) {
                    // nodeId is an intersection.
                    if (prevIntersectionId != null) {
                        segmentNodeIds.add(nodeId)

                        // Add this link to both nodes...
                        val linkStart = graphNodes[prevIntersectionId]
                        val linkEnd = graphNodes[nodeId]
                        val streetBit = StreetBit(segmentNodeIds.toList())
                        linkStart?.connectingStreets?.add(streetBit)
                        linkEnd?.connectingStreets?.add(streetBit)

                        graphLinks.add(streetBit)

                        segmentNodeIds.clear()
                    }

                    prevIntersectionId = nodeId
                }

                if (prevIntersectionId != null)
                    segmentNodeIds.add(nodeId)
            }
        }

        // Draw streets
        for (streetBit in graphLinks)
        {
            val polyLinePoints = ArrayList<Point>()
            for (nodeId in streetBit.nodeIds) {
                polyLinePoints.add(getNodePosition(nodeId))
            }

            val bitEnds = streetBit.getEnds()
            val jsonElement = JsonObject()
            jsonElement.addProperty("p0", bitEnds.first)
            jsonElement.addProperty("p1", bitEnds.second)

            val annotationOptions = PolylineAnnotationOptions()
                .withPoints(polyLinePoints)
                .withLineColor("#9c9c9c")
                .withLineWidth(8.0)
                .withData(jsonElement)
            annotationMgr.create(annotationOptions)
        }

        // Allow clicking on streets
        annotationMgr.addClickListener { annotation ->
            val data = annotation.getData() as JsonObject
            val p0 = data.get("p0").asLong
            val p1 = data.get("p1").asLong
            val streetBit = graphLinks.find { streetBit ->
                val ends = streetBit.getEnds()
                ends.first == p0 && ends.second == p1
            }

            if (streetBit != null)
                onClickedStreet?.invoke(streetBit)

            false
        }
    }

    @SuppressLint("DefaultLocale")
    private fun refreshMap() {
        val bounds = mapView.mapboxMap.coordinateBoundsForCamera(mapView.mapboxMap.cameraState.toCameraOptions())

        val query = String.format("""
            [out:json][timeout:25][bbox:%.4f,%.4f,%.4f,%.4f];
            (
              way["highway"~"^(trunk|primary|secondary|tertiary|unclassified|residential)"];
            );
            out body;
            >;
            out skel qt;
        """, bounds.south() - 0.002f, bounds.west() - 0.002f, bounds.north() + 0.002f, bounds.east() + 0.002f).trimIndent()

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

                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val elements = json.getJSONArray("elements")

                makeMapGraph(elements)
            }
        })
    }
}