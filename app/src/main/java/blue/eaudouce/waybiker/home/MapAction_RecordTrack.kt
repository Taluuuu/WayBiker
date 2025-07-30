package blue.eaudouce.waybiker.home

import android.content.Context
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.map.WaybikerMap
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import io.github.jan.supabase.postgrest.from

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
    private var recordedPathAnnotation: PolylineAnnotation? = null
    private val lineAnnotationMgr: PolylineAnnotationManager = waybikerMap.mapView.annotations.createPolylineAnnotationManager()

    private fun setState(newState: RecordState) {
        if (currentState == newState)
            return

        // Exit previous state logic
        when (currentState) {
            RecordState.Inactive -> {

            }
            RecordState.Intro -> {

            }
            RecordState.Recording -> {
                waybikerMap.onLocationUpdated = null
            }
            RecordState.PostRecording -> {
                val trackPointsTable = SupabaseInstance.client.from("track_points")
//                trackPointsTable.upsert()
            }
        }

        currentState = newState

        // Enter new state logic
        when (currentState) {
            RecordState.Inactive -> {
                deletePath()
            }
            RecordState.Intro -> {
                dialogView?.updateDialog(
                    "Record and save a track",
                    "",
                    { finishAction() },
                    { setState(RecordState.Recording) }
                )

                deletePath()
            }
            RecordState.Recording -> {
                dialogView?.updateDialog(
                    "Recording...",
                    "",
                    { setState(RecordState.Intro) },
                    { setState(RecordState.PostRecording) }
                )

                initPath()

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
        val newNodeId = waybikerMap.mapGraph.findNearestNodeIdToPoint(location) ?: return

        val addPoint = { nodeId: Long ->
            val nodePos = waybikerMap.mapGraph.getNodePosition(nodeId)
            if (nodePos != null) {
                recordedPathNodes.add(nodeId)
                recordedPathPoints.add(nodePos)
            }
        }

        if (recordedPathNodes.isEmpty()) {
            addPoint(newNodeId)
            return
        }

        val lastNodeId = recordedPathNodes.last()
        if (newNodeId == lastNodeId)
            return // We did not move

        val pathToNewNode = waybikerMap.mapGraph.findShortestPathBFS(lastNodeId, newNodeId) ?: return
        for (i in 1 until pathToNewNode.size)
            addPoint(pathToNewNode[i])

        lineAnnotationMgr.update(recordedPathAnnotation!!)
    }

    private fun deletePath() {
        recordedPathNodes.clear()
        recordedPathPoints.clear()

        recordedPathAnnotation?.let { lineAnnotationMgr.delete(it) }
    }

    private fun initPath() {

        val options = PolylineAnnotationOptions()
            .withPoints(recordedPathPoints)
            .withLineWidth(20.0)
            .withLineColor("#3467ec".toColorInt())
            .withLineOpacity(1.0)

        recordedPathAnnotation = lineAnnotationMgr.create(options)
    }
}