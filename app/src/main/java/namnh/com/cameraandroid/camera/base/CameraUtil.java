package namnh.com.cameraandroid.camera.base;

import android.media.CamcorderProfile;
import android.os.Build;
import android.support.annotation.NonNull;

public class CameraUtil {
    /**
     * Find the best camera recorder profile via camera id
     *
     * @param videoQuality the video quality
     * @param cameraId the camera id
     */
    @NonNull
    public static CamcorderProfile getCamcorderProfile(VideoQuality videoQuality, int cameraId) {
        switch (videoQuality) {
            case HIGHEST:
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            case MAX_2160P:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_2160P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P);
                }
                // Don't break.
            case MAX_1080P:
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
                }
                // Don't break.
            case MAX_720P:
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
                }
                // Don't break.
            case MAX_480P:
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
                }
                // Don't break.
            case MAX_QVGA:
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA);
                }
                // Don't break.
            case LOWEST:
            default:
                // Fallback to lowest.
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
        }
    }
}
