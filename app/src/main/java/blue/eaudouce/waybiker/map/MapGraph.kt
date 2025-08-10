package blue.eaudouce.waybiker.map

import android.annotation.SuppressLint
import android.graphics.Color
import blue.eaudouce.waybiker.StreetRating
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.util.MapTiling
import blue.eaudouce.waybiker.util.MapTiling.pointToMapTile
import blue.eaudouce.waybiker.util.MapTiling.toCoordinateBounds
import blue.eaudouce.waybiker.util.calcSegmentScore
import blue.eaudouce.waybiker.util.calcStreetColor
import blue.eaudouce.waybiker.util.isRatingFading
import com.google.gson.JsonObject
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import io.github.jan.supabase.postgrest.from
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
import kotlin.invoke
import kotlin.math.cos
import kotlin.math.sqrt

class MapGraph(mapView: MapView) {
    @ConsistentCopyVisibility
    data class LinkKey private constructor(val first: Long, val second: Long) {
        companion object {
            fun of(a: Long, b: Long): LinkKey {
                return if (a <= b) LinkKey(a, b) else LinkKey(b, a)
            }
        }
    }

    private data class RatingData(
        val rating: Float,
        val fading: Boolean
    )

    private class TileData {
        // All IDs of ways owned by this tile
        val ways = ArrayList<Long>()

        // Stored to make sure links are deleted once the tile is deleted.
        val links = ArrayList<LinkKey>()

        val ratings = HashMap<LinkKey, RatingData>()
    }

    private data class WayData(
        // All IDs of nodes within this way
        val nodes: ArrayList<Long>,
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
        val mapTile: MapTiling.MapTile,
    ) {
        val isIntersection get() = adjacentNodes.size != 2
    }

    private val tiles = HashMap<MapTiling.MapTile, TileData>()
    private val ways = HashMap<Long, WayData>()
    private val nodes = HashMap<Long, NodeData>()

    private data class LinkData(
        val annotation: PolylineAnnotation,
        val nodeIds: List<Long>,
    )

    private val links = HashMap<LinkKey, LinkData>()

    private val tilesToLoad = ArrayList<MapTiling.MapTile>()
    private val tilesCurrentlyLoading = ArrayList<MapTiling.MapTile>()
    private val linkAnnotationMgr = mapView.annotations.createPolylineAnnotationManager()
    private val circleAnnotationMgr = mapView.annotations.createCircleAnnotationManager()
    private val wayAnnotationMgr = mapView.annotations.createPolylineAnnotationManager()

    var onClickedLink: ((LinkKey) -> Unit)? = null

    private val lifecycleScope = CoroutineScope(Dispatchers.Main)

    private val client = OkHttpClient.Builder().build()

    private val normalStreetOpacity = 0.5
    private val highlightedStreetOpacity = 1.0
    private val notHighlightedStreetOpacity = 0.1

    private var highlightedSegments = HashSet<LinkKey>()

    init {
        linkAnnotationMgr.addClickListener { annotation ->
            val data = annotation.getData() as JsonObject
            val p0 = data.get("p0").asLong
            val p1 = data.get("p1").asLong
            onClickedLink?.invoke(LinkKey.of(p0, p1))
            false
        }
    }

    fun highlightSegments(segmentsToHighlight: List<LinkKey>) {
        highlightedSegments = segmentsToHighlight.toHashSet()
        for ((linkKey, linkData) in links) {
            if (highlightedSegments.contains(linkKey)) {
                // Highlight segment
                linkData.annotation.lineOpacity = highlightedStreetOpacity
            } else {
                linkData.annotation.lineOpacity = notHighlightedStreetOpacity
            }

            linkAnnotationMgr.update(linkData.annotation)
        }
    }

    fun clearHighlightedSegments() {
        highlightedSegments.clear()
        for ((_, linkData) in links) {
            linkData.annotation.lineOpacity = normalStreetOpacity
            linkAnnotationMgr.update(linkData.annotation)
        }
    }

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

    fun findNearestNodeIdToPoint(point: Point): Long? {
        return findNearestNodeIdToPoint(point, nodes.keys.toList())
    }

    fun findNearestNodeIdToPoint(point: Point, nodeIds: List<Long>): Long? {
        var minDistance = Double.MAX_VALUE
        var nearestNodeId = -1L
        for (nodeId in nodeIds) {
            val nodePoint = getNodePosition(nodeId) ?: continue
            val distance = calcPointDistanceSqr(point, nodePoint)
            if (distance < minDistance) {
                minDistance = distance
                nearestNodeId = nodeId
            }
        }

        return if (nearestNodeId == -1L) null else nearestNodeId
    }

    private fun removeTile(tile: MapTiling.MapTile) {
        val tileData = tiles.remove(tile)
        if (tileData == null)
            return

        for (wayId in tileData.ways)
            removeWay(wayId)

        for (linkKey in tileData.links) {
            links.remove(linkKey)?.let {
                linkAnnotationMgr.delete(it.annotation)
            }
        }
    }

