package org.telegram.ui.Components.spoilers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLES31;
import android.opengl.GLUtils;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class DeleteEffectView {

    public static final int ANIMATION_TIME_MS = 2000;
    public static float ANIMATION_PERFORMANCE_FACTOR = 1.0f;
    public static float ANIMATION_PERFORMANCE_FACTOR2 = 1.0f;

    private final ChatActivity activity;
    private ArrayList<DrawingObject> drawingObjects = new ArrayList<>();
    private FrameLayout contentLayout;
    private RecyclerListView listView;
    private long dialogId;
    private boolean attached;
    int ddDize = 500;
    private float getSize() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 1f;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return 2f;
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return 2f;
        }
    }

    private float getSize2() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return AndroidUtilities.dpf2(0.8f);
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return AndroidUtilities.dp(1f);
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return AndroidUtilities.dp(1.2f);
        }
    }

    public DeleteEffectView(ChatActivity activity, RecyclerListView chatListView, long dialogId) {
        this.activity = activity;
        contentLayout = activity.getContentView();
        listView = chatListView;
        this.dialogId = dialogId;
        ANIMATION_PERFORMANCE_FACTOR = getSize();
        ANIMATION_PERFORMANCE_FACTOR2 = getSize2();
    }

    public void onScrolled(int dy) {
        if (isCurrentlyPlaying()) {
            for (int i = 0; i < drawingObjects.size(); i++) {
                if (!drawingObjects.get(i).wasPlayed) {
                    drawingObjects.get(i).sv.setTranslationY(drawingObjects.get(i).sv.getTranslationY() - dy);
                }
            }
        }
    }

    public boolean isCurrentlyPlaying() {
        if (drawingObjects.isEmpty()) return false;
        for (int i = 0; i < drawingObjects.size(); i++) {
            if (!drawingObjects.get(i).wasPlayed) return true;
        }
        return false;
    }

    public void onAttachedToWindow() {
        attached = true;
    }

    public void onDetachedFromWindow() {
        attached = false;

        if (isCurrentlyPlaying()) {
            cancelAllAnimations();
        }
    }


    public void draw(Canvas canvas) {
        if (!attached) return;
        if (!drawingObjects.isEmpty()) {
            for (int k = 0; k < drawingObjects.size(); k++) {
                DrawingObject drawingObject = drawingObjects.get(k);
                if (drawingObject.wasPlayed) continue;

                long curTime = System.currentTimeMillis();
                float progress = 1f * (curTime - drawingObject.startTime) / ANIMATION_TIME_MS;

                if (progress >= 1) {
                    drawingObject.wasPlayed = true;
                    drawingObject.sv.stop();
                    contentLayout.removeView(drawingObject.sv);
                    drawingObject.sv = null;
                    drawingObjects.remove(k);
                    k--;
                    continue;
                }

                float bmpPL = progressLimit + 0.05f;
                if (progress < bmpPL) {
                    rect2.left =drawingObject.place.left + (int) (drawingObject.place.width() * progress / bmpPL);
                    rect2.top = drawingObject.place.top;
                    rect2.bottom = drawingObject.place.bottom;
                    rect2.right = drawingObject.place.right;
                    //canvas.drawBitmap(drawingObject.viewBmp, drawingObject.place, drawingObject.place, bmpPaint);
                }
            }
            contentLayout.invalidate();
        }
    }

    private Canvas mCanvas = new Canvas();
    private Paint bmpPaint = new Paint();
    private Paint paint = new Paint();
    Rect rect2 = new Rect();
    float progressLimit = 0.5f;

    public boolean showAnimationForCells(List<CellDeleteRequest> cells) {
        //if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
        //    return false;
        //}

        //AndroidUtilities.runOnUIThread(() -> {

            int minLeft = 10000;
            int minTop = 10000;
            int maxRight = 0;
            int maxBottom = 0;
            for (int i = 0; i < cells.size(); i++) {
                CellDeleteRequest cellDeleteRequest = cells.get(i);
                if (cellDeleteRequest.location[0] < minLeft) minLeft = cellDeleteRequest.location[0];
                if (cellDeleteRequest.location[1] < minTop) minTop = cellDeleteRequest.location[1];
                if (cellDeleteRequest.location[0] + cellDeleteRequest.cell.getWidth() > maxRight) {
                    maxRight = (int) cellDeleteRequest.location[0] + cellDeleteRequest.cell.getWidth();
                }
                if (cellDeleteRequest.location[1] + cellDeleteRequest.cell.getHeight() > maxBottom) {
                    maxBottom = (int) cellDeleteRequest.location[1] + cellDeleteRequest.cell.getHeight();
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(maxRight - minLeft, maxBottom - minTop, Bitmap.Config.ARGB_8888);
            if (bitmap == null) return false;

            synchronized (mCanvas) {
                Canvas canvas = mCanvas;
                canvas.setBitmap(bitmap);
                for (int i = 0; i < cells.size(); i++) {
                    CellDeleteRequest cellDeleteRequest = cells.get(i);
                    canvas.save();
                    canvas.translate(cellDeleteRequest.location[0] - minLeft, cellDeleteRequest.location[1] - minTop);
                    cellDeleteRequest.cell.draw(canvas);
                    canvas.restore();
                }
                canvas.setBitmap(null);
            }

            Rect place = new Rect(minLeft, minTop, bitmap.getWidth(), bitmap.getHeight());

            DrawingObject drawingObject = new DrawingObject();
            drawingObject.viewBmp = bitmap;
            drawingObject.place = place;
            drawingObject.startTime = System.currentTimeMillis();

            DissolveEffectTextureView ps = new DissolveEffectTextureView(contentLayout.getContext());
            contentLayout.addView(ps, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            ps.init();
            ps.setBitmap(bitmap, place.left, place.top);
            drawingObject.sv = ps;

            drawingObjects.add(drawingObject);
            contentLayout.invalidate();
        //});

        return false;
    }

    public void cancelAllAnimations() {
        if (drawingObjects.isEmpty()) return;
        try {
            for (int i = 0; i < drawingObjects.size(); i++) {
                DrawingObject drawingObject = drawingObjects.get(i);
                if (!drawingObject.wasPlayed) {
                    drawingObject.wasPlayed = true;
                    drawingObject.sv.stop();
                    contentLayout.removeView(drawingObject.sv);
                    drawingObject.sv = null;
                    drawingObjects.remove(i);
                    i--;
                }
            }
        } catch (Exception e) {
        }
    }

    private static class DrawingObject {
        Bitmap viewBmp;
        Rect place;
        DissolveEffectTextureView sv;
        long startTime;
        boolean wasPlayed;
    }

    public static class CellDeleteRequest {
        public ChatMessageCell cell;
        public int[] location;
        public boolean isGroup;
        public long groupId = 0L;
    }

    private static class DissolveEffectTextureView extends FrameLayout {

        public Runnable invalidate;

        public DissolveEffectTextureView.RenderThread thread = new DissolveEffectTextureView.RenderThread();

        public DissolveEffectTextureView(Context context) {
            super(context);
            initView();
        }

        private void initView() {
            setWillNotDraw(false);
        }

        public void setInvalidate(Runnable runnable) {
            this.invalidate = runnable;
        }

        public void setBitmap(final Bitmap bmp, int x, int y) {
            if (thread != null && bmp != null) {
                float pp = ANIMATION_PERFORMANCE_FACTOR;
                if (bmp.getHeight() <= AndroidUtilities.dp(80)) {
                    //pp = 1f;
                }
                thread.setup(new DissolveEffectTextureView.EffectSettings(bmp, bmp.getWidth(), bmp.getHeight(), x, y, ANIMATION_PERFORMANCE_FACTOR2, ANIMATION_TIME_MS, pp));
            }
        }

        public void init() {
            TextureView textureView = new TextureView(getContext());

            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
                }

                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i2) {
                    if (thread != null) {
                        thread.onSurfaceTextureAvailable(surfaceTexture);
                        thread.setSize(i, i2);
                        thread.setInvalidate(() -> {
                            if (invalidate != null) {
                                invalidate.run();
                            }
                        });
                        thread.start();
                    }
                }

                public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i2) {
                    if (thread != null) {
                        thread.setSize(i, i2);
                    }
                }

                public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                    if (thread != null) {
                        thread.onSurfaceTextureDestroyed();
                    }
                    return true;
                }
            });
            textureView.setOpaque(false);
            addView(textureView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        public void stop() {
            if (thread != null) {
                thread.onSurfaceTextureDestroyed();
                thread = null;
            }
        }

        private static class EffectRenderer {
            private int currentBuffer = 0;
            private DissolveEffectTextureView.EffectSettings effectSettings = null;
            private int width;
            private int height;
            private boolean needUpdateSize = false;
            private int[] particlesData;
            private int program;
            private boolean reset;
            private int textureHandle;
            private int dampingMultHandle;
            private int deltaTimeHandle;
            private int pointsCountHandle;
            private int pointsOffsetHandle;
            private int pointsSizeHandle;
            private int forceMultHandle;
            private int longevityHandle;
            private int maxVelocityHandle;
            private int noiseMovementHandle;
            private int noiseScaleHandle;
            private int noiseSpeedHandle;
            private int radiusHandle;
            private int resetHandle;
            private int seedHandle;
            private int sizeHandle;
            private int timeHandle;
            private int velocityMultHandle;
            private int textureId = -1;


            private void genParticlesData() {
                if (effectSettings == null) return;

                if (particlesData != null) {
                    GLES31.glDeleteBuffers(2, particlesData, 0);
                }

                particlesData = new int[2];
                GLES31.glGenBuffers(2, particlesData, 0);

                for (int i = 0; i < 2; ++i) {
                    GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, particlesData[i]);
                    GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, (int)(4 * (6 * this.effectSettings.getCount())), null, GLES31.GL_DYNAMIC_DRAW);
                }
            }

            public void drawParticles(float f, float f2) {
                if (this.needUpdateSize) {
                    GLES31.glViewport((int)0, (int)0, (int)this.width, (int)this.height);
                    this.needUpdateSize = false;
                }
                if (this.reset) {
                    if (this.reset) {
                        GLES31.glUniform2f(pointsCountHandle, this.effectSettings.getCountHorizontal(), this.effectSettings.getCountVertical());
                        GLES31.glUniform2f(pointsOffsetHandle, (float)this.effectSettings.getOffsetX() / (float)this.width, (float)this.effectSettings.getOffsetY() / (float)this.height);
                        GLES31.glUniform2f(pointsSizeHandle, (float)this.effectSettings.getWidth() / (float)this.width, (float)this.effectSettings.getHeight() / (float)this.height);
                    }
                }
                GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);
                GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, particlesData[currentBuffer]);
                GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 24, 0); // Position (vec2)
                GLES31.glEnableVertexAttribArray(0);
                GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 24, 8); // Velocity (vec2)
                GLES31.glEnableVertexAttribArray(1);
                GLES31.glVertexAttribPointer(2, 1, GLES31.GL_FLOAT, false, 24, 16); // Time (float)
                GLES31.glEnableVertexAttribArray(2);
                GLES31.glVertexAttribPointer(3, 1, GLES31.GL_FLOAT, false, 24, 20); // Duration (float)
                GLES31.glEnableVertexAttribArray(3);
                GLES31.glBindBufferBase(GLES31.GL_TRANSFORM_FEEDBACK_BUFFER, 0, particlesData[1 - currentBuffer]);
                GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 24, 0); // Position (vec2)
                GLES31.glEnableVertexAttribArray(0);
                GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 24, 8); // Velocity (vec2)
                GLES31.glEnableVertexAttribArray(1);
                GLES31.glVertexAttribPointer(2, 1, GLES31.GL_FLOAT, false, 24, 16); // Time (float)
                GLES31.glEnableVertexAttribArray(2);
                GLES31.glVertexAttribPointer(3, 1, GLES31.GL_FLOAT, false, 24, 20); // Duration (float)
                GLES31.glEnableVertexAttribArray(3);
                GLES31.glUniform1f(timeHandle, f);
                GLES31.glUniform1f(deltaTimeHandle, f2);
                if (this.textureHandle != 0 && this.textureId != -1) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
                    GLES20.glUniform1i(textureHandle, 0);
                }
                GLES31.glBeginTransformFeedback(GLES31.GL_POINTS);
                GLES31.glDrawArrays(GLES31.GL_POINTS, 0, (int) effectSettings.getCount());
                GLES31.glEndTransformFeedback();

                if (reset) {
                    reset = false;
                    GLES31.glUniform1f(resetHandle, 0f);
                }
                this.currentBuffer = 1 - this.currentBuffer;
            }

            public void init() {

                int vertexShader = GLES31.glCreateShader(GLES31.GL_VERTEX_SHADER);
                int fragmentShader = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
                GLES31.glShaderSource(vertexShader, RLottieDrawable.readRes(null, R.raw.dissolve_v) + "\n// " + Math.random());
                GLES31.glCompileShader(vertexShader);
                int[] status = new int[1];
                GLES31.glGetShaderiv(vertexShader, GLES31.GL_COMPILE_STATUS, status, 0);
                if (status[0] == 0) {
                    FileLog.e("DissolveEffectView, compile vertex shader error: " + GLES31.glGetShaderInfoLog(vertexShader));
                    GLES31.glDeleteShader(vertexShader);
                    return;
                }
                GLES31.glShaderSource(fragmentShader, RLottieDrawable.readRes(null, R.raw.dissolve_f) + "\n// " + Math.random());
                GLES31.glCompileShader(fragmentShader);
                GLES31.glGetShaderiv(fragmentShader, GLES31.GL_COMPILE_STATUS, status, 0);
                if (status[0] == 0) {
                    FileLog.e("DissolveEffectView, compile fragment shader error: " + GLES31.glGetShaderInfoLog(fragmentShader));
                    GLES31.glDeleteShader(fragmentShader);
                    return;
                }
                program = GLES20.glCreateProgram();
                if (program == 0) {
                    FileLog.e("DissolveEffectView, could not create program");
                    return;
                }
                GLES31.glAttachShader(program, vertexShader);
                GLES31.glAttachShader(program, fragmentShader);
                String[] feedbackVaryings = {"outOffset", "outVelocity", "outLifetime", "outDuration"};
                GLES31.glTransformFeedbackVaryings(program, feedbackVaryings, GLES31.GL_INTERLEAVED_ATTRIBS);
                GLES31.glLinkProgram(program);
                GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0);
                if (status[0] == 0) {
                    FileLog.e("DissolveEffectView, link draw program error: " + GLES31.glGetProgramInfoLog(program));
                    return;
                }

                resetHandle = GLES31.glGetUniformLocation(program, "reset");
                timeHandle = GLES31.glGetUniformLocation(program, "time");
                deltaTimeHandle = GLES31.glGetUniformLocation(program, "deltaTime");
                sizeHandle = GLES31.glGetUniformLocation(program, "size");
                radiusHandle = GLES31.glGetUniformLocation(program, "r");
                seedHandle = GLES31.glGetUniformLocation(program, "seed");
                pointsCountHandle = GLES31.glGetUniformLocation(program, "pointsCount");
                pointsOffsetHandle = GLES31.glGetUniformLocation(program, "pointsOffset");
                pointsSizeHandle = GLES31.glGetUniformLocation(program, "pointsSize");
                noiseScaleHandle = GLES31.glGetUniformLocation(program, "noiseScale");
                noiseSpeedHandle = GLES31.glGetUniformLocation(program, "noiseSpeed");
                noiseMovementHandle = GLES31.glGetUniformLocation(program, "noiseMovement");
                longevityHandle = GLES31.glGetUniformLocation(program, "longevity");
                dampingMultHandle = GLES31.glGetUniformLocation(program, "dampingMult");
                maxVelocityHandle = GLES31.glGetUniformLocation(program, "maxVelocity");
                velocityMultHandle = GLES31.glGetUniformLocation(program, "velocityMult");
                forceMultHandle = GLES31.glGetUniformLocation(program, "forceMult");
                textureHandle = GLES20.glGetUniformLocation(program, "u_Texture");
                this.textureId = GLES31.glGetUniformLocation((int)this.program, (String)"u_Texture");
                GLES31.glViewport(0, 0, width, height);
                GLES31.glEnable(GLES31.GL_BLEND);
                GLES31.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES31.glUseProgram((int)this.program);
                GLES31.glUniform2f(sizeHandle, width, height);
                this.reset = true;
                GLES31.glUniform1f(resetHandle, reset ? 1 : 0);
                GLES31.glUniform1f(radiusHandle, AndroidUtilities.dpf2(1.5f));
                GLES31.glUniform1f(seedHandle, Utilities.fastRandom.nextInt(256) / 256f);
                GLES31.glUniform1f(noiseScaleHandle, 6);
                GLES31.glUniform1f(noiseSpeedHandle, 0.6f);
                GLES31.glUniform1f(noiseMovementHandle, 4f);
                GLES31.glUniform1f(longevityHandle, 1.4f);
                GLES31.glUniform1f(dampingMultHandle, .9999f);
                GLES31.glUniform1f(maxVelocityHandle, 6.f);
                GLES31.glUniform1f(velocityMultHandle, perfParam);
                GLES31.glUniform1f(forceMultHandle, 0.6f);
            }

            public boolean isReset() {
                return this.reset;
            }

            public void setup(DissolveEffectTextureView.EffectSettings effectSettings) {
                if (this.effectSettings == null && effectSettings != null) {
                    this.effectSettings = effectSettings;
                    int[] textures = new int[1];
                    GLES20.glGenTextures(1, textures, 0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, effectSettings.bitmap, 0);
                    this.textureId = textures[0];
                    this.genParticlesData();
                    this.reset = true;
                }
            }

            public void setSize(int n, int n2) {
                if (this.width != n && this.height != n2) {
                    this.width = n;
                    this.height = n2;
                    this.needUpdateSize = true;
                }
            }

            float perfParam = ANIMATION_PERFORMANCE_FACTOR;

            public void setPerformanceParam(float pp) {
                perfParam = pp;
            }
        }

        private static class RenderThread extends Thread {
            private DissolveEffectTextureView.GlContext eglContext;
            private int height;
            private Runnable invalidate;
            private DissolveEffectTextureView.EffectRenderer effectRenderer;
            private volatile boolean paused = false;
            private DissolveEffectTextureView.EffectSettings effectSettings;
            private volatile boolean running = true;
            private float t;
            //private TimeConfig timeConfig = TimeConfig.FPS60Config();
            private int width;

            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture) {
                this.eglContext = new DissolveEffectTextureView.GlContext(surfaceTexture);
            }

            public void setInvalidate(Runnable runnable) {
                this.invalidate = runnable;
            }

            public void setSize(int i, int i2) {
                this.width = i;
                this.height = i2;
                if (effectRenderer != null) {
                    effectRenderer.setSize(i, i2);
                }
            }

            public void setPaused(boolean z) {
                this.paused = z;
            }

            public void onSurfaceTextureDestroyed() {
                this.running = false;
            }

            public void setup(DissolveEffectTextureView.EffectSettings effectSettings) {
                this.effectSettings = effectSettings;
            }

            public void run() {
                if (eglContext != null) {
                    eglContext.init();
                }
                this.effectRenderer = new DissolveEffectTextureView.EffectRenderer();
                this.effectRenderer.setSize(this.width, this.height);
                if (effectSettings != null) {
                    this.effectRenderer.setPerformanceParam(effectSettings.performaceParam);
                }
                this.effectRenderer.init();
                this.t = 0.0f;
                long nanoTime = System.nanoTime();
                while (this.running) {
                    long nanoTime2 = System.nanoTime();
                    double d = ((double) (nanoTime2 - nanoTime)) / 1.0E9d;
                    if (d < effectSettings.min_delta) {
                        double d2 = effectSettings.min_delta - d;
                        long j = (long) (d2 * 1000.0d);
                        try {
                            sleep(j, (int) ((d2 - (((double) j) / 1000.0d)) * 1.0E9d));
                        } catch (Exception unused) {
                        }
                        d = effectSettings.min_delta;
                    } else if (d > effectSettings.max_delta) {
                        d = effectSettings.max_delta;
                    }
                    while (this.paused) {
                        try {
                            sleep(1000);
                        } catch (Exception unused2) {
                        }
                    }
                    float f = (float) (((double) this.t) + d);
                    this.t = f;
                    DissolveEffectTextureView.EffectSettings effectSettings = this.effectSettings;
                    if (effectSettings == null) {
                        this.t = 0.0f;
                        d = 0.0d;
                    } else if (f > effectSettings.getDurationUs()) {
                        // stopping
                    }
                    this.running = this.eglContext.makeCurrent();
                    if (this.running && effectRenderer != null && this.effectSettings != null) {
                        if (effectRenderer.isReset()) {
                            this.t = 0.0f;
                        }
                        this.effectRenderer.setup(this.effectSettings);
                        this.effectRenderer.drawParticles(this.t / this.effectSettings.getDurationUs(), ((float) d) / this.effectSettings.getDurationUs());
                    }
                    this.eglContext.swapBuffer();
                    AndroidUtilities.cancelRunOnUIThread(this.invalidate);
                    AndroidUtilities.runOnUIThread(this.invalidate);
                    nanoTime = nanoTime2;
                }
                this.eglContext.cleanup();
            }
        }

        private static class EffectSettings {
            private final Bitmap bitmap;
            private final int count;
            private final int countHorizontal;
            private final int countVertical;
            private final float durationUs;
            private final int height;
            private final int offsetX;
            private final int offsetY;
            private final float radius;
            private final int width;

            private final int maxFps;
            private final double max_delta;
            private final double min_delta;

            private float performaceParam;

            public EffectSettings(Bitmap bitmap, int n, int n2, int n3, int n4, float f, int n5, float pp) {
                this.bitmap = bitmap;
                this.offsetX = n3;
                this.offsetY = n4;
                this.width = n;
                this.height = n2;
                this.durationUs = (float)n5 / 1000.0f;
                this.radius = f;
                this.countHorizontal  = (int) (Math.floor(((float)n / f)) / pp);
                this.countVertical  = (int) (Math.floor(((float)n2 / f)) / pp);
                this.count = countHorizontal * countVertical;
                this.maxFps = (int) AndroidUtilities.screenRefreshRate;
                this.min_delta = 1.0 / maxFps;
                this.max_delta = min_delta * 4.0;
                this.performaceParam = pp;
            }

            public Bitmap getBitmap() {
                return this.bitmap;
            }

            public int getCount() {
                return this.count;
            }

            public int getCountHorizontal() {
                return this.countHorizontal;
            }

            public int getCountVertical() {
                return this.countVertical;
            }

            public float getDurationUs() {
                return this.durationUs;
            }

            public int getHeight() {
                return this.height;
            }

            public int getOffsetX() {
                return this.offsetX;
            }

            public int getOffsetY() {
                return this.offsetY;
            }

            public float getRadius() {
                return this.radius;
            }

            public int getWidth() {
                return this.width;
            }
        }

        private static class GlContext {
            private EGL10 egl;
            private EGLConfig eglConfig;
            private EGLContext eglContext;
            private EGLDisplay eglDisplay;
            private EGLSurface eglSurface;
            private boolean isInitialized = false;
            private final SurfaceTexture surfaceTexture;

            public GlContext(SurfaceTexture surfaceTexture) {
                this.surfaceTexture = surfaceTexture;
            }

            public void cleanup() {
                SurfaceTexture surfaceTexture;
                EGL10 eGL10 = this.egl;
                if (eGL10 != null) {
                    EGLContext eGLContext;
                    try {
                        eGL10.eglMakeCurrent(this.eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                    } catch (Exception exception) {
                    }
                    EGLSurface eGLSurface = this.eglSurface;
                    if (eGLSurface != null) {
                        try {
                            this.egl.eglDestroySurface(this.eglDisplay, eGLSurface);
                        } catch (Exception exception) {
                        }
                    }
                    if ((eGLContext = this.eglContext) != null) {
                        try {
                            this.egl.eglDestroyContext(this.eglDisplay, eGLContext);
                        } catch (Exception exception) {
                        }
                    }
                }
                if ((surfaceTexture = this.surfaceTexture) != null) {
                    try {
                        surfaceTexture.release();
                    } catch (Exception exception) {
                    }
                }
                this.isInitialized = false;
            }

            public void init() {
                this.egl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();
                this.eglDisplay = egl.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if (this.eglDisplay == EGL10.EGL_NO_DISPLAY) {
                    return;
                }
                int[] arrn = new int[2];
                if (!egl.eglInitialize(eglDisplay, arrn)) {
                    return;
                }
                int[] configAttributes = {
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                        EGL14.EGL_NONE
                };
                EGLConfig[] eglConfigs = new EGLConfig[1];
                int[] numConfigs = new int[1];
                if (!egl.eglChooseConfig(eglDisplay, configAttributes, eglConfigs, 1, numConfigs)) {
                    return;
                }
                this.eglConfig = eglConfigs[0];
                int[] contextAttributes = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                        EGL14.EGL_NONE
                };
                this.eglContext = this.egl.eglCreateContext(this.eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, contextAttributes);
                if (this.eglContext == null) {
                    return;
                }
                this.eglSurface = this.egl.eglCreateWindowSurface(this.eglDisplay, this.eglConfig, this.surfaceTexture, null);
                if (this.eglSurface == null) {
                    return;
                }
                this.isInitialized = this.makeCurrent();
            }

            public boolean isInitialized() {
                return this.isInitialized;
            }

            public boolean makeCurrent() {
                EGL10 eGL10 = this.egl;
                EGLDisplay eGLDisplay = this.eglDisplay;
                EGLSurface eGLSurface = this.eglSurface;
                boolean bl = eGL10.eglMakeCurrent(eGLDisplay, eGLSurface, eGLSurface, this.eglContext);
                return bl;
            }

            public void swapBuffer() {
                this.egl.eglSwapBuffers(this.eglDisplay, this.eglSurface);
            }
        }
    }
}