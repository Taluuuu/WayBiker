package blue.eaudouce.waybiker

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.network.SupabaseApi
import io.github.jan.supabase.plugins.SupabasePlugin
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity(R.layout.main_activity) {
    private var currentFragment: MainAppFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            return
        }

        lifecycleScope.launch {
            try {
                val refreshToken = getRefreshToken()
                if (!refreshToken.isNullOrEmpty()) {
                    SupabaseInstance.client.auth.refreshSession(refreshToken)
                    saveRefreshToken()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val session = SupabaseInstance.client.auth.currentSessionOrNull()
            if (session == null) {
                setCurrentFragment(WelcomeFragment())
            } else {
                val waybikerFragment = WaybikerFragment()
                waybikerFragment.mainActivity = this@MainActivity
                setCurrentFragment(waybikerFragment)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveRefreshToken()
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

    fun signOut() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                SupabaseInstance.client.auth.signOut()
            }

            clearRefreshToken()
            setCurrentFragment(WelcomeFragment())
        }
    }

    private fun getSharedPreferences(): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "preferences",
            masterKeyAlias,
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getRefreshToken(): String? {
        return getSharedPreferences().getString("refreshToken", "")
    }

    private fun saveRefreshToken() {
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session != null) {
            getSharedPreferences().edit { putString("refreshToken", session.refreshToken) }
        }
    }

    private fun clearRefreshToken() {
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session != null) {
            getSharedPreferences().edit { putString("refreshToken", "") }
        }
    }
}