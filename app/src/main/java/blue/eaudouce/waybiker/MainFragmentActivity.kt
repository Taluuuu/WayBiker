package blue.eaudouce.waybiker

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import blue.eaudouce.waybiker.home.HomeFragment
import blue.eaudouce.waybiker.map.WaybikerMap
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainFragmentActivity : FragmentActivity() {
    private var homeFragment: HomeFragment? = null
    private var tracksFragment: TracksFragment? = null
    private var profileFragment: ProfileFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.fragment_activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bnv_navbar)
        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.menu_home -> homeFragment
                R.id.menu_tracks -> tracksFragment
                R.id.menu_profile -> profileFragment
                else -> null
            }

            fragment?.let {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fl_selected_activity, it)
                    .commit()
                true
            } ?: false
        }

        val waybikerMap = WaybikerMap(findViewById(R.id.mv_main_map))

        homeFragment = HomeFragment(waybikerMap)
        tracksFragment = TracksFragment()
        profileFragment = ProfileFragment()

        homeFragment?.let {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fl_selected_activity, it)
                .commit()
        }
    }
}