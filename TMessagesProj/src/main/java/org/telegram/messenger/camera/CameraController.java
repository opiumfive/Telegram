/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;


public class CameraController {

    private static final int FACING_BACK = 0;
    private static final int FACING_FRONT = 1;
    private static final String FACING_BACK_STR = String.valueOf(FACING_BACK);
    private static final String FACING_FRONT_STR = String.valueOf(FACING_FRONT);

    private static volatile CameraController Instance = null;
    private final ICameraController cameraControllerImp;
    public final boolean isCamera2Used;

    public interface VideoTakeCallback {
        void onFinishVideoRecording(String thumbPath, long duration);
    }

    public static CameraController getInstance() {
        CameraController localInstance = Instance;
        if (localInstance == null) {
            synchronized (CameraController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new CameraController();
                }
            }
        }
        return localInstance;
    }


    @SuppressLint("NewApi")
    public CameraController() {
        isCamera2Used = haveAppropriateCamera2Support();
        cameraControllerImp = isCamera2Used ? new Camera2Controller() : new Camera1Controller();
    }

    public ArrayList<String> getAvailableFlashModes() {
        return cameraControllerImp.availableFlashModes;
    }

    private boolean haveAppropriateCamera2Support() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cameraManager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
            try {
                boolean backSupport = false;
                boolean frontSupport = false;
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(FACING_BACK_STR);
                // check that both cameras mostly have camera2 support
                if (characteristics != null) {
                    int mainSupportData = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    backSupport = mainSupportData == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                        mainSupportData == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED ||
                        mainSupportData == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3;
                }

                characteristics = cameraManager.getCameraCharacteristics(FACING_FRONT_STR);
                if (characteristics != null) {
                    int mainSupportData = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    frontSupport = mainSupportData == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                        mainSupportData == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED ||
                        mainSupportData == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3;
                }
                return backSupport && frontSupport;
            } catch (@SuppressLint("NewApi") CameraAccessException cameraAccessException) {
                cameraAccessException.printStackTrace();
            }
        }
        return false;
    }

    public void cancelOnInitRunnable(final Runnable onInitRunnable) {
        cameraControllerImp.cancelOnInitRunnable(onInitRunnable);
    }

    public void initCamera(final Runnable onInitRunnable) {
        cameraControllerImp.initCamera(onInitRunnable);
    }

    public boolean isCameraInitied() {
        return cameraControllerImp.isCameraInitied();
    }

    public void close(final CameraSession session, final CountDownLatch countDownLatch, final Runnable beforeDestroyRunnable) {
        cameraControllerImp.close(session, countDownLatch, beforeDestroyRunnable);
    }

    public ArrayList<CameraInfo> getCameras() {
        return cameraControllerImp.getCameras();
    }

    public boolean takePicture(final File path, final CameraSession session, final Runnable callback) {
        return cameraControllerImp.takePicture(path, session, callback);
    }

    public void startPreview(final CameraSession session) {
        cameraControllerImp.startPreview(session);
    }

    public void stopPreview(final CameraSession session) {
        cameraControllerImp.stopPreview(session);
    }

    public void openRound(final CameraSession session, final SurfaceTexture texture, final Runnable callback, final Runnable configureCallback) {
        cameraControllerImp.openRound(session, texture, callback, configureCallback);
    }

    public void open(final CameraSession session, final SurfaceTexture texture, final Runnable callback, final Runnable prestartCallback) {
        cameraControllerImp.open(session, texture, callback, prestartCallback);
    }

    public void recordVideo(final CameraSession session, final File path, boolean mirror, final VideoTakeCallback callback, final Runnable onVideoStartRecord) {
        cameraControllerImp.recordVideo(session, path, mirror, callback, onVideoStartRecord);
    }

    public void stopVideoRecording(final CameraSession session, final boolean abandon) {
        cameraControllerImp.stopVideoRecording(session, abandon);
    }

    // could be called on ChatActivity destroy
    public void stopBackgroundThread() {
        cameraControllerImp.stopBackgroundThread();
    }

    public static Size chooseOptimalSize(List<Size> choices, int width, int height, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (int a = 0; a < choices.size(); a++) {
            Size option = choices.get(a);
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(choices, new CompareSizesByArea());
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
