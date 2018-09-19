package namnh.com.cameraandroid.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import namnh.com.cameraandroid.R


class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CODE_PERMISSION = 1
        const val FRAGMENT_DIALOG = "dialog"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!hasPermissions()) {
            requestPermission()
            return
        }

        addFragment()
    }

    private fun addFragment() {
        if (supportFragmentManager.findFragmentByTag(MainFragment.TAG) == null) {
            supportFragmentManager.beginTransaction().replace(R.id.container,
                MainFragment.newInstance(), MainFragment.TAG).commit()
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)
            || ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)
            || ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) {
            ConfirmationDialogFragment.newInstance(
                R.string.camera_permission_confirmation,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSION,
                R.string.camera_permission_not_granted)
                .show(supportFragmentManager,
                    FRAGMENT_DIALOG)
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
        grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_PERMISSION -> {
                if (permissions.size != 3 || grantResults.size != 3) {
                    throw RuntimeException("Error on requesting camera permission.")
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED
                    || grantResults[1] != PackageManager.PERMISSION_GRANTED
                    || grantResults[2] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                        R.string.camera_permission_not_granted,
                        Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    addFragment()
                }
            }
        }
    }
}
