package blue.eaudouce.waybiker.home

import android.content.Context
import android.widget.FrameLayout
import android.widget.RatingBar
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import blue.eaudouce.waybiker.R
import blue.eaudouce.waybiker.StreetRating
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.map.MapGraph
import blue.eaudouce.waybiker.map.StreetBit
import blue.eaudouce.waybiker.map.WaybikerMap
import blue.eaudouce.waybiker.util.MapTiling
import com.google.gson.JsonObject
import com.mapbox.maps.plugin.annotation.Annotation
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.OnCircleAnnotationDragListener
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okhttp3.Dispatcher
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class MapAction_RateStreet(
    private val waybikerMap: WaybikerMap,
    private val tabContentView: FrameLayout
) : MapAction_Base() {
    private val lineAnnotationMgr: PolylineAnnotationManager = waybikerMap.mapView.annotations.createPolylineAnnotationManager()
    private val circleAnnotationMgr: CircleAnnotationManager = waybikerMap.mapView.annotations.createCircleAnnotationManager()

    private val selectedStreets = ArrayDeque<Long>()
    private val selectionAnnotations = HashMap<MapGraph.LinkKey, PolylineAnnotation>()
    private var dialogView: MapDialogView? = null

    private val lifecycleScope = CoroutineScope(Dispatchers.Main)

    private enum class RatingState {
        Inactive,
        TapStreet,
        DefineStreetLength,
        RateStreet
    }

    private var currentState = RatingState.Inactive

    private fun setState(newState: RatingState) {
        if (currentState == newState)
            return

        // Exit previous state logic
        when (currentState) {
            RatingState.Inactive -> {}
            RatingState.TapStreet -> {
                waybikerMap.mapGraph.onClickedLink = null
            }
            RatingState.DefineStreetLength -> {
                destroyHandles()
                circleAnnotationMgr.dragListeners.clear()

                if (newState != RatingState.RateStreet) {
                    unhighlightAllStreets()
                    selectedStreets.clear()
                }
            }
            RatingState.RateStreet -> {}
        }

        currentState = newState

        // Enter new state logic
        when (currentState) {
            RatingState.Inactive -> {
                unhighlightAllStreets()
            }
            RatingState.TapStreet -> {
                dialogView?.updateDialog(
                    "Tap the street you want to rate",
                    "",
                    { finishAction() },
                    null
                )

                waybikerMap.mapGraph.onClickedLink = { link: MapGraph.LinkKey ->
                    selectedStreets.add(link.first)
                    selectedStreets.add(link.second)
                    highlightStreet(link)

                    setState(RatingState.DefineStreetLength)
                }
            }
            RatingState.DefineStreetLength -> {
                dialogView?.updateDialog(
                    "Define the portion of the street to rate",
                    "",
                    { setState(RatingState.TapStreet) },
                    { setState(RatingState.RateStreet) }
                )

                createHandle(0)
                createHandle(1)

                circleAnnotationMgr.addDragListener(object : OnCircleAnnotationDragListener {
                    override fun onAnnotationDragStarted(annotation: Annotation<*>) {}
                    override fun onAnnotationDragFinished(annotation: Annotation<*>) {}
                    override fun onAnnotationDrag(annotation: Annotation<*>) {
                        onDragHandle(annotation as CircleAnnotation)
                    }
                })
            }
            RatingState.RateStreet -> {
                val ratingBar = StreetRatingBarView(tabContentView.context)
                dialogView?.updateDialog(
                    "Rate your selection",
                    "",
                    { setState(RatingState.DefineStreetLength) },
                    {
                        val editedTiles = ArrayList<MapTiling.MapTile>()
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val ratingsTable = SupabaseInstance.client.from("street_ratings")
                                    selectedStreets
                                        .zipWithNext()
                                        .forEach { ends ->
                                            val linkKey = MapGraph.LinkKey.of(ends.first, ends.second)
                                            val linkTile = waybikerMap.mapGraph.getLinkTile(linkKey)

                                            if (linkTile != null) {
                                                val rating = StreetRating(
                                                    start = linkKey.first,
                                                    end = linkKey.second,
                                                    rating = ratingBar.rating.roundToInt().toShort(),
                                                    user_id = SupabaseInstance.client.auth.currentUserOrNull()?.id ?: "",
                                                    tile_x = linkTile.x,
                                                    tile_y = linkTile.y,
                                                    Clock.System.now()
                                                )

                                                ratingsTable.upsert(rating)

                                                if (!editedTiles.contains(linkTile))
                                                    editedTiles.add(linkTile)
                                            }
                                        }
                                } catch (e: Exception) {
                                    // TODO: Show error
                                    e.printStackTrace()
                                }
                            }

                            waybikerMap.mapGraph.queueTileLoads(editedTiles, true)
                            finishAction()
                        }
                    }
                )

                dialogView?.setContent(ratingBar)
            }
        }
    }

    private fun getHandleIntersection(handleIndex: Int): Long? {
        if (selectedStreets.isEmpty()) {
            return null
        }

        return if (handleIndex == 0) selectedStreets.first() else selectedStreets.last()
    }

    private fun highlightStreet(link: MapGraph.LinkKey) {
        val streetNodeIds = waybikerMap.mapGraph.getLinkNodeIds(link) ?: return
        val nodePositions = streetNodeIds.mapNotNull { waybikerMap.mapGraph.getNodePosition(it) }

        val annotationOptions = PolylineAnnotationOptions()
            .withPoints(nodePositions)
            .withLineWidth(20.0)
            .withLineColor("#439c32")
        val annotation = lineAnnotationMgr.create(annotationOptions)

        selectionAnnotations[link] = annotation
    }


    private fun unhighlightStreet(linkKey: MapGraph.LinkKey) {
        val annotation = selectionAnnotations.remove(linkKey)
        if (annotation != null)
            lineAnnotationMgr.delete(annotation)
    }

    private fun unhighlightAllStreets() {
        lineAnnotationMgr.deleteAll()
        selectionAnnotations.clear()
    }

    private fun createHandle(handleIndex: Int) {
        val handleNodeId = if (handleIndex == 0)
            selectedStreets.first() else selectedStreets.last()

        val handlePosition = waybikerMap.mapGraph.getNodePosition(handleNodeId) ?: return

        val jsonElement = JsonObject()
        jsonElement.addProperty("handleIndex", handleIndex)

        val firstHandleAnnotationOptions = CircleAnnotationOptions()
            .withPoint(handlePosition)
            .withCircleRadius(20.0)
            .withCircleColor("#4eee8b")
            .withDraggable(true)
            .withData(jsonElement)

        circleAnnotationMgr.create(firstHandleAnnotationOptions)
    }

    private fun destroyHandles() {
        circleAnnotationMgr.deleteAll()
    }

    private fun onDragHandle(circleAnnotation: CircleAnnotation) {

        val annotationData = circleAnnotation.getData() as JsonObject
        val handleIndex = annotationData.get("handleIndex").asInt

        val currentIntersection = getHandleIntersection(handleIndex) ?: return

        // Find the new intersection for this handle
        val usableIntersections = ArrayList(waybikerMap.mapGraph.getLinkedIntersectionIds(currentIntersection))
        usableIntersections.add(currentIntersection)
        val nearestNodeId = waybikerMap.mapGraph.findNearestNodeIdToPoint(circleAnnotation.point, usableIntersections) ?: return

        // Move the handle to this intersection
        circleAnnotation.point = waybikerMap.mapGraph.getNodePosition(nearestNodeId) ?: return
        circleAnnotationMgr.update(circleAnnotation)

        if (nearestNodeId == currentIntersection) {
            return
        }

        // Remove all nodes after the last occurrence of an intersection in case of a loop
        var hasRemovedNodes = false
        if (handleIndex == 0) {
            val prevNodeIndex = selectedStreets.indexOfFirst { nodeId -> nodeId == nearestNodeId }
            if (prevNodeIndex != -1) {
                for (i in 0 until prevNodeIndex) {
                    unhighlightStreet(MapGraph.LinkKey.of(selectedStreets[0], selectedStreets[1]))
                    selectedStreets.removeFirst()
                    hasRemovedNodes = true
                }
            }
        } else {
            val prevNodeIndex = selectedStreets.indexOfLast { nodeId -> nodeId == nearestNodeId }
            if (prevNodeIndex != -1) {
                for (i in selectedStreets.size - 1 downTo prevNodeIndex + 1) {
                    unhighlightStreet(MapGraph.LinkKey.of(selectedStreets[i], selectedStreets[i - 1]))
                    selectedStreets.removeLast()
                    hasRemovedNodes = true
                }
            }
        }

        if (hasRemovedNodes) {
            return
        }

        highlightStreet(MapGraph.LinkKey.of(currentIntersection, nearestNodeId))

        if (handleIndex == 0) {
            selectedStreets.addFirst(nearestNodeId)
        } else {
            selectedStreets.addLast(nearestNodeId)
        }
    }

    override fun start(context: Context?) {

        if (context != null) {
            dialogView = MapDialogView(context)
            tabContentView.addView(dialogView)
        }

        setState(RatingState.TapStreet)
    }

    override fun finishAction() {
        setState(RatingState.Inactive)
        tabContentView.removeView(dialogView)
        super.finishAction()
    }
}