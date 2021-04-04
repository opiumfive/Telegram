package org.telegram.ui.Components.MessageAnimations.FourGradientBackground;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.opengl.Matrix;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationInterpolation;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParamType;
import org.telegram.ui.Components.MessageAnimations.Editor.data.BackgroundGradientParams;
import org.telegram.ui.Components.MessageAnimations.FourGradientBackground.OrientationSmooth.TiltProvider;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_TEST;
import static android.opengl.GLES20.glBlendFunc;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glViewport;

public class FourGradientBackgroundView extends GLTextureView {

    private final static double MAX_ORIENTATION_ANGLE = 20f;

    public Renderer renderer;
    private TiltProvider tiltProvider;
    private boolean mini = false;
    private WeakReference<ChatActivity> chatActivityWeakReference = null;
    private int activationAngle = 15;

    public FourGradientBackgroundView(Context context, boolean mini, ChatActivity chatActivity) {
        super(context);
        this.mini = mini;
        if (chatActivity != null) {
            chatActivityWeakReference = new WeakReference<>(chatActivity);
        }
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer = new Renderer(Resources.getSystem().getDisplayMetrics().widthPixels, mini ? AndroidUtilities.dp(200) : Resources.getSystem().getDisplayMetrics().heightPixels - AndroidUtilities.dp(50), context);
        tiltProvider = new TiltProvider();

        setRenderer(renderer);
    }

    public void setData(BackgroundGradientParams backgroundGradientParams) {
        activationAngle = backgroundGradientParams.tiltActivationAngle;
        renderer.setData(backgroundGradientParams);
        draw();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        tiltProvider.stop();
    }

    public void draw() {
        this.requestRender();
    }

    public void startAnimation(AnimationParamType animationParamType) {
        renderer.toggle(animationParamType);
    }

    private boolean breakout = false;
    private final TiltProvider.TiltListener tiltListener = new TiltProvider.TiltListener() {
        @Override
        public void sync(double angle1, double angle2) {
            if (renderer != null) {
                if (renderer.gradient == null || renderer.gradient.animatingState) return;
                if (chatActivityWeakReference != null) {
                    ChatActivity chatActivity = chatActivityWeakReference.get();
                    if (chatActivity != null) {
                        ChatAttachAlert chatAttachAlert = chatActivity.chatAttachAlert;
                        boolean cameraPreviewShowing = chatAttachAlert != null && chatAttachAlert.isShowing();
                        if (cameraPreviewShowing || (chatActivity.getInstantCameraView() != null && chatActivity.getInstantCameraView().recording)) return;
                    }
                }
                if (!breakout && (Math.abs(angle1) >= activationAngle || Math.abs(angle1) >= activationAngle)) breakout = true;
                if (!breakout) return;

                if (angle1 > MAX_ORIENTATION_ANGLE) angle1 = MAX_ORIENTATION_ANGLE;
                if (angle1 < -MAX_ORIENTATION_ANGLE) angle1 = -MAX_ORIENTATION_ANGLE;

                if (angle2 > MAX_ORIENTATION_ANGLE) angle2 = MAX_ORIENTATION_ANGLE;
                if (angle2 < -MAX_ORIENTATION_ANGLE) angle2 = -MAX_ORIENTATION_ANGLE;

                double an1 = angle1 / MAX_ORIENTATION_ANGLE / 2.0 + 0.5;
                double an2 = angle2 / MAX_ORIENTATION_ANGLE / 2.0 + 0.5;

                double dist = Math.sqrt((an1 - 0.5)*(an1 - 0.5) + (an2 - 0.5)*(an2 - 0.5));
                if (dist == 0.0) dist = 0.05;
                if (dist < 0.45) {
                    double f = 0.45 / dist;
                    an1 = 0.5 + (an1 - 0.5) * f;
                    an2 = 0.5 + (an2 - 0.5) * f;
                }

                if (an1 > 0.918) an1 = 0.918;
                if (an1 < 0.082) an1 = 0.082;
                if (an2 > 0.918) an2 = 0.918;
                if (an2 < 0.082) an2 = 0.082;

                renderer.setAngles(an1, an2);
            }
        }
    };


    public void start() {
        tiltProvider.start(tiltListener);
        resetOrientation();
    }

    public void resetOrientation() {
        tiltProvider.resetAngle();
        breakout = false;
    }

    public void stop() {
        tiltProvider.stop();
    }

