package org.telegram.messenger.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2Controller implements MediaRecorder.OnInfoListener, ICameraController {

    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 1;
    private static final int KEEP_ALIVE_SECONDS = 60;
    protected volatile ArrayList<CameraInfo> cameraInfos;
    CameraService[] availableCameras = null;
    private MediaRecorder recorder;
    private final ThreadPoolExecutor threadPool;
    private final CameraManager cameraManager;
    private final ArrayList<Runnable> onFinishCameraInitRunnables = new ArrayList<>();
    private boolean cameraInitied;
    private boolean loadingCameras;
    private CountDownLatch pictureReadyLatch = null;
    private byte[] takePictureResult = null;
    private boolean mirrorRecorderVideo;
    private CameraController.VideoTakeCallback onVideoTakeCallback;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler = null;
    private String recordedFile;


    public Camera2Controller() {
        cameraManager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
        threadPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public void stopBackgroundThread() {
        if (backgroundThread == null || backgroundHandler == null) return;
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            FileLog.d("camera2 stopBackgroundThread = " + e.getMessage());
        }
    }

    @Override
    public void initCamera(Runnable onInitRunnable) {
        initCamera(onInitRunnable, false);
    }

    private void initCamera(Runnable onInitRunnable, boolean withDelay) {
        if (cameraInitied) {
            if (backgroundThread == null || backgroundHandler == null) startBackgroundThread();
            if (availableCameras.length >= 2) {
                if (availableCameras[0] != null && availableCameras[0].backgroundHandler == null) {
                    availableCameras[0].backgroundHandler = backgroundHandler;
                }

                if (availableCameras[1] != null && availableCameras[1].backgroundHandler == null) {
                    availableCameras[1].backgroundHandler = backgroundHandler;
                }
            } else if (availableCameras.length == 1) {
                if (availableCameras[0] != null && availableCameras[0].backgroundHandler == null) {
                    availableCameras[0].backgroundHandler = backgroundHandler;
                }
            }
            return;
        }
        if (onInitRunnable != null && !onFinishCameraInitRunnables.contains(onInitRunnable)) {
            onFinishCameraInitRunnables.add(onInitRunnable);
        }
        if (loadingCameras || cameraInitied) {
            return;
        }
        loadingCameras = true;

        startBackgroundThread();

        threadPool.execute(() -> {
            try {
                if (cameraInfos == null) {
                    availableCameras = new CameraService[cameraManager.getCameraIdList().length];

                    ArrayList<CameraInfo> result = new ArrayList<>();

                    Comparator<Size> comparator = (o1, o2) -> {
                        if (o1.mWidth < o2.mWidth) {
                            return 1;
                        } else if (o1.mWidth > o2.mWidth) {
                            return -1;
                        } else {
                            if (o1.mHeight < o2.mHeight) {
                                return 1;
                            } else if (o1.mHeight > o2.mHeight) {
                                return -1;
                            }
                            return 0;
                        }
                    };

                    for (String cameraID : cameraManager.getCameraIdList()) {
                        int id = Integer.parseInt(cameraID);
                        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
                        StreamConfigurationMap configurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                        int facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

                        // define proper rotation compensation for made photo
                        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                        int polarity = facing == CameraCharacteristics.LENS_FACING_FRONT ? 1 : -1;
                        WindowManager mgr = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                        int rotation = mgr.getDefaultDisplay().getRotation();
                        long floorDivX = sensorOrientation + polarity * rotation;
                        long floorDivY = 360;
                        long floorDiv = floorDivX / floorDivY;
                        if ((floorDivX ^ floorDivY) < 0 && (floorDiv * floorDivY != floorDivX)) {
                            floorDiv--;
                        }
                        long floorMod = floorDivX - floorDiv * floorDivY;
                        int rotationCompensation = (int) floorMod;

                        if (ApplicationLoader.mainInterfacePaused && ApplicationLoader.externalInterfacePaused) {
                            throw new RuntimeException("APP_PAUSED");
                        }

                        CameraInfo cameraInfo = new CameraInfo(id, facing == CameraCharacteristics.LENS_FACING_FRONT ? 1 : 0);

                        android.util.Size[] list = configurationMap.getOutputSizes(ImageFormat.JPEG);
                        for (int a = 0; a < list.length; a++) {
                            android.util.Size size = list[a];

                            if (size.getHeight() < 2160 && size.getWidth() < 2160) {
                                cameraInfo.previewSizes.add(new Size(size.getWidth(), size.getHeight()));
                                cameraInfo.pictureSizes.add(new Size(size.getWidth(), size.getHeight()));
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("preview size = " + size.getWidth() + " " + size.getHeight());
                                }
                            }
                        }

                        result.add(cameraInfo);

                        Collections.sort(cameraInfo.previewSizes, comparator);
                        Collections.sort(cameraInfo.pictureSizes, comparator);


                        result.add(cameraInfo);

                        availableCameras[id] = new CameraService(cameraManager, cameraID, backgroundHandler, rotationCompensation);
                    }

                    cameraInfos = result;
                }

                AndroidUtilities.runOnUIThread(() -> {
                    loadingCameras = false;
                    cameraInitied = true;
                    if (!onFinishCameraInitRunnables.isEmpty()) {
                        for (int a = 0; a < onFinishCameraInitRunnables.size(); a++) {
                            onFinishCameraInitRunnables.get(a).run();
                        }
                        onFinishCameraInitRunnables.clear();
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.cameraInitied);
                });
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> {
                    onFinishCameraInitRunnables.clear();
                    loadingCameras = false;
                    cameraInitied = false;
                    if (!withDelay && "APP_PAUSED".equals(e.getMessage())) {
                        AndroidUtilities.runOnUIThread(() -> initCamera(onInitRunnable, true), 1000);
                    }
                });
            }
        });
    }

    @Override
    public boolean isCameraInitied() {
        return cameraInitied && cameraInfos != null && !cameraInfos.isEmpty();
    }

    @Override
    public void close(CameraSession session, CountDownLatch countDownLatch, Runnable beforeDestroyRunnable) {
        session.destroy();

        threadPool.execute(() -> {
            if (beforeDestroyRunnable != null) {
                beforeDestroyRunnable.run();
            }

            availableCameras[session.cameraInfo.cameraId].closeCamera();

            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
        });
        if (countDownLatch != null) {
            try {
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public boolean takePicture(File path, CameraSession session, Runnable callback) {
        if (session == null) {
            return false;
        }

        threadPool.execute(() -> {
            pictureReadyLatch = new CountDownLatch(1);
            takePictureResult = null;

            availableCameras[session.cameraInfo.cameraId].makePhoto((photoBytes) -> {
                takePictureResult = photoBytes;

                if (pictureReadyLatch != null) {
                    pictureReadyLatch.countDown();
                }
            });

            if (pictureReadyLatch != null) {
                try {
                    pictureReadyLatch.await(3L, TimeUnit.SECONDS);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            pictureReadyLatch = null;
            availableCameras[session.cameraInfo.cameraId].clearCallback();

            if (takePictureResult == null) return;

            final CameraInfo info = session.cameraInfo;
            final boolean flipFront = session.isFlipFront();
            try {

                Bitmap bitmap = null;
                int size = (int) (AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                String key = String.format(Locale.US, "%s@%d_%d", Utilities.MD5(path.getAbsolutePath()), size, size);
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(takePictureResult, 0, takePictureResult.length, options);
                    float scaleFactor = Math.max((float) options.outWidth / AndroidUtilities.getPhotoSize(), (float) options.outHeight / AndroidUtilities.getPhotoSize());
                    if (scaleFactor < 1) {
                        scaleFactor = 1;
                    }
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = (int) scaleFactor;
                    options.inPurgeable = true;
                    bitmap = BitmapFactory.decodeByteArray(takePictureResult, 0, takePictureResult.length, options);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                try {
                    if (info.frontCamera != 0 && flipFront) {
                        try {
                            Matrix matrix = new Matrix();
                            matrix.setRotate(availableCameras[session.cameraInfo.cameraId].rotationCompensation);
                            matrix.postScale(-1, 1);
                            Bitmap scaled = Bitmaps.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            if (scaled != bitmap) {
                                bitmap.recycle();
                            }
                            FileOutputStream outputStream = new FileOutputStream(path);
                            scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                            outputStream.flush();
                            outputStream.getFD().sync();
                            outputStream.close();
                            AndroidUtilities.runOnUIThread(() -> {
                                if (scaled != null) {
                                    ImageLoader.getInstance().putImageToCache(new BitmapDrawable(scaled), key);
                                }
                                if (callback != null) {
                                    callback.run();
                                }
                            });
                            return;
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }
                    Matrix matrix = new Matrix();
                    matrix.setRotate(availableCameras[session.cameraInfo.cameraId].rotationCompensation);
                    matrix.postScale(1, 1);
                    Bitmap scaled = Bitmaps.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (scaled != bitmap) {
                        bitmap.recycle();
                    }
                    FileOutputStream outputStream = new FileOutputStream(path);
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                    outputStream.flush();
                    outputStream.getFD().sync();
                    outputStream.close();
                    AndroidUtilities.runOnUIThread(() -> {
                        if (scaled != null) {
                            ImageLoader.getInstance().putImageToCache(new BitmapDrawable(scaled), key);
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.run();
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

        return true;
    }

    @Override
    public void startPreview(CameraSession session) {
        if (session == null) {
            return;
        }
        threadPool.execute(() -> {
            availableCameras[session.cameraInfo.cameraId].relaunchPreview();
        });
    }

    @Override
    public void stopPreview(CameraSession session) {
        if (session == null) {
            return;
        }
        threadPool.execute(() -> {
            availableCameras[session.cameraInfo.cameraId].stopPreview();
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void open(CameraSession session, SurfaceTexture texture, Runnable callback, Runnable prestartCallback) {
        if (session == null || texture == null) {
            return;
        }

        threadPool.execute(() -> {
            availableCameras[session.cameraInfo.cameraId].openCamera(texture);

            if (prestartCallback != null) {
                prestartCallback.run();
            }

            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback);
            }
        });
    }

    @Override
    public void openRound(CameraSession session, SurfaceTexture texture, Runnable callback, Runnable configureCallback) {

        if (session == null || texture == null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("failed to open round " + session + " tex = " + texture);
            }
            return;
        }

        threadPool.execute(() -> {
            try {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("start creating round camera session");
                }
                availableCameras[session.cameraInfo.cameraId].openCamera(texture);

                //session.configureRoundCamera();
                if (configureCallback != null) {
                    configureCallback.run();
                }

                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("round camera session created");
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    @Override
    public void recordVideo(final CameraSession session, final File path, boolean mirror, final CameraController.VideoTakeCallback callback, final Runnable onVideoStartRecord) {
        if (session == null) {
            return;
        }

        final CameraInfo info = session.cameraInfo;

        threadPool.execute(() -> {
            try {
                mirrorRecorderVideo = mirror;
                recorder = new MediaRecorder();
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                session.configureRecorder(1, recorder);
                recorder.setOutputFile(path.getAbsolutePath());
                recorder.setMaxFileSize(1024 * 1024 * 1024);
                recorder.setVideoFrameRate(30);
                recorder.setMaxDuration(0);
                Size pictureSize;
                pictureSize = new Size(16, 9);
                pictureSize = CameraController.chooseOptimalSize(info.getPictureSizes(), 720, 480, pictureSize);
                int bitrate;
                if (Math.min(pictureSize.mHeight, pictureSize.mWidth) >= 720) {
                    bitrate = 3500000;
                } else {
                    bitrate = 1800000;
                }
                recorder.setVideoEncodingBitRate(bitrate);
                recorder.setVideoSize(pictureSize.getWidth(), pictureSize.getHeight());
                recorder.setOnInfoListener(Camera2Controller.this);

                recorder.prepare();
                availableCameras[session.cameraInfo.cameraId].recordVideo(recorder);
                recorder.start();

                onVideoTakeCallback = callback;
                recordedFile = path.getAbsolutePath();
                if (onVideoStartRecord != null) {
                    AndroidUtilities.runOnUIThread(onVideoStartRecord);
                }
            } catch (Exception e) {
                recorder.release();
                recorder = null;
                FileLog.e(e);
            }
        });
    }

    @Override
    public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN) {
            MediaRecorder tempRecorder = recorder;
            recorder = null;
            if (tempRecorder != null) {
                tempRecorder.stop();
                tempRecorder.release();
            }
            if (onVideoTakeCallback != null) {
                finishRecordingVideo();
            }
        }
    }

    private void finishRecordingVideo() {
        MediaMetadataRetriever mediaMetadataRetriever = null;
        long duration = 0;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(recordedFile);
            String d = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) {
                duration = (int) Math.ceil(Long.parseLong(d) / 1000.0f);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        Bitmap bitmap = SendMessagesHelper.createVideoThumbnail(recordedFile, MediaStore.Video.Thumbnails.MINI_KIND);
        if (mirrorRecorderVideo) {
            Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            canvas.scale(-1, 1, b.getWidth() / 2, b.getHeight() / 2);
            canvas.drawBitmap(bitmap, 0, 0, null);
            bitmap.recycle();
            bitmap = b;
        }
        String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
        final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
        try {
            FileOutputStream stream = new FileOutputStream(cacheFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        SharedConfig.saveConfig();
        final long durationFinal = duration;
        final Bitmap bitmapFinal = bitmap;
        AndroidUtilities.runOnUIThread(() -> {
            if (onVideoTakeCallback != null) {
                String path = cacheFile.getAbsolutePath();
                if (bitmapFinal != null) {
                    ImageLoader.getInstance().putImageToCache(new BitmapDrawable(bitmapFinal), Utilities.MD5(path));
                }
                onVideoTakeCallback.onFinishVideoRecording(path, durationFinal);
                onVideoTakeCallback = null;
            }
        });
    }

    @Override
    public void stopVideoRecording(CameraSession session, boolean abandon) {
        threadPool.execute(() -> {
            if (recorder != null) {
                MediaRecorder tempRecorder = recorder;
                recorder = null;
                try {
                    tempRecorder.stop();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    tempRecorder.release();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                availableCameras[session.cameraInfo.cameraId].stopVideo();
                try {
                    session.stopVideoRecording();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (!abandon && onVideoTakeCallback != null) {
                finishRecordingVideo();
            } else {
                onVideoTakeCallback = null;
            }
        });
    }

    @Override
    public void cancelOnInitRunnable(Runnable onInitRunnable) {
        onFinishCameraInitRunnables.remove(onInitRunnable);
    }

    @Override
    public ArrayList<CameraInfo> getCameras() {
        return cameraInfos;
    }

    interface PhotoTakeCallback {
        void onPhotoTaken(byte[] photoBytes);
    }

    public static class CameraService {

        final private String cameraId;
        final private CameraManager cameraManager;
        final private int rotationCompensation;

        private CameraDevice cameraDevice = null;
        private CameraCaptureSession captureSession;
        private ImageReader imageReader;
        private MediaRecorder mediaRecorder;
        private PhotoTakeCallback callback;private Surface surface;
        private Handler backgroundHandler;

        private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {

            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                if (callback != null) {
                    callback.onPhotoTaken(bytes);
                }
            }

        };

        private final CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {

            @Override
            public void onOpened(CameraDevice camera) {
                cameraDevice = camera;
                createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                cameraDevice.close();
                cameraDevice = null;
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                FileLog.d("camera2 open error = " + error);
            }
        };

        public CameraService(CameraManager cameraManager, String cameraID, Handler backgroundHandler, int rotationCompensation) {
            this.cameraManager = cameraManager;
            this.cameraId = cameraID;
            this.backgroundHandler = backgroundHandler;
            this.rotationCompensation = rotationCompensation;
        }

        public void relaunchPreview() {
            try {
                final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                builder.addTarget(surface);

                if (mediaRecorder != null) {
                    Surface recorderSurface = mediaRecorder.getSurface();
                    builder.addTarget(recorderSurface);
                }

                List<Surface> sessionSurfaces = mediaRecorder == null ? Arrays.asList(surface, imageReader.getSurface()) : Arrays.asList(surface, imageReader.getSurface(), mediaRecorder.getSurface());

                cameraDevice.createCaptureSession(sessionSurfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                        }
                    }, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private void createCameraPreviewSession() { // width and height could be taken from camera params
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
            relaunchPreview();
        }

        public void stopPreview() {
            try {
                captureSession.stopRepeating();
                captureSession.abortCaptures();
                captureSession.close();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        public void makePhoto(PhotoTakeCallback photoTakeCallback) {
            try {
                final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(imageReader.getSurface());
                CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {
                    }
                };

                captureSession.stopRepeating();
                captureSession.abortCaptures();
                captureSession.capture(captureBuilder.build(), CaptureCallback, backgroundHandler);
                callback = photoTakeCallback;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        public void stopVideo() {
            try {
                captureSession.stopRepeating();
                captureSession.abortCaptures();
                captureSession.close();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            this.mediaRecorder = null;
        }

        public void recordVideo(MediaRecorder mediaRecorder) {
            this.mediaRecorder = mediaRecorder;
            relaunchPreview();
        }

        public void clearCallback() {
            callback = null;
        }

        public void setBackgroundHandler(Handler backgroundHandler) {
            this.backgroundHandler = backgroundHandler;
        }

        public boolean isOpen() {
            return cameraDevice != null;
        }

        @SuppressLint("MissingPermission")
        public void openCamera(SurfaceTexture texture) {
            surface = new Surface(texture);
            try {
                cameraManager.openCamera(cameraId, cameraCallback, backgroundHandler);
            } catch (CameraAccessException e) {
                FileLog.d("openCamera error = " + e.getMessage());
            }
        }


        public void closeCamera() {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    }
}
