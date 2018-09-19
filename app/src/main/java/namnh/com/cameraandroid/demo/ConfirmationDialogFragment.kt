package namnh.com.cameraandroid.demo

import android.app.Dialog
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.widget.Toast

class ConfirmationDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments
        if (activity == null || args == null) {
            throw IllegalStateException("Activity is destroyed !")
        }
        val activity = activity!!
        return AlertDialog.Builder(activity).setMessage(args.getInt(
            ARG_MESSAGE))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val permissions = args.getStringArray(
                    ARG_PERMISSIONS)
                    ?: throw IllegalArgumentException()
                ActivityCompat.requestPermissions(activity, permissions,
                    args.getInt(
                        ARG_REQUEST_CODE))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                Toast.makeText(activity, args.getInt(
                    ARG_NOT_GRANTED_MESSAGE),
                    Toast.LENGTH_SHORT).show()
            }
            .create()
    }


    companion object {

        private const val ARG_MESSAGE = "message"
        private const val ARG_PERMISSIONS = "permissions"
        private const val ARG_REQUEST_CODE = "request_code"
        private const val ARG_NOT_GRANTED_MESSAGE = "not_granted_message"

        fun newInstance(@StringRes message: Int, permissions: Array<String>,
            requestCode: Int, @StringRes notGrantedMessage: Int): ConfirmationDialogFragment {
            val fragment = ConfirmationDialogFragment()
            val args = Bundle()
            args.putInt(
                ARG_MESSAGE, message)
            args.putStringArray(
                ARG_PERMISSIONS, permissions)
            args.putInt(
                ARG_REQUEST_CODE, requestCode)
            args.putInt(
                ARG_NOT_GRANTED_MESSAGE, notGrantedMessage)
            fragment.arguments = args
            return fragment
        }
    }
}