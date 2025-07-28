package blue.eaudouce.waybiker.map

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import blue.eaudouce.waybiker.util.MapTiling
import blue.eaudouce.waybiker.util.MapTiling.toCoordinateBounds
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
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

class MapGraph(
    private val mapView: MapView
) {
    private data class TileData(
        // All IDs of ways within this tile
        val ways: HashSet<Long>,
    )

    private data class WayData(
        // All IDs of nodes within this way
        val nodes: ArrayList<Long>,
        // The number of individual loaded tiles with this way
        var referenceCount: Int,
    )

    private data class NodeData(
        // The ID of nodes adjacent to this one
        // - 1 adjacent node means a dead-end.
        // - 2 adjacent nodes mean it is a point on a street.
        // - 3 adjacent nodes or more mean it's an intersection.
        val adjacentNodes: ArrayList<Long>,
        val position: Point,
        // The number of individual loaded ways with this node
        var referenceCount: Int,
    )

    private val tiles = HashMap<MapTiling.MapTile, TileData>()
    private val ways = HashMap<Long, WayData>()
    private val nodes = HashMap<Long, NodeData>()

    private val pendingTiles = ArrayDeque<MapTiling.MapTile>()
    private var waitingOnTile = false

    private val circleAnnotationMgr = mapView.annotations.createCircleAnnotationManager()

    fun isTilePendingOrLoaded(tile: MapTiling.MapTile): Boolean {
        return tiles.contains(tile) || pendingTiles.contains(tile)
    }

    fun removeAllTiles(shouldRemove: ((MapTiling.MapTile) -> Boolean)) {
        tiles.entries.removeIf { (tile, tileData) ->
            if (!shouldRemove(tile))
                return@removeIf false

            for (wayId in tileData.ways)
                removeWay(wayId)

            return@removeIf true
        }
    }

    private fun removeTile(tile: MapTiling.MapTile) {
        val tileData = tiles.remove(tile)
        if (tileData == null)
            return

        for (wayId in tileData.ways)
            removeWay(wayId)
    }

    private fun removeWay(wayId: Long) {
        val way = ways[wayId]!!
        if (--way.referenceCount > 0)
            return

        // No more tiles refer to this way, it can be deleted.
        ways.remove(wayId)

        // Remove all newly unused nodes
        for (nodeId in way.nodes)
            removeNode(nodeId)
    }

    private fun removeNode(nodeId: Long) {
        val node = nodes[nodeId]!!
        if (--node.referenceCount > 0)
            return

        // No more ways refer to this node, it can be deleted.
        nodes.remove(nodeId)

        // Remove the node from its adjacent nodes
        for (adjNodeId in node.adjacentNodes)
            nodes[adjNodeId]!!.adjacentNodes.remove(nodeId)
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

    fun queueTileLoad(tile: MapTiling.MapTile) {
        if (!pendingTiles.contains(tile)) {
            pendingTiles.add(tile)
        }

        loadNextTile()
    }

    private fun loadNextTile() {
        if (waitingOnTile)
            return

        val tile = pendingTiles.removeFirstOrNull()
        if (tile != null)
            loadTileImmediate(tile)
    }

    @SuppressLint("DefaultLocale")
    private fun loadTileImmediate(tile: MapTiling.MapTile) {

        // Reload tile
        if (isTilePendingOrLoaded(tile))
            removeTile(tile)

        val bounds = tile.toCoordinateBounds()

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

        tiles[tile] = TileData(HashSet())

        waitingOnTile = true

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                waitingOnTile = false

                Log.e("Overpass", "Request failed: ${e.message}")

                tiles.remove(tile)
                queueTileLoad(tile)
            }

            override fun onResponse(call: Call, response: Response) {
                waitingOnTile = false

                try {
                    val body = response.body?.string() ?: return
                    val json = JSONObject(body)
                    val elements = json.getJSONArray("elements")

                    onReceivedTile(elements, tile)
                } catch (e: Exception) {
                    Log.e("Overpass", "Failed to parse tile response: ${e.message}")

                    tiles.remove(tile)
                    queueTileLoad(tile)
                }
            }
        })
    }

    private fun getNextIntersection(startId: Long, adjId: Long): Long? {
        var adjId = adjId
        var startId = startId
        var node = nodes[adjId] ?: return null
        while (node.adjacentNodes.size == 2) {
            startId = adjId
            adjId = node.adjacentNodes.find { it != startId } ?: return null
            node = nodes[adjId] ?: return null
        }

        return adjId
    }

    private fun onReceivedTile(elements: JSONArray, mapTile: MapTiling.MapTile) {
        val nodePositions = HashMap<Long, Point>()

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

                val wayId = element.getLong("id")
                tiles[mapTile]?.ways?.add(wayId)

                var way = ways[wayId]
                if (way != null) {
                    // Way was already loaded by another tile.
                    way.referenceCount++
                    continue
                }

                way = WayData(ArrayList(), 1)
                ways[wayId] = way

                val wayNodes = element.getJSONArray("nodes")
                for (nodeIndex in 0 until wayNodes.length()) {
                    val nodeId = wayNodes.getLong(nodeIndex)

                    way.nodes.add(nodeId)

                    var node = nodes[nodeId]
                    if (node != null) {
                        // Node was already created by another way.
                        node.referenceCount++

                        if (nodeIndex > 0) {
                            node.adjacentNodes.add(wayNodes.getLong(nodeIndex - 1))
                        }

                        if (nodeIndex < wayNodes.length() - 1) {
                            node.adjacentNodes.add(wayNodes.getLong(nodeIndex + 1))
                        }

                        continue
                    }

                    node = NodeData(
                        ArrayList(),
                        nodePositions[nodeId] ?: continue,
                        1
                    )
                    nodes[nodeId] = node

                    if (nodeIndex > 0) {
                        node.adjacentNodes.add(wayNodes.getLong(nodeIndex - 1))
                    }

                    if (nodeIndex < wayNodes.length() - 1) {
                        node.adjacentNodes.add(wayNodes.getLong(nodeIndex + 1))
                    }
                }
            }
            catch (_: Exception) { }
        }

        // Draw streets
//        val tile = tiles[mapTile]
//        if (tile != null) {
//            val tileNodes = ArrayList<Long>()
//            for (wayId in tile.ways) {
//                val way = ways[wayId]
//                if (way == null)
//                    continue
//
//                // Add only intersection nodes
//                for (nodeId in way.nodes) {
//                    val node = nodes[nodeId]
//                    if (node == null)
//                        continue
//
//                    if (node.adjacentNodes.size != 2) {
//                        tileNodes.add(nodeId)
//                    }
//                }
//            }
//        }

        circleAnnotationMgr.deleteAll()
        for ((nodeId, nodeData) in nodes) {
            if (nodeData.adjacentNodes.size == 2)
                continue

            val options = CircleAnnotationOptions()
                .withPoint(nodeData.position)
                .withCircleRadius(10.0)
                .withCircleColor(Color.BLUE)

            circleAnnotationMgr.create(options)

//            // Node is an intersection.
//            for (adjacentNodeId in nodeData.adjacentNodes) {
//                val nextIntersectionId = getNextIntersection(nodeId, adjacentNodeId)
//                if (nextIntersectionId == null)
//                    continue
//
//            }
        }

        loadNextTile()
    }
}