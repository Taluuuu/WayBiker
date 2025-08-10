package blue.eaudouce.waybiker.home

import android.content.Context
import android.widget.FrameLayout
import blue.eaudouce.waybiker.map.WaybikerMap

class MapAction_MarkUtility(
    private val waybikerMap: WaybikerMap,
    private val tabContentView: FrameLayout
) : MapAction_Base() {

    private enum class MarkState {
        Inactive,
        Marking,
        Saving
    }

    override fun start(context: Context?) {
    }
}