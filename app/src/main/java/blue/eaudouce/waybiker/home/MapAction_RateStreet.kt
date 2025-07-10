package blue.eaudouce.waybiker.home

import android.content.Context
import android.widget.FrameLayout
import android.widget.RatingBar
import blue.eaudouce.waybiker.R
import blue.eaudouce.waybiker.map.StreetBit
import blue.eaudouce.waybiker.map.WaybikerMap
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
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


class MapAction_RateStreet(
    private val waybikerMap: WaybikerMap,
    private val tabContentView: FrameLayout
) : MapAction_Base() {
    private val lineAnnotationMgr: PolylineAnnotationManager = waybikerMap.mapView.annotations.createPolylineAnnotationManager()
    private val circleAnnotationMgr: CircleAnnotationManager = waybikerMap.mapView.annotations.createCircleAnnotationManager()

    private val selectedStreets = ArrayDeque<Long>()
    private val selectionAnnotations = HashMap<Pair<Long, Long>, PolylineAnnotation>()
    private var dialogView: MapDialogView? = null

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
                waybikerMap.onClickedStreet = null
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

                waybikerMap.onClickedStreet = { clickedStreet: StreetBit ->
                    val clickedStreetEnds = clickedStreet.getEnds()
                    selectedStreets.add(clickedStreetEnds.first)
                    selectedStreets.add(clickedStreetEnds.second)
                    highlightStreet(clickedStreet)

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

                        GlobalScope.launch {
                            withContext(Dispatchers.IO) {
                                val ratingsTable = waybikerMap.supabase.from("StreetRatings")
                                selectedStreets
                                    .zipWithNext()
                                    .forEach { ends ->
                                        val (from, to) = sortEnds(ends)
                                        val rating = WaybikerMap.StreetRating(
                                            from,
                                            to,
                                            ratingBar.rating.roundToInt().toShort()
                                        )
                                        ratingsTable.upsert(rating)
                                    }
                            }

                            waybikerMap.refreshMap()
                        }

                        finishAction()
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

    private fun sortEnds(ends: Pair<Long, Long>): Pair<Long, Long> {
        return if (ends.first < ends.second)
            Pair(ends.first, ends.second) else
            Pair(ends.second, ends.first)
    }

    private fun highlightStreet(streetBit: StreetBit) {
        val nodePositions = streetBit.nodeIds.mapNotNull {
            nodeId -> waybikerMap.nodePositions[nodeId]
        }

        val annotationOptions = PolylineAnnotationOptions()
            .withPoints(nodePositions)
            .withLineWidth(20.0)
            .withLineColor("#439c32")
        val annotation = lineAnnotationMgr.create(annotationOptions)

        selectionAnnotations[sortEnds(streetBit.getEnds())] = annotation
    }


    private fun unhighlightStreet(ends: Pair<Long, Long>) {
        val annotation = selectionAnnotations.remove(sortEnds(ends))
        if (annotation != null)
            lineAnnotationMgr.delete(annotation)
    }

    private fun unhighlightAllStreets() {
        lineAnnotationMgr.deleteAll()
        selectionAnnotations.clear()
    }

    private fun createHandle(handleIndex: Int) {
        val handlePosition = if (handleIndex == 0)
            selectedStreets.first() else selectedStreets.last()

        val jsonElement = JsonObject()
        jsonElement.addProperty("handleIndex", handleIndex)

        val firstHandleAnnotationOptions = CircleAnnotationOptions()
            .withPoint(waybikerMap.getNodePosition(handlePosition))
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
        val usableIntersections = waybikerMap.getConnectedIntersections(currentIntersection)
        usableIntersections.add(currentIntersection)
        val nearestNodeId = waybikerMap.findNearestIntersectionToPoint(circleAnnotation.point, usableIntersections)

        // Move the handle to this intersection
        circleAnnotation.point = waybikerMap.getNodePosition(nearestNodeId)
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
                    unhighlightStreet(Pair(selectedStreets[0], selectedStreets[1]))
                    selectedStreets.removeFirst()
                    hasRemovedNodes = true
                }
            }
        } else {
            val prevNodeIndex = selectedStreets.indexOfLast { nodeId -> nodeId == nearestNodeId }
            if (prevNodeIndex != -1) {
                for (i in selectedStreets.size - 1 downTo prevNodeIndex + 1) {
                    unhighlightStreet(Pair(selectedStreets[i], selectedStreets[i - 1]))
                    selectedStreets.removeLast()
                    hasRemovedNodes = true
                }
            }
        }

        if (hasRemovedNodes) {
            return
        }

        val streetBit = waybikerMap.findLinkBetween(currentIntersection, nearestNodeId) ?: return
        highlightStreet(streetBit)

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