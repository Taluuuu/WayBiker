package blue.eaudouce.waybiker.home

import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout
import blue.eaudouce.waybiker.map.WaybikerMap
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager

class MapAction_RecordTrack(
    private val waybikerMap: WaybikerMap,
    private val tabContentView: FrameLayout
) : MapAction_Base() {

    private var dialogView: MapDialogView? = null

    private enum class RecordState {
        Inactive,
        Intro,
        Recording,
        PostRecording
    }

    private var currentState = RecordState.Inactive

    private val recordedPathNodes = ArrayList<Long>()
    private val recordedPathPoints = ArrayList<Point>()
    private val recordedPathAnnotation: PolylineAnnotation
    private val lineAnnotationMgr: PolylineAnnotationManager = waybikerMap.mapView.annotations.createPolylineAnnotationManager()

    init {
        val options = PolylineAnnotationOptions()
            .withPoints(recordedPathPoints)
            .withLineWidth(15.0)
            .withLineColor(Color.CYAN)

        recordedPathAnnotation = lineAnnotationMgr.create(options)
    }

    private fun setState(newState: RecordState) {
        if (currentState == newState)
            return

        // Exit previous state logic
        when (currentState) {
            RecordState.Inactive -> {
                lineAnnotationMgr.delete(recordedPathAnnotation)
                recordedPathNodes.clear()
                recordedPathPoints.clear()
            }
            RecordState.Intro -> {

            }
            RecordState.Recording -> {
                waybikerMap.onLocationUpdated = null
            }
            RecordState.PostRecording -> {

            }
        }

        currentState = newState

        // Enter new state logic
        when (currentState) {
            RecordState.Inactive -> {

            }
            RecordState.Intro -> {
                dialogView?.updateDialog(
                    "Record and save a track",
                    "",
                    { finishAction() },
                    { setState(RecordState.Recording) }
                )
            }
            RecordState.Recording -> {
                dialogView?.updateDialog(
                    "Recording...",
                    "",
                    { setState(RecordState.Intro) },
                    { setState(RecordState.PostRecording) }
                )

                waybikerMap.onLocationUpdated = { onLocationUpdated(it) }
            }
            RecordState.PostRecording -> {
                dialogView?.updateDialog(
                    "Save this track?",
                    "You will be notified if there is a change in the condition of this track.",
                    { finishAction() },
                    { finishAction() }
                )
            }
        }
    }

    override fun start(context: Context?) {
        if (context != null) {
            dialogView = MapDialogView(context)
            tabContentView.addView(dialogView)
        }

        setState(RecordState.Intro)
    }

    override fun finishAction() {
        setState(RecordState.Inactive)
        tabContentView.removeView(dialogView)
        super.finishAction()
    }

    private fun onLocationUpdated(location: Point) {
        var nearestPointId = -1L
        var minDistance = Double.MAX_VALUE
        for (link in waybikerMap.graphLinks) {
            for (pointId in link.nodeIds) {
                val point = waybikerMap.getNodePosition(pointId)
                val dist = waybikerMap.calcPointDistanceSqr(location, point)
                if (dist < minDistance) {
                    minDistance = dist
                    nearestPointId = pointId
                }
            }
        }

        if (recordedPathNodes.isNotEmpty() && recordedPathNodes.last() == nearestPointId) {
            return
        }

        // We have reached a new point.
        recordedPathNodes.add(nearestPointId)
        recordedPathPoints.add(waybikerMap.getNodePosition(nearestPointId))

        lineAnnotationMgr.update(recordedPathAnnotation)
    }
}