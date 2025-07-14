package blue.eaudouce.waybiker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class CreateAccountFragment : MainAppFragment(R.layout.fragment_create_account) {
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
                try {
                    val result = SupabaseInstance.client.auth.signUpWith(Email) {
                        email = usernameText.text.toString()
                        password = passwordText.text.toString()
                    }

                    onFinished?.invoke(WaybikerFragment())
                } catch (e: Exception) {
                    // TODO: Show error
                }
            }
        }
    }
}