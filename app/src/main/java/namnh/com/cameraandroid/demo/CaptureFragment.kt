package namnh.com.cameraandroid.demo

import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_capture.*
import namnh.com.cameraandroid.R
import namnh.com.cameraandroid.camera.CameraView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


class CaptureFragment : Fragment() {

    companion object {
        val TAG: String = CaptureFragment::class.java.simpleName
        fun newInstance() = CaptureFragment()
    }

    private var backgroundHandler: Handler? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_capture, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btn_back.setOnClickListener {
            activity?.onBackPressed()
        }

        img_result.setOnClickListener {
            it.visibility = View.GONE
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
        btn_take_picture.setOnClickListener {
            camera.takePicture()
        }

        camera.addCallback(object : CameraView.Callback() {
            override fun onPictureTaken(cameraView: CameraView?, data: ByteArray?) {
                super.onPictureTaken(cameraView, data)
                Log.d(TAG, "onPictureTaken " + data?.size)
                val file = getPictureFile()
                Toast.makeText(context, "Picture is taken ! ${file?.path}", Toast.LENGTH_SHORT).show()
                getBackgroundHandler()?.post {
                    var os: OutputStream? = null
                    try {
                        os = FileOutputStream(file)
                        os.write(data)
                    } catch (e: IOException) {
                        Log.e(TAG, "Can not write taken picture !")
                    } finally {
                        os?.close()
                    }
                }
            }
        })
    }

    private fun getBackgroundHandler(): Handler? {
        if (backgroundHandler == null) {
            val thread = HandlerThread("background")
            thread.start()
            backgroundHandler = Handler(thread.looper)
        }
        return backgroundHandler
    }


    private fun getPictureFile(): File? {
        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), getString(R.string.app_name))
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) return null
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        return File(mediaStorageDir.path + File.separator + "PIC_" + timeStamp + ".jpg")
    }

    override fun onResume() {
        super.onResume()
        camera.start()
    }

    override fun onPause() {
        camera.stop()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (backgroundHandler != null) {
            backgroundHandler?.looper?.quit()
            backgroundHandler = null
        }
    }
}
