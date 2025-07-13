package blue.eaudouce.waybiker

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment

abstract class MainAppFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {
    var onFinished: ((MainAppFragment) -> Unit)? = null
}