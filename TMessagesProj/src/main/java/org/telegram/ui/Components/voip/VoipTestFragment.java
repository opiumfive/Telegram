package org.telegram.ui.Components.voip;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.VoIPFragment;

// TODO REMOVE
public class VoipTestFragment extends BaseFragment {

    public VoipTestFragment() {
        super();
    }

    VoIPSmoothMotionView backgroundView;

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        backgroundView.stopCycleAnimation();
    }
    int state = VoIPSmoothMotionView.INITIATING;

    private AcceptDeclineView acceptDeclineView;
    private VoIPButtonsLayout buttonsLayout;
    VoIPToggleButton[] bottomButtons = new VoIPToggleButton[4];

    @Override
    public View createView(Context context) {
        FrameLayout content = new FrameLayout(context);
        fragmentView = content;

        backgroundView = new VoIPSmoothMotionView(context);

        float h = AndroidUtilities.getRealScreenSize().x / AndroidUtilities.density;

        content.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        RLottieImageView effectView4 = new RLottieImageView(context);
        effectView4.setAnimation(R.raw.star_effect4, 120, 120);
        content.addView(effectView4, LayoutHelper.createFrame(120, 120, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 36, 0, 36, h / 2));

        RLottieImageView effectView5 = new RLottieImageView(context);
        effectView5.setAnimation(R.raw.star_effect4, 120, 120);
        content.addView(effectView5, LayoutHelper.createFrame(120, 120, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 36, 0, 36, h / 2));


        VoIPRateButtonView rateButtonView = new VoIPRateButtonView(context, AndroidUtilities.dp(8));
        rateButtonView.setOverlayTextColor(VoIPFragment.Gradients.averagePaletteColor(VoIPFragment.Gradients.BLUE_VIOLET, VoIPFragment.Gradients.LIGHT));
        rateButtonView.setOverlayText("Close");
        rateButtonView.setVisibility(View.GONE);
        AndroidUtilities.runOnUIThread(() -> {
            rateButtonView.setVisibility(View.VISIBLE);
            rateButtonView.show();
        }, 2500);

        rateButtonView.setOnClickListener(v -> {
            rateButtonView.setVisibility(View.VISIBLE);
            rateButtonView.show();


        });
        content.addView(rateButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 45, Gravity.CENTER, 16, 0, 16, 80));


        content.setOnClickListener(v -> {
            state = ++state % 3;
            backgroundView.setState(state, null);


        });

        buttonsLayout = new VoIPButtonsLayout(context);
        for (int i = 0; i < 4; i++) {
            bottomButtons[i] = new VoIPToggleButton(context);
            buttonsLayout.addView(bottomButtons[i]);
        }

        content.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        currentState = 0;
        updateButtons(false, false);

        acceptDeclineView = new AcceptDeclineView(context);
        acceptDeclineView.setListener(new AcceptDeclineView.Listener() {
            @Override
            public void onAccept() {
                currentState = 1;
                showAcceptDeclineView(false, true, true);
                updateButtons(true, true);

                PointF center = new PointF();
                center.x = (AndroidUtilities.dp(40) + acceptDeclineView.buttonWidth / 2f) * 2f;
                center.y = 0;
                state = 1;
                backgroundView.setState(state, center);
            }

            @Override
            public void onDecline() {

            }
        });
        //acceptDeclineView.setRetryMod(true);
        content.addView(acceptDeclineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 186, Gravity.BOTTOM));
        showAcceptDeclineView(true, true, false);


        return fragmentView;
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        backgroundView.runCycleAnimation();
    }

    int currentState = 0;


    private void updateButtons(boolean animated, boolean transitionFromAccept) {
        if (!transitionFromAccept) {
            if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                TransitionSet transitionSet = new TransitionSet();
                Visibility visibility = new Visibility() {
                    @Override
                    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, AndroidUtilities.dp(100), 0);
                        if (view instanceof VoIPToggleButton) {
                            view.setTranslationY(AndroidUtilities.dp(100));
                            animator.setStartDelay(((VoIPToggleButton) view).animationDelay);
                        }
                        return animator;
                    }

                    @Override
                    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                        return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), AndroidUtilities.dp(100));
                    }
                };
                transitionSet
                        .addTransition(visibility.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT))
                        .addTransition(new ChangeBounds().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT));
                transitionSet.excludeChildren(VoIPToggleButton.class, true);
                TransitionManager.beginDelayedTransition(buttonsLayout, transitionSet);
            }
        } else {

            if (animated) {
                int dur = 250;
                float scaleProgressTurn = 0.75f;
                buttonsLayout.animate().cancel();
                buttonsLayout.setTranslationY(-AndroidUtilities.dp(60));
                buttonsLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(dur).start();

                bottomButtons[0].animate().setUpdateListener(null).cancel();
                bottomButtons[0].setTranslationX(AndroidUtilities.dp(40) - buttonsLayout.childPadding);
                bottomButtons[0].setAlpha(0.2f);
                bottomButtons[0].animate().translationX(0).alpha(1f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(dur).setUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    float scale = 1f;
                    if (progress < scaleProgressTurn) {
                        scale = AndroidUtilities.lerp(1f, 0.9f, progress / scaleProgressTurn);
                    } else {
                        scale = AndroidUtilities.lerp(0.9f, 1f, (progress - scaleProgressTurn) / (1 - scaleProgressTurn));
                    }
                    bottomButtons[0].setScaleX(scale);
                    bottomButtons[0].setScaleY(scale);
                }).start();

                bottomButtons[1].animate().setUpdateListener(null).cancel();
                bottomButtons[1].setTranslationX(AndroidUtilities.dp(40) - buttonsLayout.childPadding * 3 - buttonsLayout.childWidth);
                bottomButtons[1].setAlpha(0.2f);
                bottomButtons[1].animate().translationX(0).alpha(1f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(dur).setUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    float scale = 1f;
                    if (progress < scaleProgressTurn) {
                        scale = AndroidUtilities.lerp(1f, 0.9f, progress / scaleProgressTurn);
                    } else {
                        scale = AndroidUtilities.lerp(0.9f, 1f, (progress - scaleProgressTurn) / (1 - scaleProgressTurn));
                    }
                    bottomButtons[1].setScaleX(scale);
                    bottomButtons[1].setScaleY(scale);
                }).start();

                bottomButtons[2].animate().setUpdateListener(null).cancel();
                bottomButtons[2].setTranslationX(AndroidUtilities.dp(40) - buttonsLayout.childPadding * 4 - buttonsLayout.childWidth * 2);
                bottomButtons[2].setAlpha(0.2f);
                bottomButtons[2].animate().translationX(0).alpha(1f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(dur).setUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    float scale = 1f;
                    if (progress < scaleProgressTurn) {
                        scale = AndroidUtilities.lerp(1f, 0.9f, progress / scaleProgressTurn);
                    } else {
                        scale = AndroidUtilities.lerp(0.9f, 1f, (progress - scaleProgressTurn) / (1 - scaleProgressTurn));
                    }
                    bottomButtons[2].setScaleX(scale);
                    bottomButtons[2].setScaleY(scale);
                }).start();

                bottomButtons[3].animate().setUpdateListener(null).cancel();
                int x1 = acceptDeclineView.getMeasuredWidth() - acceptDeclineView.buttonWidth - AndroidUtilities.dp(50);
                int x2 = buttonsLayout.childPadding * 7 + buttonsLayout.childWidth * 3;
                bottomButtons[3].setTranslationX(x1 - x2);
                bottomButtons[3].animate().translationX(0).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(dur).setUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    float scale = 1f;
                    if (progress < scaleProgressTurn) {
                        scale = AndroidUtilities.lerp(1f, 0.9f, progress / scaleProgressTurn);
                    } else {
                        scale = AndroidUtilities.lerp(0.9f, 1f, (progress - scaleProgressTurn) / (1 - scaleProgressTurn));
                    }
                    bottomButtons[3].setScaleX(scale);
                    bottomButtons[3].setScaleY(scale);
                }).start();
            }
        }



        if (currentState == 0) {

                bottomButtons[0].setVisibility(View.GONE);
                bottomButtons[1].setVisibility(View.GONE);
                bottomButtons[2].setVisibility(View.GONE);

            bottomButtons[3].setVisibility(View.GONE);
        } else {
            setSpeakerPhoneAction(bottomButtons[0], null, animated);

            setVideoAction(bottomButtons[1], null, animated);
            setMicrohoneAction(bottomButtons[2], null, animated);

            bottomButtons[3].setData(R.raw.voip_call_decline, true, Color.WHITE, 0xFFF01D2C, LocaleController.getString("VoipEndCall", R.string.VoipEndCall), false, animated);
            bottomButtons[3].setOnClickListener(view -> {

            });
        }

        int animationDelay = 0;
        for (int i = 0; i < 4; i++) {
            if (bottomButtons[i].getVisibility() == View.VISIBLE) {
                bottomButtons[i].animationDelay = animationDelay;
                animationDelay += 16;
            }
        }
    }

    private void showAcceptDeclineView(boolean show, boolean animated, boolean transitionToButtons) {
        if (!animated) {
            acceptDeclineView.setVisibility(show ? View.VISIBLE : View.GONE);
        } else {
            if (show && acceptDeclineView.getTag() == null) {
                acceptDeclineView.animate().setListener(null).cancel();
                if (acceptDeclineView.getVisibility() == View.GONE) {
                    acceptDeclineView.setVisibility(View.VISIBLE);
                    acceptDeclineView.setAlpha(0);
                }
                acceptDeclineView.animate().alpha(1f);
            }
            if (!show && acceptDeclineView.getTag() != null) {
                acceptDeclineView.animate().setListener(null).cancel();
                acceptDeclineView.hideDeclineImmediately();
                ViewPropertyAnimator v = acceptDeclineView.animate().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        acceptDeclineView.setVisibility(View.GONE);
                    }
                }).alpha(0f).setDuration(150);
                if (transitionToButtons) {
                    v.translationY(AndroidUtilities.dp(30));
                    v.translationX(-AndroidUtilities.dp(10));
                }
            }
        }

        acceptDeclineView.setEnabled(show);
        acceptDeclineView.setTag(show ? 1 : null);
    }

    private void setMicrohoneAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        //if (service.isMicMute()) {
        //    bottomButton.setData(R.drawable.calls_unmute, Color.BLACK, Color.WHITE, LocaleController.getString("VoipUnmute", R.string.VoipUnmute), true, animated);
        //} else {
            bottomButton.setData(R.raw.voip_call_mute, true, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipMute", R.string.VoipMute), false, animated);
        //}
        //currentUserCameraFloatingLayout.setMuted(service.isMicMute(), animated);
        bottomButton.setOnClickListener(view -> {

        });
    }

    private void setVideoAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        boolean isVideoAvailable;
        //if (currentUserIsVideo || callingUserIsVideo) {
            isVideoAvailable = true;
        //} else {
            //isVideoAvailable = service.isVideoAvailable();
        //}
        //if (currentUserIsVideo) {
        //    bottomButton.setData(service.isScreencast() ? R.drawable.calls_sharescreen : R.drawable.calls_video, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipStopVideo", R.string.VoipStopVideo), false, animated);
        //} else {
        bottomButton.setData(R.raw.voip_video_start, true, Color.BLACK, Color.WHITE, LocaleController.getString("VoipStartVideo", R.string.VoipStartVideo), true, animated);
        //}
        bottomButton.setCrossOffset(-AndroidUtilities.dpf2(3.5f));
        bottomButton.setOnClickListener(view -> {

        });
        bottomButton.setEnabled(true);
    }

    private void setSpeakerPhoneAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        bottomButton.setData(R.raw.voip_speaker_to_bt, true, Color.BLACK, Color.WHITE, LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), false, animated);
        bottomButton.setChecked(true, animated);

        bottomButton.setCheckableForAccessibility(true);
        bottomButton.setEnabled(true);
        bottomButton.setOnClickListener(view -> {

        });
    }


}