    class Renderer implements GLTextureView.Renderer {

        int width, height;
        Context context;
        Shader shader;
        float[] projection = new float[16];
        float[] view = new float[16];
        float[] model = new float[16];

        boolean stateAngles = false;

        Gradient4Points gradient;
        private ArrayList<Animator> animators = new ArrayList<>();
        public BackgroundGradientParams backgroundGradientParams;

        Renderer(int width, int height, Context context) {
            this.width = width;
            this.height = height;
            this.context = context;
            gradient = new Gradient4Points(shader, width, height);
        }

        public void setData(BackgroundGradientParams params) {
            backgroundGradientParams = params;
            if (gradient != null) {
                gradient.setData(backgroundGradientParams);
            }
        }

        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            setup();
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            gradient.setup(shader);
            gradient.setData(backgroundGradientParams);
        }

        public void onDrawFrame(GL10 unused) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            gradient.display();
        }

        public void onSurfaceChanged(GL10 unused, int width, int height) {
            glViewport(0, 0, width, height);
            Matrix.orthoM(projection, 0, 0, width, height, 0, -10, 10);
            shader.setUniformM("projection", projection);
        }

        @Override
        public void onSurfaceDestroyed(GL10 gl) {
        }

        public void setup() {
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA);

            shader = new Shader(R.raw.four_gradient_vertex, R.raw.four_gradient_fragment, context);

            Matrix.setIdentityM(projection, 0);
            Matrix.setIdentityM(view, 0);
            Matrix.setIdentityM(model, 0);
            shader.setUniformM("projection", projection);
            shader.setUniformM("view", view);
            shader.setUniformM("model", model);
        }

        public void setAngles(double a1, double a2) {
            //TODO animate
            if (gradient != null && gradient.shader != null) {
                stateAngles = true;
                gradient.setAngles(a1, a2);
                draw();
            }
        }

        private float currentAnimValue = 0f;

        private void propagateAnimValue(ValueAnimator animation, AnimationParamType type) {
            float value = (float) animation.getAnimatedValue();
            currentAnimValue = value;
            AnimationInterpolation info = backgroundGradientParams.getInterpolation(type);
            float v = info.getAnimationValue(0, 1, value);
            gradient.updateColorsPosition(v);
            requestRender();
        }

        private void onEndAnimation(Animator animation) {
            gradient.toggle2();
            animators.remove(animation);
            if (animators.size() > 0) {
                gradient.animatingState = true;
                animators.get(0).start();
            } else {
                resetOrientation();
                gradient.animatingState = false;
            }
        }

        public void toggle(AnimationParamType type) {
            long dur = (long) (1000f * backgroundGradientParams.getInterpolation(type).duration / 60);
            ValueAnimator animator = ObjectAnimator.ofFloat(0, 1);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEndAnimation(animation);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    super.onAnimationCancel(animation);
                    Log.d("bganim", "onAnimationCancel#" + animation.toString());
                }
            });
            animator.addUpdateListener(animation -> {
                propagateAnimValue(animation, type);
            });
            animator.setDuration(dur);

            if (animators.size() > 0 && animators.get(0) != null && animators.get(0).isRunning()) {
                if (currentAnimValue > 0.5) {
                    try {
                        for(Animator a: animators) {
                            a.removeAllListeners();
                            a.cancel();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    animators.clear();
                    animator.setDuration((long)(dur - dur * currentAnimValue));
                    animator.setFloatValues(currentAnimValue, 1f);

                    ValueAnimator animator2 = ObjectAnimator.ofFloat(0, 1);
                    animator2.setDuration((long) (dur * currentAnimValue));
                    animator2.addUpdateListener(animation -> {
                        propagateAnimValue(animation, type);
                    });
                    animator2.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            onEndAnimation(animation);
                        }
                    });
                    animators.add(animator);
                    animators.add(animator2);

                    gradient.animatingState = true;
                    animators.get(0).start();
                } else {
                    try {
                        for(Animator a: animators) {
                            a.removeAllListeners();
                            a.cancel();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    animators.clear();
                    animator.setFloatValues(currentAnimValue, 1f);
                    animators.add(animator);

                    if (animators.size() == 1) {
                        gradient.animatingState = true;
                        animators.get(0).start();
                    }
                }
            } else {
                animators.add(animator);

                if (animators.size() == 1) {
                    gradient.animatingState = true;
                    animators.get(0).start();
                }
            }
        }
    }
}