    private fun removeWay(wayId: Long) {
        val way = ways[wayId] ?: return

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

    fun getLinkNodeIds(linkKey: LinkKey): List<Long>? {
        return links[linkKey]?.nodeIds
    }

    fun getLinkTile(linkKey: LinkKey): MapTiling.MapTile? {
        val nodePos = getNodePosition(linkKey.first) ?: return null
        return pointToMapTile(nodePos)
    }

    fun getNodePosition(nodeId: Long): Point? {
        return nodes[nodeId]?.position
    }

    fun getLinkedIntersectionIds(nodeId: Long): List<Long> {
        return links.keys.mapNotNull {
            when (nodeId) {
                it.first -> it.second
                it.second -> it.first
                else -> null
            }
        }
    }

    fun queueTileLoads(newTiles: List<MapTiling.MapTile>, forceReload: Boolean = false) {
        for (tile in newTiles) {
            if (forceReload || !tilesToLoad.contains(tile) && !tilesCurrentlyLoading.contains(tile) && !tiles.contains(tile))
                tilesToLoad.add(tile)
        }

        loadPendingTiles()
    }

    @SuppressLint("DefaultLocale")
    private fun makeQueryText(tiles: List<MapTiling.MapTile>): String {
        var ways = ""
        for (tile in tiles) {
            val bounds = tile.toCoordinateBounds()
            ways += String.format("              way[\"highway\"~\"^(trunk|primary|secondary|tertiary|unclassified|residential)\"](%.4f,%.4f,%.4f,%.4f);\n",
                bounds.south(), bounds.west(), bounds.north(), bounds.east())
        }

        return String.format("""
            [out:json];
            (
%s
            );
            out skel center;
            >;
            out skel qt;
        """, ways).trimIndent()
    }

    @SuppressLint("DefaultLocale")
    fun loadPendingTiles() {

        if (tilesToLoad.isEmpty())
            return

        for (tile in tilesToLoad)
            removeTile(tile)

        val query = makeQueryText(tilesToLoad)

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val requestBody = "data=$encodedQuery"
            .toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://overpass.private.coffee/api/interpreter")
            .post(requestBody)
            .build()

        tilesCurrentlyLoading.addAll(tilesToLoad)
        tilesToLoad.clear()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                lifecycleScope.launch {
                    e.printStackTrace()

                    tilesToLoad += tilesCurrentlyLoading;
                    tilesCurrentlyLoading.clear()
                    loadPendingTiles()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                lifecycleScope.launch {
                    val queriedTiles = tilesCurrentlyLoading.toMutableList()
                    try {
                        val elements = withContext(Dispatchers.IO) {
                            val body = response.body?.string() ?: ""
                            val json = JSONObject(body)
                            json.getJSONArray("elements")
                        }

                        // Get all ratings that apply to queried tiles
                        val ratings = withContext(Dispatchers.IO) {
                            SupabaseInstance.client
                                .from("street_ratings")
                                .select {
                                    filter {
                                        or {
                                            queriedTiles.forEach { (x, y) ->
                                                and {
                                                    eq("tile_x", x)
                                                    eq("tile_y", y)
                                                }
                                            }
                                        }
                                    }
                                }
                                .decodeList<StreetRating>()
                        }

                        onReceivedTiles(elements, ratings, queriedTiles)
                        tilesCurrentlyLoading.clear()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    response.close()
                }
            }
        })
    }

    fun getNextIntersection(startId: Long, adjId: Long): Long? {
        var prevId = startId
        var currentId = adjId

        var node = nodes[currentId] ?: return null
        val visited = mutableSetOf<Long>() // Prevent infinite loops

        while (!node.isIntersection) {
            if (!visited.add(currentId)) return null // loop detected

            val nextId = node.adjacentNodes.find { it != prevId } ?: return null
            prevId = currentId
            currentId = nextId
            node = nodes[currentId] ?: return null
        }

        return currentId
    }

