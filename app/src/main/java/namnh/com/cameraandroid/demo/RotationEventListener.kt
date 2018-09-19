package namnh.com.cameraandroid.demo

import android.content.Context
import android.view.OrientationEventListener

abstract class RotationEventListener(context: Context) : OrientationEventListener(context) {

    private var lastOrientation = 0

    override fun onOrientationChanged(orientation: Int) {
        if (orientation < 0) {
            return  // Flip screen, Not take account
        }

        val curOrientation: Int = when (orientation) {
            in 0..30, in 330..360 -> ORIENTATION_PORTRAIT
            in 60..120 -> ORIENTATION_LANDSCAPE_REVERSE
            in 150..210 -> ORIENTATION_PORTRAIT_REVERSE
            in 240..300 -> ORIENTATION_LANDSCAPE
            else -> lastOrientation
        }

        if (curOrientation != lastOrientation) {
            onChanged(lastOrientation, curOrientation)
            lastOrientation = curOrientation
        }
    }


    private fun onChanged(lastOrientation: Int, orientation: Int) {
        val startDeg = if (lastOrientation == 0)
            if (orientation == 3) 360 else 0
        else if (lastOrientation == 1)
            90
        else if (lastOrientation == 2)
            180
        else
            270 // don't know how, but it works
        val endDeg = if (orientation == 0)
            if (lastOrientation == 1) 0 else 360
        else if (orientation == 1)
            90
        else if (orientation == 2)
            180
        else
            270 // don't know how, but it works

        onRotateChanged(startDeg, endDeg)
    }

    abstract fun onRotateChanged(startDeg: Int, endDeg: Int)

    companion object {
        const val ORIENTATION_PORTRAIT = 0
        const val ORIENTATION_LANDSCAPE = 1
        const val ORIENTATION_PORTRAIT_REVERSE = 2
        const val ORIENTATION_LANDSCAPE_REVERSE = 3
    }
}
