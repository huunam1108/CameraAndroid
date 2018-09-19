package namnh.com.cameraandroid.demo

import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_main.*
import namnh.com.cameraandroid.R
import namnh.com.cameraandroid.camera.CameraView

class MainFragment : Fragment() {

    companion object {
        val TAG: String = MainFragment::class.java.simpleName
        fun newInstance() = MainFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btn_close.setOnClickListener {
            activity?.finish()
        }

        expand.setOnClickListener {
            appbar.setExpanded(true)
        }

        layout_take_picture.setOnClickListener {
            val captureFragment = CaptureFragment.newInstance()
            val ft = fragmentManager?.beginTransaction()
            ft?.let {
                ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                    android.R.anim.fade_in, android.R.anim.fade_out)
                ft.replace(R.id.container, captureFragment,
                    CaptureFragment.TAG)?.addToBackStack(null)?.commit()
            }
        }

        layout_record.setOnClickListener {
            val recordVideoFragment = RecordVideoFragment.newInstance()
            val ft = fragmentManager?.beginTransaction()
            ft?.let {
                ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                    android.R.anim.fade_in, android.R.anim.fade_out)
                ft.replace(R.id.container, recordVideoFragment,
                    RecordVideoFragment.TAG)?.addToBackStack(null)?.commit()
            }
        }

        appbar.addOnOffsetChangedListener { _, verticalOffset ->
            val percentage = Math.abs(verticalOffset).toFloat() / appbar.totalScrollRange
            // update transparent of some views
            expand.alpha = percentage
            camera.alpha = 1 - percentage / 2f
            center_view.alpha = 1 - percentage / 2f
            /*if (verticalOffset != 0 && recycler.paddingBottom == 0) {
                // Trick for resolving recycler last item is cut off
                recycler.setPadding(0, 0, 0, 1)
            }*/
        }

        camera.addCallback(object : CameraView.Callback() {
            override fun onCameraOpened(cameraView: CameraView?) {
                super.onCameraOpened(cameraView)
                Log.d(TAG, "onCameraOpened")
            }

            override fun onCameraClosed(cameraView: CameraView?) {
                super.onCameraClosed(cameraView)
                Log.d(TAG, "onCameraClosed")
            }

        })
    }

    override fun onResume() {
        super.onResume()
        camera.start()
    }

    override fun onPause() {
        super.onPause()
        camera.stop()
    }

}