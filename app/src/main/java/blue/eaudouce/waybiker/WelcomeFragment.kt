package blue.eaudouce.waybiker

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment

class WelcomeFragment : MainAppFragment(R.layout.fragment_welcome) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val logInButton = view.findViewById<Button>(R.id.btn_log_in)
        logInButton?.setOnClickListener {
            onFinished?.invoke(LogInFragment())
        }

        val createAccountButton = view.findViewById<Button>(R.id.btn_create_account)
        createAccountButton?.setOnClickListener {
            onFinished?.invoke(WaybikerFragment())
        }
    }
}