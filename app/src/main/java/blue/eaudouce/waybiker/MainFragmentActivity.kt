package blue.eaudouce.waybiker

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.toCameraOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import java.net.URLEncoder

class MainFragmentActivity : FragmentActivity() {
    private val homeFragment = HomeFragment()
    private val tracksFragment = TracksFragment()
    private val profileFragment = ProfileFragment()

    private lateinit var waybikerMap: WaybikerMap


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.fragment_activity_main)

        // Setup tabs
        supportFragmentManager.beginTransaction()
            .replace(R.id.fl_selected_activity, homeFragment)
            .commit()

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

        waybikerMap = WaybikerMap(findViewById(R.id.mv_main_map))
    }
}