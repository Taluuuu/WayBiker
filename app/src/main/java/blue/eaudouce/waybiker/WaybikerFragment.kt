package blue.eaudouce.waybiker

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import blue.eaudouce.waybiker.home.HomeFragment
import blue.eaudouce.waybiker.map.WaybikerMap
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.auth.auth

class WaybikerFragment : MainAppFragment(R.layout.fragment_waybiker) {
    private var homeFragment: HomeFragment? = null
    private var tracksFragment: TracksFragment? = null
    private var profileFragment: ProfileFragment? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bnv_navbar)
        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.menu_home -> homeFragment
                R.id.menu_tracks -> tracksFragment
                R.id.menu_profile -> profileFragment
                else -> null
            }

            fragment?.let {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fl_selected_activity, it)
                    .commit()
                true
            } ?: false
        }

        val waybikerMap = WaybikerMap(view.findViewById(R.id.mv_main_map))

        homeFragment = HomeFragment.newInstance(waybikerMap)
        tracksFragment = TracksFragment()
        profileFragment = ProfileFragment()

        homeFragment?.let {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fl_selected_activity, it)
                .commit()
        }
    }
}