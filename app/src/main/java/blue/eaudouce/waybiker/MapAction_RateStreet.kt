package blue.eaudouce.waybiker

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

class MapAction_RateStreet(
    private val waybikerMap: WaybikerMap
) {
    private val lineAnnotationMgr: PolylineAnnotationManager = waybikerMap.mapView.annotations.createPolylineAnnotationManager()
    private val circleAnnotationMgr: CircleAnnotationManager = waybikerMap.mapView.annotations.createCircleAnnotationManager()

    data class StreetBitAnnotation(
        val start: Long,
        val end: Long,
        val annotation: PolylineAnnotation
    )

    private val selectedStreets = ArrayList<StreetBitAnnotation>()

    private fun getHandleIntersection(handleIndex: Int): Long? {
        if (selectedStreets.isEmpty()) {
            return null
        }

        val streetBit = if (handleIndex == 0) selectedStreets.first() else selectedStreets.last()
        return if (handleIndex == 0) streetBit.start else streetBit.end
    }

    private fun onDragHandle(circleAnnotation: CircleAnnotation) {
        val annotationData = circleAnnotation.getData() as JsonObject
        val handleIndex = annotationData.get("handleIndex").asInt

        val currentIntersection = getHandleIntersection(handleIndex) ?: return
        val usableIntersections = waybikerMap.getConnectedIntersections(currentIntersection)
        usableIntersections.add(currentIntersection)

        val nearestNodeId = waybikerMap.findNearestIntersectionToPoint(circleAnnotation.point, usableIntersections)
        circleAnnotation.point = waybikerMap.getNodePosition(nearestNodeId)
        circleAnnotationMgr.update(circleAnnotation)

        if (nearestNodeId == currentIntersection) {
            return
        }

        for (streetBit in selectedStreets) {
            if (streetBit.end == nearestNodeId || streetBit.start == nearestNodeId) {
                return
            }
        }

        val streetBit = waybikerMap.findLinkBetween(currentIntersection, nearestNodeId) ?: return
        val nodePositions = streetBit.nodeIds.mapNotNull {
            nodeId -> waybikerMap.nodePositions[nodeId]
        }

        val annotationOptions = PolylineAnnotationOptions()
            .withPoints(nodePositions)
            .withLineWidth(20.0)
            .withLineColor("#439c32")
        val annotation = lineAnnotationMgr.create(annotationOptions)
        lineAnnotationMgr.create(annotationOptions)

        val newSelectedBit = StreetBitAnnotation(streetBit.getEnds().first, streetBit.getEnds().second, annotation)
        if (handleIndex == 0) {
            selectedStreets.add(0, newSelectedBit)
        } else {
            selectedStreets.add(newSelectedBit)
        }
    }

    fun start() {
        // Initial phase - user clicks on a street to rate
        waybikerMap.onClickedStreet = { clickedStreet: StreetBit ->

            waybikerMap.onClickedStreet = null

            // Second phase - add street selection handles
            val nodePositions = clickedStreet.nodeIds.mapNotNull {
                nodeId -> waybikerMap.nodePositions[nodeId]
            }

            // Highlight street
            val annotationOptions = PolylineAnnotationOptions()
                .withPoints(nodePositions)
                .withLineWidth(20.0)
                .withLineColor("#439c32")
            val annotation = lineAnnotationMgr.create(annotationOptions)

            val selectedStreetEnds = clickedStreet.getEnds()
            selectedStreets.add(StreetBitAnnotation(
                selectedStreetEnds.first, selectedStreetEnds.second, annotation))

            // Add handles
            val jsonElement0 = JsonObject()
            jsonElement0.addProperty("handleIndex", 0)
            val firstHandleAnnotationOptions = CircleAnnotationOptions()
                .withPoint(waybikerMap.getNodePosition(selectedStreetEnds.first))
                .withCircleRadius(20.0)
                .withCircleColor("#4eee8b")
                .withDraggable(true)
                .withData(jsonElement0)
            circleAnnotationMgr.create(firstHandleAnnotationOptions)

            val jsonElement1 = JsonObject()
            jsonElement1.addProperty("handleIndex", 1)
            val secondHandleAnnotationOptions = CircleAnnotationOptions()
                .withPoint(waybikerMap.getNodePosition(selectedStreetEnds.second))
                .withCircleRadius(20.0)
                .withCircleColor("#4eee8b")
                .withDraggable(true)
                .withData(jsonElement1)
            circleAnnotationMgr.create(secondHandleAnnotationOptions)

            circleAnnotationMgr.addDragListener(object : OnCircleAnnotationDragListener {
                override fun onAnnotationDragStarted(annotation: Annotation<*>) {}

                override fun onAnnotationDrag(annotation: Annotation<*>) {
                    onDragHandle(annotation as CircleAnnotation)
                }

                override fun onAnnotationDragFinished(annotation: Annotation<*>) {}
            })
        }
    }
}