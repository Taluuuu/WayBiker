package blue.eaudouce.waybiker.home

import android.content.Context
import android.widget.FrameLayout
import blue.eaudouce.waybiker.home.MapAction_RateStreet.RatingState
import blue.eaudouce.waybiker.map.WaybikerMap

class MapAction_RecordTrack(
    private val waybikerMap: WaybikerMap,
    private val tabContentView: FrameLayout
) : MapAction_Base() {

    private var dialogView: MapDialogView? = null

    private enum class RecordState {
        Inactive,
        Intro,
        Recording,
        PostRecording
    }

    private var currentState = RecordState.Inactive

    private fun setState(newState: RecordState) {
        if (currentState == newState)
            return

        // Exit previous state logic
        when (currentState) {
            RecordState.Inactive -> {

            }
            RecordState.Intro -> {

            }
            RecordState.Recording -> {

            }
            RecordState.PostRecording -> {

            }
        }

        currentState = newState

        // Enter new state logic
        when (currentState) {
            RecordState.Inactive -> {

            }
            RecordState.Intro -> {
                dialogView?.updateDialog(
                    "Record and save a track",
                    "",
                    { finishAction() },
                    { setState(RecordState.Recording) }
                )
            }
            RecordState.Recording -> {
                dialogView?.updateDialog(
                    "Recording...",
                    "",
                    { setState(RecordState.Intro) },
                    { setState(RecordState.PostRecording) }
                )
            }
            RecordState.PostRecording -> {
                dialogView?.updateDialog(
                    "Save this track?",
                    "You will be notified if there is a change in the condition of this track.",
                    { finishAction() },
                    { finishAction() }
                )
            }
        }
    }

    override fun start(context: Context?) {
        if (context != null) {
            dialogView = MapDialogView(context)
            tabContentView.addView(dialogView)
        }

        setState(RecordState.Intro)
    }

    override fun finishAction() {
        setState(RecordState.Inactive)
        tabContentView.removeView(dialogView)
        super.finishAction()
    }

}