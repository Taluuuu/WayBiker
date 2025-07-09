package blue.eaudouce.waybiker.home

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import blue.eaudouce.waybiker.R

class MapDialogView(context: Context) : FrameLayout(context) {

    init {
        inflate(context, R.layout.view_map_dialog, this)
    }

    fun updateDialog(title: String, subtitle: String, backCallback: (() -> Unit)?, nextCallback: (() -> Unit)?) {

        val dialogTitle = findViewById<TextView>(R.id.tv_dialog_title)
        dialogTitle?.text = title

        val dialogSubtext = findViewById<TextView>(R.id.tv_dialog_subtext)
        dialogSubtext?.text = subtitle

        val backButton = findViewById<Button>(R.id.btn_back)
        if (backCallback == null) {
            backButton?.isEnabled = false
        } else {
            backButton?.isEnabled = true
            backButton?.setOnClickListener { backCallback() }
        }

        val nextButton = findViewById<Button>(R.id.btn_next)
        if (nextCallback == null) {
            nextButton?.isEnabled = false
        } else {
            nextButton?.isEnabled = true
            nextButton?.setOnClickListener { nextCallback() }
        }

        setContent(null)
    }

    // Support multiple content views ?
    fun setContent(view: View?) {
        val content_view = findViewById<FrameLayout>(R.id.fl_dialog_content)
        if (view == null) {
            content_view.removeAllViews()
        } else {
            content_view.addView(view)
        }
    }
}