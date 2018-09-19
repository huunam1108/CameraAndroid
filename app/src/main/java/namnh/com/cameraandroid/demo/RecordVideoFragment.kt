package namnh.com.cameraandroid.demo

import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_record_video.*
import namnh.com.cameraandroid.R
import namnh.com.cameraandroid.camera.CameraView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordVideoFragment : Fragment() {

    private var isRecordingVideo = false
    private var rotationAngle = 0
    private lateinit var rotationEventListener: RotationEventListener

    companion object {
        val TAG: String = RecordVideoFragment::class.java.simpleName
        fun newInstance() = RecordVideoFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rotationEventListener = object : RotationEventListener(requireContext()) {
            override fun onRotateChanged(startDeg: Int, endDeg: Int) {
                doOnRotateChanged(startDeg, endDeg)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_record_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btn_back.setOnClickListener {
            activity?.onBackPressed()
        }

        btn_rotate_camera.setOnClickListener {
            camera.facing = if (camera.facing == CameraView.Facing.FACING_FRONT) {
                btn_rotate_camera.setImageResource(R.drawable.ic_camera_rear)
                CameraView.Facing.FACING_BACK
            } else {
                btn_rotate_camera.setImageResource(R.drawable.ic_camera_front)
                CameraView.Facing.FACING_FRONT
            }
        }

        btn_flash.setOnClickListener {
            if (!camera.isCameraOpened) return@setOnClickListener

            when (camera.flash) {
                CameraView.Flash.FLASH_OFF -> {
                    camera.flash = CameraView.Flash.FLASH_TORCH
                    btn_flash.setImageResource(R.drawable.ic_flash_on)
                }
                else -> {
                    camera.flash = CameraView.Flash.FLASH_OFF
                    btn_flash.setImageResource(R.drawable.ic_flash_off)
                }
            }

        }

        btn_record.setOnClickListener {
            isRecordingVideo = if (!isRecordingVideo) {
                val videoFile = getVideoFile()
                camera.starRecordingVideo(videoFile, rotationAngle, true)
                btn_record.setImageResource(R.drawable.ic_recording)
                text_time_recorded.base = SystemClock.elapsedRealtime()
                text_time_recorded.start()
                btn_rotate_camera.visibility = View.GONE
                btn_back.visibility = View.GONE
                removeOrientationCallback() // when recording is started, stop user change camera orientation
                true
            } else {
                camera.stopRecordingVideo()
                text_time_recorded.base = SystemClock.elapsedRealtime()
                text_time_recorded.stop()
                btn_rotate_camera.visibility = View.VISIBLE
                btn_back.visibility = View.VISIBLE
                btn_record.setImageResource(R.drawable.ic_record)
                false
            }
        }
        camera.addCallback(object : CameraView.Callback() {
            override fun onVideoRecorded(cameraView: CameraView?, videoFile: File?) {
                super.onVideoRecorded(cameraView, videoFile)
                Toast.makeText(context, "Video is recorded at : " + videoFile?.absolutePath,
                        Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun getVideoFile(): File? {
        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), getString(R.string.app_name))
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) return null
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        return File(mediaStorageDir.path + File.separator + "VID_" + timeStamp + ".mp4")
    }

    override fun onResume() {
        super.onResume()
        camera.start()
        addOrientationCallback()
    }

    override fun onPause() {
        camera.stop()
        removeOrientationCallback()
        super.onPause()
    }


    private fun addOrientationCallback() {
        if (rotationEventListener.canDetectOrientation()) {
            rotationEventListener.enable()
        }
    }

    private fun removeOrientationCallback() {
        if (rotationEventListener.canDetectOrientation()) {
            rotationEventListener.disable()
        }
    }

    private fun doOnRotateChanged(startDeg: Int, endDeg: Int) {
        btn_rotate_camera.apply {
            rotation = startDeg.toFloat()
            animate().rotation(endDeg.toFloat()).start()
        }

        fl_top_bar.apply {
            val oldWidth = width
            val oldHeight = height
            val x: Float
            val y: Float
            val newParams = layoutParams as FrameLayout.LayoutParams
            if (endDeg == 90 || endDeg == 270) {
                newParams.width = layout_container.height
                newParams.height = layout_container.width
                x = (newParams.width - oldWidth) / 2f
                y = (newParams.height - oldHeight) / 2f
            } else {
                newParams.width = layout_container.width
                newParams.height = layout_container.height
                x = 0f
                y = if (endDeg == 180) (newParams.height - top_bar.height).toFloat() else 0f
            }
            layoutParams = newParams
            rotation = startDeg.toFloat()
            animate().rotation(endDeg.toFloat()).translationX(-x).translationY(-y).start()
        }
        rotationAngle = endDeg
    }

}
