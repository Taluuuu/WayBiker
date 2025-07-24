package blue.eaudouce.waybiker

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

class MainActivity : FragmentActivity(R.layout.main_activity) {
    private var currentFragment: MainAppFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            return
        }

        // Restore session
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "preferences",
            masterKeyAlias,
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val refreshToken = sharedPreferences.getString("refreshToken", "")

        lifecycleScope.launch {
            if (refreshToken?.isNotEmpty() ?: false) {
                SupabaseInstance.client.auth.refreshSession(refreshToken)
            }

            if (SupabaseInstance.client.auth.currentSessionOrNull() == null) {
                setCurrentFragment(WelcomeFragment())
            } else {
                setCurrentFragment(WaybikerFragment())
            }
        }
    }

    override fun onPause() {
        // Save session
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "preferences",
            masterKeyAlias,
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session != null) {
            sharedPreferences.edit { putString("refreshToken", session.refreshToken) }
        }

        super.onPause()
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