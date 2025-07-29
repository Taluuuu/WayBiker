package blue.eaudouce.waybiker.map

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import blue.eaudouce.waybiker.util.MapTiling
import blue.eaudouce.waybiker.util.MapTiling.toCoordinateBounds
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    @ConsistentCopyVisibility
    data class LinkKey private constructor(val first: Long, val second: Long) {
        companion object {
            fun of(a: Long, b: Long): LinkKey {
                return if (a <= b) LinkKey(a, b) else LinkKey(b, a)
            }
        }
    }

    private class TileData {
        // All IDs of ways within this tile
        val ways = HashSet<Long>()

        // Stored to make sure links are deleted once the tile is deleted.
        val links = ArrayList<LinkKey>()
    }

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
    ) {
        val isIntersection get() = adjacentNodes.size != 2
    }

    private val tiles = HashMap<MapTiling.MapTile, TileData>()
    private val ways = HashMap<Long, WayData>()
    private val nodes = HashMap<Long, NodeData>()

    private data class LinkData(
        val annotation: PolylineAnnotation,
    )

    private val links = HashMap<LinkKey, LinkData>()

    private val maxPendingTiles = 5
    private var numPendingTiles = 0
    private val tilesToLoad = ArrayDeque<MapTiling.MapTile>()

//    private val circleAnnotationMgr = mapView.annotations.createCircleAnnotationManager()
    private val polylineAnnotationMgr = mapView.annotations.createPolylineAnnotationManager()

    private val lifecycleScope = CoroutineScope(Dispatchers.Main)

    fun isTilePendingOrLoaded(tile: MapTiling.MapTile): Boolean {
        return tiles.contains(tile) || tilesToLoad.contains(tile)
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
        if (!tilesToLoad.contains(tile)) {
            tilesToLoad.add(tile)
        }

        loadNextTile()
    }

    private fun loadNextTile() {
        if (numPendingTiles >= maxPendingTiles)
            return

        val tile = tilesToLoad.removeFirstOrNull()
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

        numPendingTiles++

        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(requestBody)
            .build()

        lifecycleScope.launch {
            val elements = withContext(Dispatchers.IO) {
                try {
                    val response = OkHttpClient().newCall(request).execute()
                    val body = response.body!!.string()
                    val json = JSONObject(body)
                    json.getJSONArray("elements")
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            numPendingTiles--

            if (elements != null) {
                try {
                    onReceivedTile(elements, tile)
                } catch (e: Exception) {
                    e.printStackTrace()
                    tiles.remove(tile)
                    queueTileLoad(tile)
                }
            }
        }
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

    fun areNeighbourTilesLoaded(centerTile: MapTiling.MapTile): Boolean {
        for (i in centerTile.x - 1..centerTile.x + 1) {
            for (j in centerTile.y - 1..centerTile.y + 1) {
                val tile = MapTiling.MapTile(i, j)
                if (!tiles.contains(tile))
                    return false
            }
        }

        return true
    }

    fun drawStreets(centerTile: MapTiling.MapTile) {
        for (i in centerTile.x - 1..centerTile.x + 1) {
            for (j in centerTile.y - 1..centerTile.y + 1) {
                val mapTile = MapTiling.MapTile(i, j)
                if (!areNeighbourTilesLoaded(mapTile))
                    continue

                // Get tile nodes
                val tile = tiles[mapTile]
                if (tile == null || tile.links.isNotEmpty())
                    continue

                val intersectionNodeIds = ArrayList<Long>()
                for (wayId in tile.ways) {
                    val way = ways[wayId]
                    if (way == null)
                        continue

                    for (nodeId in way.nodes) {
                        val node = nodes[nodeId]
                        if (node != null && node.isIntersection)
                            intersectionNodeIds.add(nodeId)
                    }
                }

                for (nodeId in intersectionNodeIds) {
                    val node = nodes[nodeId]!!

                    for (adjacentNodeId in node.adjacentNodes) {
                        var prevId = nodeId
                        var curId = adjacentNodeId

                        val points = ArrayList<Point>()
                        points.add(node.position)

                        while (true) {
                            val cur = nodes[curId]!!

                            points.add(cur.position)

                            if (cur.isIntersection) {
                                val linkKey = LinkKey.of(nodeId, curId)
                                if (MapTiling.pointToMapTile(nodes[linkKey.first]!!.position) != mapTile)
                                    break

                                if (!links.contains(linkKey)) {
                                    val options = PolylineAnnotationOptions()
                                        .withPoints(points)
                                        .withLineWidth(10.0)
                                        .withLineColor(Color.GREEN)
                                        .withLineOpacity(0.5)

                                    links[linkKey] = LinkData(polylineAnnotationMgr.create(options))
                                    tile.links.add(linkKey)
                                }

                                break
                            }

                            val temp = prevId
                            prevId = curId
                            curId = cur.adjacentNodes.find { temp != it }!!// .next
                        }
                    }
                }

//                // TODO: Nodes that are temporarily not intersections will still pass this.
//                //       Need to make sure this only runs if all neighbouring tiles are loaded.
//                val intersectionNodeIds = nodePositions.keys.filter {
//                    val node = nodes[it]
//                    return@filter node != null && node.isIntersection
//                }
//
//                for (nodeId in intersectionNodeIds) {
//                    val node = nodes[nodeId]!!
//
//                    for (adjacentNodeId in node.adjacentNodes) {
//                        var prevId = nodeId
//                        var curId = adjacentNodeId
//
//                        val points = ArrayList<Point>()
//                        points.add(node.position)
//
//                        while (true) {
//                            val cur = nodes[curId]!!
//
//                            points.add(cur.position)
//
//                            if (cur.isIntersection) {
//                                val linkKey = LinkKey.of(nodeId, curId)
//                                if (MapTiling.pointToMapTile(nodes[linkKey.first]!!.position) != mapTile)
//                                    break
//
//                                if (!links.contains(linkKey)) {
//                                    val options = PolylineAnnotationOptions()
//                                        .withPoints(points)
//                                        .withLineWidth(10.0)
//                                        .withLineColor(Color.GREEN)
//
//                                    links[linkKey] = LinkData(polylineAnnotationMgr.create(options))
//                                }
//
//                                break
//                            }
//
//                            val temp = prevId
//                            prevId = curId
//                            curId = cur.adjacentNodes.find { temp != it }!!// .next
//                        }
//                    }
//                }
            }
        }
    }

    private fun onReceivedTile(elements: JSONArray, mapTile: MapTiling.MapTile) {
        var tile = tiles[mapTile]
        if (tile != null)
            return

        tile = TileData()
        tiles[mapTile] = tile

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
                tile.ways.add(wayId)

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
                    if (node == null) {
                        node = NodeData(
                            ArrayList(),
                            nodePositions[nodeId] ?: continue,
                            1
                        )
                        nodes[nodeId] = node
                    } else {
                        // Node was already created by another way.
                        node.referenceCount++
                    }

                    if (nodeIndex > 0)
                        node.adjacentNodes.add(wayNodes.getLong(nodeIndex - 1))

                    if (nodeIndex < wayNodes.length() - 1)
                        node.adjacentNodes.add(wayNodes.getLong(nodeIndex + 1))
                }
            }
            catch (_: Exception) { }
        }

        // Debug intersections
//        circleAnnotationMgr.deleteAll()
//        for ((nodeId, nodeData) in nodes) {
//            if (nodeData.adjacentNodes.size == 2)
//                continue
//
//            val options = CircleAnnotationOptions()
//                .withPoint(nodeData.position)
//                .withCircleRadius(10.0)
//                .withCircleColor(Color.BLUE)
//
//            circleAnnotationMgr.create(options)
//
////            // Node is an intersection.
////            for (adjacentNodeId in nodeData.adjacentNodes) {
////                val nextIntersectionId = getNextIntersection(nodeId, adjacentNodeId)
////                if (nextIntersectionId == null)
////                    continue
////
////            }
//        }

        drawStreets(mapTile)
        loadNextTile()
    }
}