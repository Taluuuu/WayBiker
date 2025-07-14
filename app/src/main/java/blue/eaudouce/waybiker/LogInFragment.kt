package blue.eaudouce.waybiker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogInFragment : MainAppFragment(R.layout.fragment_log_in) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val confirmButton = view.findViewById<Button>(R.id.btn_confirm)
        confirmButton?.setOnClickListener {

            val usernameText = view.findViewById<EditText>(R.id.et_username)
            val passwordText = view.findViewById<EditText>(R.id.et_password)

            if (usernameText == null || passwordText == null) {
                return@setOnClickListener
            }

            lifecycleScope.launch {
                var success = false
                withContext(Dispatchers.IO) {
                    try {
                        SupabaseInstance.client.auth.signInWith(Email) {
                            email = usernameText.text.toString()
                            password = passwordText.text.toString()
                        }

                        success = true
                    } catch (e: Exception) {
                        // TODO: Show error
                    }
                }

                if (success)
                    onFinished?.invoke(WaybikerFragment())
            }
        }
    }
}