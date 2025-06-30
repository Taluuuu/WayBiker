package blue.eaudouce.waybiker.map

import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import java.util.ArrayList

class StreetPortion(
    streetNodes: ArrayList<Long>,
    nodePositions: ArrayList<Point>,
    portionId: Long
) {
    private val streetNodes = streetNodes
    private val nodePositions = nodePositions
    private val portionId = portionId

    fun getEnds(): Pair<Long, Long> {
        assert(streetNodes.isNotEmpty())
        return Pair(streetNodes.first(), streetNodes.last())
    }

    fun getOtherEnd(end: Long): Long {
        val (e1, e2) = getEnds()
        return if (end == e1) e2 else e1
    }

    fun getPortionId(): Long {
        return portionId
    }

    fun drawAnnotation(annotationMgr: PolylineAnnotationManager) {
        val annotationOptions = PolylineAnnotationOptions()
            .withPoints(nodePositions)
            .withLineColor("#ee4e8b")
            .withLineWidth(10.0)

        annotationMgr.create(annotationOptions)
        annotationMgr.addClickListener { annotation ->
            annotationMgr.delete(annotation)
            false
        }
    }
}