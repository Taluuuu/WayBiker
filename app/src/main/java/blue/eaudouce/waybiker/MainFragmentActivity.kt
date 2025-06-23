package blue.eaudouce.waybiker

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
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

    private lateinit var mapView: MapView
    private lateinit var annotationMgr: PolylineAnnotationManager

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

        mapView = findViewById(R.id.mv_main_map)
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(-73.5824535409464, 45.49576954424193))
                .pitch(0.0)
                .zoom(15.0)
                .bearing(0.0)
                .build()
        )

        annotationMgr = mapView.annotations.createPolylineAnnotationManager()

        fetchStreetGeometry()
    }

    fun fetchStreetGeometry() {
        val query = """
            [out:json][timeout:25];
            (
              way["name"="Rue Saint-Mathieu"]["lanes"]["highway"](around:1000,45.495,-73.578);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val requestBody = "data=$encodedQuery"
            .toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Overpass", "Request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val elements = json.getJSONArray("elements")

                val points = ArrayList<Point>()
                for (i in 0 until elements.length()) {
                    val element = elements.getJSONObject(i)
                    try {
                        val lat = element.getDouble("lat")
                        val lon = element.getDouble("lon")
                        points.add(Point.fromLngLat(lon, lat))
                    }
                    catch (e: Exception) { }
                }

                points.sortBy { point -> point.latitude() }

                val annotationOptions = PolylineAnnotationOptions()
                    .withPoints(points)
                    .withLineColor("#ee4e8b")
                    .withLineWidth(5.0)
                annotationMgr.create(annotationOptions)
            }
        })
    }
}