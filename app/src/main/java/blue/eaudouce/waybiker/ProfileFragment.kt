package blue.eaudouce.waybiker

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.mapbox.maps.MapView
import io.github.jan.supabase.auth.auth

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    var mainActivity: MainActivity? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = SupabaseInstance.client.auth.currentUserOrNull() ?: return

        val userText = view.findViewById<TextView>(R.id.tv_connected_user_name)
        userText.text = user.email.toString()

        view.findViewById<Button>(R.id.btn_sign_out)?.setOnClickListener {
            mainActivity?.signOut()
        }
    }
}