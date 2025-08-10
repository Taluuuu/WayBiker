package blue.eaudouce.waybiker

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import blue.eaudouce.waybiker.home.HomeFragment
import blue.eaudouce.waybiker.map.WaybikerMap
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.auth.auth

class WaybikerFragment : MainAppFragment(R.layout.fragment_waybiker) {
    var mainActivity: MainActivity? = null

    private var homeFragment: HomeFragment? = null
    private var tracksFragment: TracksFragment? = null
    private var profileFragment: ProfileFragment? = null

    private var locationClient: FusedLocationProviderClient? = null

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

        val waybikerMap = WaybikerMap(view.findViewById(R.id.mv_main_map), context)

        context?.let {
            locationClient = LocationServices.getFusedLocationProviderClient(it)
            if (ActivityCompat.checkSelfPermission(it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
                locationClient?.requestLocationUpdates(locationRequest,
                    { location ->
                        waybikerMap.onLocationUpdated(location)
                    }, Looper.getMainLooper()
                )
            }
        }

        homeFragment = HomeFragment.newInstance(waybikerMap)
        tracksFragment = TracksFragment.newInstance(waybikerMap)
        profileFragment = ProfileFragment()
        profileFragment?.mainActivity = mainActivity

        homeFragment?.let {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fl_selected_activity, it)
                .commit()
        }
    }
}