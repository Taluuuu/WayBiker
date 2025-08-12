package blue.eaudouce.waybiker.home

import android.content.Context
import android.graphics.Color
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.SpinnerAdapter
import blue.eaudouce.waybiker.R
import blue.eaudouce.waybiker.SupabaseInstance
import blue.eaudouce.waybiker.Utility
import blue.eaudouce.waybiker.map.WaybikerMap
import blue.eaudouce.waybiker.util.BitmapUtils.bitmapFromDrawableRes
import blue.eaudouce.waybiker.util.MapTiling
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MapAction_MarkUtility(
    private val waybikerMap: WaybikerMap,
    private val tabContentView: FrameLayout
) : MapAction_Base() {

    private var dialogView: MapDialogView? = null
    private var selectedPoint: Point? = null

    private val annotationMgr = waybikerMap.mapView.annotations.createPointAnnotationManager()
    private var pointAnnotation: PointAnnotation? = null

    private val lifecycleScope = CoroutineScope(Dispatchers.Main)

    private enum class MarkState {
        Inactive,
        Marking,
        Saving
    }

    private var currentState = MarkState.Inactive

    private fun setState(newState: MarkState) {
        if (currentState == newState)
            return

        // Exit state logic
        when (currentState) {
            MarkState.Inactive -> {

            }
            MarkState.Marking -> {
                waybikerMap.onClickedMap = null
            }
            MarkState.Saving -> {

            }
        }

        currentState = newState

        // Enter state logic
        when (currentState) {
            MarkState.Inactive -> {
                resetPointAnnotation()
            }
            MarkState.Marking -> {
                resetPointAnnotation()
                onPointUpdated(null)
                waybikerMap.onClickedMap = { onPointUpdated(it) }
            }
            MarkState.Saving -> {
                val spinner = Spinner(tabContentView.context)
                val adapter = ArrayAdapter(
                    tabContentView.context,
                    android.R.layout.simple_spinner_item,
                    listOf("Bike Lock", "Repair Station"))
                spinner.adapter = adapter

                dialogView?.updateDialog(
                    "Save the utility?",
                    "",
                    { setState(MarkState.Marking) },
                    {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                val user = SupabaseInstance.client.auth.currentUserOrNull()
                                if (user == null)
                                    return@withContext

                                selectedPoint?.let {
                                    val pointTile = MapTiling.pointToMapTile(it)

                                    SupabaseInstance.client
                                        .from("utilities")
                                        .insert(Utility(
                                            UUID.randomUUID().toString(),
                                            pointTile.x,
                                            pointTile.y,
                                            it.longitude(),
                                            it.latitude(),
                                            user.id,
                                            spinner.selectedItemPosition.toShort()
                                        )
                                    )
                                }
                            }

                            selectedPoint?.let {
                                waybikerMap.mapGraph.queueTileLoads(
                                    listOf(MapTiling.pointToMapTile(it)),
                                    true
                                )
                            }
                            finishAction()
                        }
                    }
                )

                dialogView?.setContent(spinner)
            }
        }
    }

    override fun start(context: Context?) {
        if (context != null) {
            dialogView = MapDialogView(context)
            tabContentView.addView(dialogView)
        }

        setState(MarkState.Marking)
    }

    override fun finishAction() {
        setState(MarkState.Inactive)
        tabContentView.removeView(dialogView)
        super.finishAction()
    }

    private fun onPointUpdated(newPoint: Point?) {
        selectedPoint = newPoint

        if (selectedPoint == null) {
            dialogView?.updateDialog(
                "Mark the location of an utility",
                "",
                { finishAction() },
                null
            )
        } else {
            dialogView?.updateDialog(
                "Mark the location of an utility",
                "",
                { finishAction() },
                { setState(MarkState.Saving) }
            )
        }

        if (newPoint != null) {
            if (pointAnnotation == null) {
                val iconBitmap = bitmapFromDrawableRes(tabContentView.context, R.drawable.baseline_location_pin_24)
                if (iconBitmap != null) {
                    val options = PointAnnotationOptions()
                        .withPoint(newPoint)
                        .withIconImage(iconBitmap)
                        .withIconSize(2.0)
                        .withIconOffset(listOf(0.0, -10.0))
                    pointAnnotation = annotationMgr.create(options)
                }
            }

            pointAnnotation?.let {
                it.point = newPoint
                annotationMgr.update(it)
            }
        }
    }

    private fun resetPointAnnotation() {
        pointAnnotation?.let { annotationMgr.delete(it) }
        pointAnnotation = null
    }
}