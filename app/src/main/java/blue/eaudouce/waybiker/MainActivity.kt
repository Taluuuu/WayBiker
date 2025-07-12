package blue.eaudouce.waybiker

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity(R.layout.main_activity) {
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentFragment = WaybikerFragment()
        currentFragment?.let {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fl_main, it)
                .commit()
        }
    }
}