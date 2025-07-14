package blue.eaudouce.waybiker

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.github.jan.supabase.network.SupabaseApi
import io.github.jan.supabase.plugins.SupabasePlugin

class MainActivity : FragmentActivity(R.layout.main_activity) {
    private var currentFragment: MainAppFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCurrentFragment(WelcomeFragment())
    }

    private fun setCurrentFragment(fragment: MainAppFragment) {
        currentFragment = fragment

        currentFragment?.onFinished = { nextFragment ->
            setCurrentFragment(nextFragment)
        }

        currentFragment?.let {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fl_main, it)
                .commit()
        }
    }
}