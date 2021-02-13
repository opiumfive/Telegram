package org.telegram.messenger.camera;

import android.graphics.SurfaceTexture;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public interface ICameraController {

    ArrayList<String> availableFlashModes = new ArrayList<>();

    void initCamera(final Runnable onInitRunnable);

    boolean isCameraInitied();

    void close(final CameraSession session, final CountDownLatch countDownLatch, final Runnable beforeDestroyRunnable);

    boolean takePicture(final File path, final CameraSession session, final Runnable callback);

    void startPreview(final CameraSession session);

    void stopPreview(final CameraSession session);

    void open(final CameraSession session, final SurfaceTexture texture, final Runnable callback, final Runnable prestartCallback);

    void openRound(final CameraSession session, final SurfaceTexture texture, final Runnable callback, final Runnable configureCallback);

    void recordVideo(final CameraSession session, final File path, boolean mirror, final CameraController.VideoTakeCallback callback, final Runnable onVideoStartRecord);

    void stopVideoRecording(final CameraSession session, final boolean abandon);

    void cancelOnInitRunnable(final Runnable onInitRunnable);

    void stopBackgroundThread();

    ArrayList<CameraInfo> getCameras();
}