    // Does not take into account the distance between nodes.
    fun findShortestPathBFS(
        startId: Long,
        endId: Long,
    ): List<Long>? {
        val queue = ArrayDeque<List<Long>>() // Queue of paths
        val visited = HashSet<Long>()

        queue.add(listOf(startId))
        visited.add(startId)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val currentId = path.last()

            if (currentId == endId)
                return path // Success

            val currentNode = nodes[currentId] ?: continue

            for (neighbor in currentNode.adjacentNodes) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor)
                    queue.add(path + neighbor)
                }
            }
        }

        return null // No path found
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

                val tile = tiles[mapTile]
                if (tile == null)
                    continue

                // Get all nodes in the tile that are intersections
                val intersectionNodeIds = ArrayList<Long>()
                for ((nodeId, nodeData) in nodes) {
                    if (nodeData.mapTile == mapTile && nodeData.isIntersection) {
                        intersectionNodeIds.add(nodeId)

//                        val options = CircleAnnotationOptions()
//                            .withPoint(nodeData.position)
//                            .withCircleColor(Color.BLUE)
//                            .withCircleRadius(5.0)
//                        circleAnnotationMgr.create(options)
                    }
                }

                for (nodeId in intersectionNodeIds) {
                    val node = nodes[nodeId]
                    if (node == null)
                        continue

                    for (adjacentNodeId in node.adjacentNodes) {
                        var prevId = nodeId
                        var curId = adjacentNodeId

                        val linkNodeIds = ArrayList<Long>()
                        linkNodeIds.add(nodeId)

                        // Traverse from this node to the nearest intersection in the direction of
                        // adjacentNodeId
                        while (true) {
                            val cur = nodes[curId]!!

                            linkNodeIds.add(curId)

                            if (cur.isIntersection) {
                                val linkKey = LinkKey.of(nodeId, curId)
                                if (pointToMapTile(nodes[linkKey.first]!!.position) != mapTile)
                                    break

                                val rating = tile.ratings[linkKey]

                                val points = linkNodeIds.mapNotNull { nodes[it]?.position }

                                if (!links.contains(linkKey)) {
                                    val jsonElement = JsonObject()
                                    jsonElement.addProperty("p0", linkKey.first)
                                    jsonElement.addProperty("p1", linkKey.second)

                                    var opacity = normalStreetOpacity
                                    if (!highlightedSegments.isEmpty()) {
                                        opacity = if (highlightedSegments.contains(linkKey)) highlightedStreetOpacity else notHighlightedStreetOpacity
                                    }

                                    val options = PolylineAnnotationOptions()
                                        .withPoints(points)
                                        .withLineWidth(15.0)
                                        .withLineColor(calcStreetColor(rating?.rating))
                                        .withLineOpacity(opacity)
                                        .withLineBlur(if (rating?.fading?: false) 10.0 else 0.0)
                                        .withData(jsonElement)

                                    links[linkKey] = LinkData(linkAnnotationMgr.create(options), linkNodeIds)
                                    tile.links.add(linkKey)
                                }

                                break
                            }

                            val temp = prevId
                            prevId = curId
                            curId = cur.adjacentNodes.find { temp != it }!!
                        }
                    }
                }
            }
        }
    }

    fun isNodeIntersection(nodeId: Long): Boolean {
        return nodes[nodeId]?.isIntersection ?: false
    }

    private fun onReceivedTiles(elements: JSONArray, ratings: List<StreetRating>, queriedTiles: MutableList<MapTiling.MapTile>) {
        val nodePositions = HashMap<Long, Point>()

        queriedTiles.removeAll { tiles[it] != null }

        for (mapTile in queriedTiles) {
            // TODO: This assert sometimes fails
            // assert(tiles[mapTile] == null)
//            removeTile(mapTile)
            tiles[mapTile] = TileData()
        }

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

                // Find to which tile this way belongs
                val wayId = element.getLong("id")
                val wayCenterObject = element.getJSONObject("center")
                val wayCenter = Point.fromLngLat(
                    wayCenterObject.getDouble("lon"),
                    wayCenterObject.getDouble("lat")
                )
                val mapTile = pointToMapTile(wayCenter)

                // Add this way id to the tile
                val tileWays = tiles[mapTile]?.ways
                if (tileWays != null) {
                    if (!tileWays.contains(wayId))
                        tileWays.add(wayId)
                }

                var way = ways[wayId]
                if (way != null)
                    continue

                way = WayData(ArrayList())
                ways[wayId] = way

                val wayNodes = element.getJSONArray("nodes")
                for (nodeIndex in 0 until wayNodes.length()) {
                    val nodeId = wayNodes.getLong(nodeIndex)
                    val nodePos = nodePositions[nodeId] ?: continue

                    way.nodes.add(nodeId)

                    var node = nodes[nodeId]
                    if (node == null) {
                        node = NodeData(
                            ArrayList(),
                            nodePos,
                            1,
                            pointToMapTile(nodePos)
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

//                val wayPositions = way.nodes.mapNotNull { nodePositions[it] }
//                val options = PolylineAnnotationOptions()
//                    .withPoints(wayPositions)
//                    .withLineWidth(5.0)
//                    .withLineColor(Color.BLUE)
//                    .withLineOpacity(0.3)
//
//                wayAnnotationMgr.create(options)
            }
            catch (_: Exception) { }
        }

        val segmentToRatings = HashMap<LinkKey, ArrayList<StreetRating>>()
        for (rating in ratings) {
            val ratedLinkKey = LinkKey.of(rating.start, rating.end)
            segmentToRatings.getOrPut(ratedLinkKey) { arrayListOf() }.add(rating)
        }

        for ((linkKey, segmentRatings) in segmentToRatings) {
            val mapTile = getLinkTile(linkKey) ?: continue
            val tile = tiles[mapTile] ?: continue
            val rating = calcSegmentScore(segmentRatings)
            val isFading = isRatingFading(segmentRatings)
            if (rating != null)
                tile.ratings[linkKey] = RatingData(rating, isFading)
        }

        for (mapTile in queriedTiles)
            drawStreets(mapTile)
    }
}