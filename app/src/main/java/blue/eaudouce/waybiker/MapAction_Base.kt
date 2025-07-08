package blue.eaudouce.waybiker

import android.content.Context

abstract class MapAction_Base {

    var onFinished: (() -> (Unit))? = null

    abstract fun start(context: Context?)

    protected open fun finishAction() {
        onFinished?.invoke()
    }
}