package blue.eaudouce.waybiker

import android.os.Bundle
import android.view.View
import android.widget.Button

class LogInFragment : MainAppFragment(R.layout.fragment_log_in) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val confirmButton = view.findViewById<Button>(R.id.btn_confirm_log_in)
        confirmButton?.setOnClickListener {
            onFinished?.invoke(WaybikerFragment())
        }
    }
}