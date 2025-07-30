package blue.eaudouce.waybiker.home

import android.content.Context
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.Track
import blue.eaudouce.waybiker.TrackPoint
import blue.eaudouce.waybiker.map.WaybikerMap
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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

    private val lifecycleScope = CoroutineScope(Dispatchers.Main)

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
                    {
                        lifecycleScope.launch {
                            // Trim all points that are not intersections, apart from the first and last ones.
                            val finalPoints = ArrayList<TrackPoint>()
                            val trackId = UUID.randomUUID()
                            var pointIndex = 0
                            for (i in 0 until recordedPathNodes.size) {
                                val nodeId = recordedPathNodes[i]

                                if (i == 0 || i == recordedPathNodes.size - 1) {
                                    finalPoints.add(
                                        TrackPoint(
                                            trackId.toString(),
                                            pointIndex++,
                                            nodeId
                                        )
                                    )
                                    continue
                                }

                                if (waybikerMap.mapGraph.isNodeIntersection(nodeId)) {
                                    finalPoints.add(
                                        TrackPoint(
                                            trackId.toString(),
                                            pointIndex++,
                                            nodeId
                                        )
                                    )
                                }
                            }

                            val track = Track(
                                trackId.toString(),
                                "test",
                                SupabaseInstance.client.auth.currentUserOrNull()?.id ?: ""
                            )

                            withContext(Dispatchers.IO) {
                                try {
                                    val tracksTable = SupabaseInstance.client.from("tracks")
                                    tracksTable.upsert(track)

                                    val trackPointsTable = SupabaseInstance.client.from("track_points")
                                    for (point in finalPoints) {
                                        trackPointsTable.upsert(point)
                                    }
                                } finally {}
                            }

                            finishAction()
                        }
                    }
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