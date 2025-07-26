package blue.eaudouce.waybiker.map

import android.annotation.SuppressLint
import android.util.Log
import blue.eaudouce.waybiker.util.MapTiling
import blue.eaudouce.waybiker.util.MapTiling.tileBoundsToCoordinateBounds
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
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

class MapGraph {
    // All loaded ways, used to avoid loading them twice.
    private val loadedWays = HashSet<Long>()

    // Node ID to adjacent node IDs.
    // - 1 adjacent node means a dead-end.
    // - 2 adjacent nodes mean it is a point on a street.
    // - 3 adjacent nodes or more mean it's an intersection.
    private val nodes = HashMap<Long, ArrayList<Long>>()

    // Node ID to their position
    private val nodePositions = HashMap<Long, Point>()

    enum class TileRequestState { Requested, Received }
    private val tileRequestStates = HashMap<MapTiling.MapTile, TileRequestState>()

    fun isTilePendingOrLoaded(tile: MapTiling.MapTile): Boolean {
        return tileRequestStates.contains(tile)
    }

    fun removeAllTiles(predicate: ((MapTiling.MapTile) -> (Boolean))) {
        TODO()
    }

    // Returns all nodes linking the two inputs, including them.
    fun findLinkBetween(node0: Long, node1: Long): ArrayList<Long>? {
//        val adjacentNodes = nodes[node0] ?: return null
//
//        for (adjacentNode in adjacentNodes) {
//            var reachedOtherNode = adjacentNode == node1
//            while (!reachedOtherNode) {
//                val temp = nodes[adjacentNode] ?: continue
//            }
//        }
//
//        return graphLinks.find { streetBit ->
//            streetBit.connectsIntersection(intersection0) &&
//                    streetBit.connectsIntersection(intersection1)
//        }

        return null
    }

    @SuppressLint("DefaultLocale")
    fun loadTile(tile: MapTiling.MapTile) {
        val bounds = tileBoundsToCoordinateBounds(MapTiling.TileBounds(tile, tile))

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

        tileRequestStates[tile] = TileRequestState.Requested

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Overpass", "Request failed: ${e.message}")
                tileRequestStates.remove(tile)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val elements = json.getJSONArray("elements")

                onReceivedTile(elements)

                tileRequestStates[tile] = TileRequestState.Received
            }
        })
    }

    private fun onReceivedTile(elements: JSONArray) {
        // Set node positions
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

        // Populate nodes structure with all adjacent nodes from this way.
        for (elementIndex in 0 until elements.length()) {
            val element = elements.getJSONObject(elementIndex)
            try {
                val elementType = element.getString("type")
                if (!elementType.equals("way"))
                    continue

                val id = element.getLong("id")
                if (!loadedWays.add(id))
                    continue // Way was already loaded.

                val wayNodes = element.getJSONArray("nodes")
                for (nodeIndex in 0 until wayNodes.length()) {
                    val nodeId = wayNodes.getLong(nodeIndex)
                    val adjacentNodes = nodes.getOrPut(nodeId) { ArrayList() }

                    if (nodeIndex > 0) {
                        adjacentNodes.add(wayNodes.getLong(nodeIndex - 1))
                    }

                    if (nodeIndex < wayNodes.length() - 1) {
                        adjacentNodes.add(wayNodes.getLong(nodeIndex + 1))
                    }
                }
            }
            catch (_: Exception) { }
        }
    }
}