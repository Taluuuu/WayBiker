package blue.eaudouce.waybiker.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import blue.eaudouce.waybiker.R
import blue.eaudouce.waybiker.map.WaybikerMap
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeFragment : Fragment(R.layout.fragment_home) {

    // The currently running action
    private var mapAction: MapAction_Base? = null
    private var viewsHiddenDuringAction = ArrayList<View>()
    private lateinit var waybikerMap: WaybikerMap

    companion object {
        fun newInstance(waybikerMap: WaybikerMap) : HomeFragment {
            val instance = HomeFragment()
            instance.waybikerMap = waybikerMap
            return instance
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rateStreetButton = view.findViewById<FloatingActionButton>(R.id.fab_rate_street)
        rateStreetButton.setOnClickListener {
            mapAction = MapAction_RateStreet(waybikerMap, view.findViewById(R.id.fl_home_dialog))
            startAction()
        }

        viewsHiddenDuringAction.add(view.findViewById(R.id.hsv_map_toggles))
        viewsHiddenDuringAction.add(view.findViewById(R.id.ll_action_buttons))
    }

    private fun startAction() {
        mapAction?.start(context)
        mapAction?.onFinished = {
            stopAction()
        }

        for (view in viewsHiddenDuringAction) {
            view.visibility = INVISIBLE
        }
    }

    private fun stopAction() {
        mapAction = null

        for (view in viewsHiddenDuringAction) {
            view.visibility = VISIBLE
        }
    }
}