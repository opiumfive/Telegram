package org.telegram.ui.Components.MessageAnimations.Editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationItemType;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParamType;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParams;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParamsHolder;
import org.telegram.ui.Components.MessageAnimations.Editor.data.BackgroundGradientParams;
import org.telegram.ui.Components.MessageAnimations.Editor.popup.AnimParamsSharingPopup;
import org.telegram.ui.Components.MessageAnimations.Editor.popup.ColorPickerPopup;
import org.telegram.ui.Components.MessageAnimations.Editor.popup.DurationSelectPopup;
import org.telegram.ui.Components.MessageAnimations.FourGradientBackground.FourGradientBackgroundView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.ShareAlert;

import java.util.ArrayList;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

public class MessageAnimationEditorActivity extends BaseFragment {

    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };
    private Context context;
    private ActionBarMenuItem moreButton;
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private final AnimationEditorPage[] animationEditorPages = new AnimationEditorPage[2];
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;
    private int maximumVelocity;
    private final Paint backgroundPaint = new Paint();
    private boolean swipeBackEnabled = true;

    private ListAdapter backgroundAdapter;
    private ListAdapter shortTextAdapter;
    private ListAdapter longTextAdapter;
    private ListAdapter linkAdapter;
    private ListAdapter emojiAdapter;
    private ListAdapter stickerAdapter;
    private ListAdapter voiceAdapter;
    private ListAdapter videoAdapter;
    private ListAdapter gifAdapter;
    private ListAdapter attachmentAdapter;
    private boolean shouldBlockTouches = false;

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onBackPressed() {
        return super.onBackPressed();
    }

    @Override
    public View createView(Context context) {
        this.context = context;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.setTitle("Animation Settings");

        if (moreButton == null) {
            ActionBarMenu barMenu = actionBar.createMenu();
            moreButton = barMenu.addItem(1, R.drawable.ic_ab_other);
        }

        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                switch (id) {
                    case -1:
                        finishFragment();
                        break;
                    case 1:
                        AnimParamsSharingPopup animParamsSharingPopup = new AnimParamsSharingPopup(moreButton);
                        animParamsSharingPopup.show(index -> {
                            if (index == 0) {
                                String exported = AnimationParamsHolder.instance.exportParams();
                                TextView textView = new TextView(context);
                                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                                textView.setText(exported);
                                textView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setTitle("Exporting params");
                                builder.setView(textView);
                                builder.setPositiveButton("Share", (dialogInterface, i) -> {
                                    ShareAlert shareAlert = new ShareAlert(context, null, null, exported, null, false, null, null, false, false);
                                    shareAlert.show();
                                });
                                builder.setNegativeButton("Copy", (dialogInterface, i) -> {
                                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                    ClipData clip = android.content.ClipData.newPlainText("Animation Params",exported);
                                    clipboard.setPrimaryClip(clip);
                                });
                                builder.setNeutralButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> {
                                });
                                AlertDialog dialog = builder.create();
                                showDialog(dialog);
                            } else if (index == 1) {
                                EditText editText = new EditText(context);
                                editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                                editText.setHint("Paste params here");
                                editText.addTextChangedListener(new TextWatcher() {
                                    @Override
                                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                                    }

                                    @Override
                                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                                        if (editText.getText().toString().isEmpty()) {
                                            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                                        } else {
                                            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                                        }
                                    }

                                    @Override
                                    public void afterTextChanged(Editable editable) {
                                    }
                                });
                                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                if (clipboard != null && clipboard.getPrimaryClipDescription() != null && clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN)) {
                                    ClipData clipData = clipboard.getPrimaryClip();
                                    if (clipData != null && clipData.getItemCount() > 0) {
                                        ClipData.Item item = clipData.getItemAt(0);
                                        String fromCpy = item.getText().toString();
                                        editText.setText(fromCpy);
                                    }
                                }
                                editText.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(12));
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setTitle("Importing params");
                                builder.setView(editText);
                                builder.setPositiveButton("Import", (dialogInterface, i) -> {
                                    boolean success = AnimationParamsHolder.instance.importParams(editText.getText().toString());
                                    if (success) {
                                        backgroundAdapter.getParams();
                                        emojiAdapter.getParams();
                                        stickerAdapter.getParams();
                                        linkAdapter.getParams();
                                        longTextAdapter.getParams();
                                        shortTextAdapter.getParams();
                                        voiceAdapter.getParams();
                                        videoAdapter.getParams();
                                        gifAdapter.getParams();
                                        attachmentAdapter.getParams();
                                        //TODO remake to notify only current pages
                                        backgroundAdapter.notifyDataSetChanged();
                                        emojiAdapter.notifyDataSetChanged();
                                        stickerAdapter.notifyDataSetChanged();
                                        linkAdapter.notifyDataSetChanged();
                                        longTextAdapter.notifyDataSetChanged();
                                        shortTextAdapter.notifyDataSetChanged();
                                        voiceAdapter.notifyDataSetChanged();
                                        videoAdapter.notifyDataSetChanged();
                                        gifAdapter.notifyDataSetChanged();
                                        attachmentAdapter.notifyDataSetChanged();
                                    }
                                });
                                builder.setNeutralButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> {
                                });
                                AlertDialog dialog = builder.create();
                                showDialog(dialog);
                            } else if (index == 2) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setTitle("Restore to default");
                                builder.setMessage("Do you want to restore animations parameters to default?");
                                builder.setPositiveButton("Restore", (dialogInterface, i) -> {
                                    AnimationParamsHolder.instance.restore();
                                    backgroundAdapter.getParams();
                                    emojiAdapter.getParams();
                                    stickerAdapter.getParams();
                                    linkAdapter.getParams();
                                    longTextAdapter.getParams();
                                    shortTextAdapter.getParams();
                                    voiceAdapter.getParams();
                                    videoAdapter.getParams();
                                    gifAdapter.getParams();
                                    attachmentAdapter.getParams();
                                    //TODO remake to notify only current pages
                                    backgroundAdapter.notifyDataSetChanged();
                                    emojiAdapter.notifyDataSetChanged();
                                    stickerAdapter.notifyDataSetChanged();
                                    linkAdapter.notifyDataSetChanged();
                                    longTextAdapter.notifyDataSetChanged();
                                    shortTextAdapter.notifyDataSetChanged();
                                    voiceAdapter.notifyDataSetChanged();
                                    videoAdapter.notifyDataSetChanged();
                                    gifAdapter.notifyDataSetChanged();
                                    attachmentAdapter.notifyDataSetChanged();
                                });
                                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                AlertDialog dialog = builder.create();
                                showDialog(dialog);
                                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                                if (button != null) {
                                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                                }
                            }
                        });
                        break;
                }
            }
        });

        backgroundAdapter = new ListAdapter(context, AnimationItemType.Background);
        shortTextAdapter = new ListAdapter(context, AnimationItemType.ShortText);
        longTextAdapter = new ListAdapter(context, AnimationItemType.LongText);
        linkAdapter = new ListAdapter(context, AnimationItemType.Link);
        emojiAdapter = new ListAdapter(context, AnimationItemType.Emoji);
        stickerAdapter = new ListAdapter(context, AnimationItemType.Sticker);
        voiceAdapter = new ListAdapter(context, AnimationItemType.VoiceMessage);
        videoAdapter = new ListAdapter(context, AnimationItemType.VideoMessage);
        gifAdapter = new ListAdapter(context, AnimationItemType.Gif);
        attachmentAdapter = new ListAdapter(context, AnimationItemType.Attachment);

        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        actionBar.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int id, boolean forward) {
                if (animationEditorPages[0].selectedType == id) {
                    return;
                }
                swipeBackEnabled = id == scrollSlidingTextTabStrip.getFirstTabId();
                animationEditorPages[1].selectedType = id;
                animationEditorPages[1].setVisibility(View.VISIBLE);
                switchToCurrentSelectedMode(true);
                animatingForward = forward;
            }

            @Override
            public void onPageScrolled(float progress) {
                if (progress == 1 && animationEditorPages[1].getVisibility() != View.VISIBLE) {
                    return;
                }
                if (animatingForward) {
                    animationEditorPages[0].setTranslationX(-progress * animationEditorPages[0].getMeasuredWidth());
                    animationEditorPages[1].setTranslationX(animationEditorPages[0].getMeasuredWidth() - progress * animationEditorPages[0].getMeasuredWidth());
                } else {
                    animationEditorPages[0].setTranslationX(progress * animationEditorPages[0].getMeasuredWidth());
                    animationEditorPages[1].setTranslationX(progress * animationEditorPages[0].getMeasuredWidth() - animationEditorPages[0].getMeasuredWidth());
                }
                if (progress == 1) {
                    AnimationEditorPage tempPage = animationEditorPages[0];
                    animationEditorPages[0] = animationEditorPages[1];
                    animationEditorPages[1] = tempPage;
                    animationEditorPages[1].setVisibility(View.GONE);
                }
            }
        });

        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();

        FrameLayout frameLayout;
        fragmentView = frameLayout = new FrameLayout(context) {

            private int startedTrackingPointerId;
            private boolean startedTracking;
            private boolean maybeStartTracking;
            private int startedTrackingX;
            private int startedTrackingY;
            private VelocityTracker velocityTracker;
            private boolean globalIgnoreLayout;

            private boolean prepareForMoving(MotionEvent ev, boolean forward) {
                int id = scrollSlidingTextTabStrip.getNextPageId(forward);
                if (id < 0) {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                maybeStartTracking = false;
                startedTracking = true;
                startedTrackingX = (int) ev.getX();
                actionBar.setEnabled(false);
                scrollSlidingTextTabStrip.setEnabled(false);
                animationEditorPages[1].selectedType = id;
                animationEditorPages[1].setVisibility(View.VISIBLE);
                animatingForward = forward;
                switchToCurrentSelectedMode(true);
                if (forward) {
                    animationEditorPages[1].setTranslationX(animationEditorPages[0].getMeasuredWidth());
                } else {
                    animationEditorPages[1].setTranslationX(-animationEditorPages[0].getMeasuredWidth());
                }
                return true;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                globalIgnoreLayout = true;
                for (int a = 0; a < animationEditorPages.length; a++) {
                    if (animationEditorPages[a] == null) {
                        continue;
                    }
                    if (animationEditorPages[a].listView != null) {
                        animationEditorPages[a].listView.setPadding(0, actionBarHeight, 0, AndroidUtilities.dp(4));
                    }
                }
                globalIgnoreLayout = false;

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getMeasuredHeight() + (int) actionBar.getTranslationY());
                }
            }

            @Override
            public void requestLayout() {
                if (globalIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            public boolean checkTabsAnimationInProgress() {
                if (tabsAnimationInProgress) {
                    boolean cancel = false;
                    if (backAnimation) {
                        if (Math.abs(animationEditorPages[0].getTranslationX()) < 1) {
                            animationEditorPages[0].setTranslationX(0);
                            animationEditorPages[1].setTranslationX(animationEditorPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                            cancel = true;
                        }
                    } else if (Math.abs(animationEditorPages[1].getTranslationX()) < 1) {
                        animationEditorPages[0].setTranslationX(animationEditorPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                        animationEditorPages[1].setTranslationX(0);
                        cancel = true;
                    }
                    if (cancel) {
                        if (tabsAnimation != null) {
                            tabsAnimation.cancel();
                            tabsAnimation = null;
                        }
                        tabsAnimationInProgress = false;
                    }
                    return tabsAnimationInProgress;
                }
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return checkTabsAnimationInProgress() || scrollSlidingTextTabStrip.isAnimatingIndicator() || onTouchEvent(ev);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                canvas.drawRect(0, actionBar.getMeasuredHeight() + actionBar.getTranslationY(), getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (shouldBlockTouches) return false;
                if (!parentLayout.checkTransitionAnimation() && !checkTabsAnimationInProgress()) {
                    if (ev != null) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        velocityTracker.addMovement(ev);
                    }
                    if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) ev.getX();
                        startedTrackingY = (int) ev.getY();
                        velocityTracker.clear();
                    } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                        int dx = (int) (ev.getX() - startedTrackingX);
                        int dy = Math.abs((int) ev.getY() - startedTrackingY);
                        if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                            if (!prepareForMoving(ev, dx < 0)) {
                                maybeStartTracking = true;
                                startedTracking = false;
                                animationEditorPages[0].setTranslationX(0);
                                animationEditorPages[1].setTranslationX(animatingForward ? animationEditorPages[0].getMeasuredWidth() : -animationEditorPages[0].getMeasuredWidth());
                                scrollSlidingTextTabStrip.selectTabWithId(animationEditorPages[1].selectedType, 0);
                            }
                        }
                        if (maybeStartTracking && !startedTracking) {
                            float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                            if (Math.abs(dx) >= touchSlop && Math.abs(dx) > dy) {
                                prepareForMoving(ev, dx < 0);
                            }
                        } else if (startedTracking) {
                            if (animatingForward) {
                                animationEditorPages[0].setTranslationX(dx);
                                animationEditorPages[1].setTranslationX(animationEditorPages[0].getMeasuredWidth() + dx);
                            } else {
                                animationEditorPages[0].setTranslationX(dx);
                                animationEditorPages[1].setTranslationX(dx - animationEditorPages[0].getMeasuredWidth());
                            }
                            float scrollProgress = Math.abs(dx) / (float) animationEditorPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip.selectTabWithId(animationEditorPages[1].selectedType, scrollProgress);
                        }
                    } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                        float velX;
                        float velY;
                        if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL) {
                            velX = velocityTracker.getXVelocity();
                            velY = velocityTracker.getYVelocity();
                            if (!startedTracking) {
                                if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                                    prepareForMoving(ev, velX < 0);
                                }
                            }
                        } else {
                            velX = 0;
                            velY = 0;
                        }
                        if (startedTracking) {
                            float x = animationEditorPages[0].getX();
                            tabsAnimation = new AnimatorSet();
                            backAnimation = Math.abs(x) < animationEditorPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                            float distToMove;
                            float dx;
                            if (backAnimation) {
                                dx = Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(animationEditorPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(animationEditorPages[1], View.TRANSLATION_X, animationEditorPages[1].getMeasuredWidth())
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(animationEditorPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(animationEditorPages[1], View.TRANSLATION_X, -animationEditorPages[1].getMeasuredWidth())
                                    );
                                }
                            } else {
                                dx = animationEditorPages[0].getMeasuredWidth() - Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(animationEditorPages[0], View.TRANSLATION_X, -animationEditorPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(animationEditorPages[1], View.TRANSLATION_X, 0)
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(animationEditorPages[0], View.TRANSLATION_X, animationEditorPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(animationEditorPages[1], View.TRANSLATION_X, 0)
                                    );
                                }
                            }
                            tabsAnimation.setInterpolator(interpolator);

                            int width = getMeasuredWidth();
                            int halfWidth = width / 2;
                            float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                            float distance = (float) halfWidth + (float) halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio);
                            velX = Math.abs(velX);
                            int duration;
                            if (velX > 0) {
                                duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                            } else {
                                float pageDelta = dx / getMeasuredWidth();
                                duration = (int) ((pageDelta + 1.0f) * 100.0f);
                            }
                            duration = Math.max(150, Math.min(duration, 600));

                            tabsAnimation.setDuration(duration);
                            tabsAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    tabsAnimation = null;
                                    if (backAnimation) {
                                        animationEditorPages[1].setVisibility(View.GONE);
                                    } else {
                                        AnimationEditorPage tempPage = animationEditorPages[0];
                                        animationEditorPages[0] = animationEditorPages[1];
                                        animationEditorPages[1] = tempPage;
                                        animationEditorPages[1].setVisibility(View.GONE);
                                        swipeBackEnabled = animationEditorPages[0].selectedType == scrollSlidingTextTabStrip.getFirstTabId();
                                        scrollSlidingTextTabStrip.selectTabWithId(animationEditorPages[0].selectedType, 1.0f);
                                    }
                                    tabsAnimationInProgress = false;
                                    maybeStartTracking = false;
                                    startedTracking = false;
                                    actionBar.setEnabled(true);
                                    scrollSlidingTextTabStrip.setEnabled(true);
                                }
                            });
                            tabsAnimation.start();
                            tabsAnimationInProgress = true;
                            startedTracking = false;
                        } else {
                            maybeStartTracking = false;
                            actionBar.setEnabled(true);
                            scrollSlidingTextTabStrip.setEnabled(true);
                        }
                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                    }
                    return startedTracking;
                }
                return false;
            }
        };
        frameLayout.setWillNotDraw(false);

        int scrollToPositionOnRecreate = -1;
        int scrollToOffsetOnRecreate = 0;

        for (int a = 0; a < animationEditorPages.length; a++) {
            if (a == 0) {
                if (animationEditorPages[a] != null && animationEditorPages[a].layoutManager != null) {
                    scrollToPositionOnRecreate = animationEditorPages[a].layoutManager.findFirstVisibleItemPosition();
                    if (scrollToPositionOnRecreate != animationEditorPages[a].layoutManager.getItemCount() - 1) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) animationEditorPages[a].listView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                        if (holder != null) {
                            scrollToOffsetOnRecreate = holder.itemView.getTop();
                        } else {
                            scrollToPositionOnRecreate = -1;
                        }
                    } else {
                        scrollToPositionOnRecreate = -1;
                    }
                }
            }
            final AnimationEditorPage ViewPage = new AnimationEditorPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress) {
                        if (animationEditorPages[0] == this) {
                            float scrollProgress = Math.abs(animationEditorPages[0].getTranslationX()) / (float) animationEditorPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip.selectTabWithId(animationEditorPages[1].selectedType, scrollProgress);
                        }
                    }
                }
            };
            frameLayout.addView(ViewPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            animationEditorPages[a] = ViewPage;

            final LinearLayoutManager layoutManager = animationEditorPages[a].layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }

                @Override
                public boolean canScrollHorizontally() {
                    return false;
                }

                @Override
                public boolean canScrollVertically() {
                    return !shouldBlockTouches;
                }
            };
            RecyclerListView listView = new RecyclerListView(context);
            animationEditorPages[a].listView = listView;
            animationEditorPages[a].listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            animationEditorPages[a].listView.setItemAnimator(null);
            animationEditorPages[a].listView.setClipToPadding(false);
            animationEditorPages[a].listView.setSectionsType(2);
            animationEditorPages[a].listView.setLayoutManager(layoutManager);
            animationEditorPages[a].addView(animationEditorPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            animationEditorPages[a].listView.setOnItemClickListener((view, position) -> {
                if (getParentActivity() == null) {
                    return;
                }
                ListAdapter adapter = (ListAdapter) listView.getAdapter();
                if (adapter == null) return;
                if (position == adapter.backgroundOpenFullScreenRow) {
                    presentFragment(new BackgroundAnimationPreviewActivity((BackgroundGradientParams) AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.Background).clone()));
                } else if (position == adapter.durationRow) {
                    DurationSelectPopup durationSelectPopup = new DurationSelectPopup(view, adapter.currentType);
                    durationSelectPopup.show(ms -> {
                        adapter.typeSetting.setDuration(ms / 1000.0f * 60);
                        adapter.saveParams();
                        adapter.notifyDataSetChanged();
                    });
                } else if (position == adapter.durationSendMessageRow) {
                    DurationSelectPopup durationSelectPopup = new DurationSelectPopup(scrollSlidingTextTabStrip, adapter.currentType);
                    durationSelectPopup.show(ms -> {
                        adapter.typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnSendMessage).duration = ms / 1000.0f * 60;
                        adapter.saveParams();
                        adapter.notifyItemRangeChanged(position, 2);
                    });
                } else if (position == adapter.durationOpenChatRow) {
                    DurationSelectPopup durationSelectPopup = new DurationSelectPopup(scrollSlidingTextTabStrip, adapter.currentType);
                    durationSelectPopup.show(ms -> {
                        adapter.typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnOpenChat).duration = ms / 1000.0f * 60;
                        adapter.saveParams();
                        adapter.notifyItemRangeChanged(position, 2);
                    });
                } else if (position == adapter.durationJumpToMessageRow) {
                    DurationSelectPopup durationSelectPopup = new DurationSelectPopup(scrollSlidingTextTabStrip, adapter.currentType);
                    durationSelectPopup.show(ms -> {
                        adapter.typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnJumpToMessage).duration = ms / 1000.0f * 60;
                        adapter.saveParams();
                        adapter.notifyItemRangeChanged(position, 2);
                    });
                } else if (position == adapter.color1Row) {
                    ColorPickerPopup colorPickerPopup = new ColorPickerPopup(context, ((BackgroundGradientParams) adapter.typeSetting).color1);
                    colorPickerPopup.show(fragmentView, color -> {
                        ((BackgroundGradientParams) adapter.typeSetting).color1 = color;
                        adapter.saveParams();
                        if (adapter.backgroundView != null) {
                            adapter.backgroundView.setData((BackgroundGradientParams) adapter.typeSetting);
                        }
                        adapter.notifyItemChanged(position);
                        //adapter.notifyItemChanged(adapter.backgroundRow);
                    });
                } else if (position == adapter.color2Row) {
                    ColorPickerPopup colorPickerPopup = new ColorPickerPopup(context, ((BackgroundGradientParams) adapter.typeSetting).color2);
                    colorPickerPopup.show(fragmentView, color -> {
                        ((BackgroundGradientParams) adapter.typeSetting).color2 = color;
                        adapter.saveParams();
                        if (adapter.backgroundView != null) {
                            adapter.backgroundView.setData((BackgroundGradientParams) adapter.typeSetting);
                        }
                        adapter.notifyItemChanged(position);
                        //adapter.notifyItemChanged(adapter.backgroundRow);
                    });
                } else if (position == adapter.color3Row) {
                    ColorPickerPopup colorPickerPopup = new ColorPickerPopup(context, ((BackgroundGradientParams) adapter.typeSetting).color3);
                    colorPickerPopup.show(fragmentView, color -> {
                        ((BackgroundGradientParams) adapter.typeSetting).color3 = color;
                        adapter.saveParams();
                        if (adapter.backgroundView != null) {
                            adapter.backgroundView.setData((BackgroundGradientParams) adapter.typeSetting);
                        }
                        adapter.notifyItemChanged(position);
                        //adapter.notifyItemChanged(adapter.backgroundRow);
                    });
                } else if (position == adapter.color4Row) {
                    ColorPickerPopup colorPickerPopup = new ColorPickerPopup(context, ((BackgroundGradientParams) adapter.typeSetting).color4);
                    colorPickerPopup.show(fragmentView, color -> {
                        ((BackgroundGradientParams) adapter.typeSetting).color4 = color;
                        adapter.saveParams();
                        if (adapter.backgroundView != null) {
                            adapter.backgroundView.setData((BackgroundGradientParams) adapter.typeSetting);
                        }
                        adapter.notifyItemChanged(position);
                        //adapter.notifyItemChanged(adapter.backgroundRow);
                    });
                }
            });

            animationEditorPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState != RecyclerView.SCROLL_STATE_DRAGGING) {
                        int scrollY = (int) -actionBar.getTranslationY();
                        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
                        if (scrollY != 0 && scrollY != actionBarHeight) {
                            if (scrollY < actionBarHeight / 2) {
                                animationEditorPages[0].listView.smoothScrollBy(0, -scrollY);
                            } else {
                                animationEditorPages[0].listView.smoothScrollBy(0, actionBarHeight - scrollY);
                            }
                        }
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (recyclerView == animationEditorPages[0].listView) {
                        float currentTranslation = actionBar.getTranslationY();
                        float newTranslation = currentTranslation - dy;
                        if (newTranslation < -ActionBar.getCurrentActionBarHeight()) {
                            newTranslation = -ActionBar.getCurrentActionBarHeight();
                        } else if (newTranslation > 0) {
                            newTranslation = 0;
                        }
                        if (newTranslation != currentTranslation) {
                            setScrollY(newTranslation);
                        }
                    }
                }
            });
            if (a == 0 && scrollToPositionOnRecreate != -1) {
                layoutManager.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate);
            }
            if (a != 0) {
                animationEditorPages[a].setVisibility(View.GONE);
            }
        }

        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateTabs();
        switchToCurrentSelectedMode(false);
        swipeBackEnabled = scrollSlidingTextTabStrip.getCurrentTabId() == scrollSlidingTextTabStrip.getFirstTabId();

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (backgroundAdapter != null) {
            backgroundAdapter.notifyDataSetChanged();
        }
        if (shortTextAdapter != null) {
            shortTextAdapter.notifyDataSetChanged();
        }
        if (longTextAdapter != null) {
            longTextAdapter.notifyDataSetChanged();
        }
        if (linkAdapter != null) {
            linkAdapter.notifyDataSetChanged();
        }
        if (emojiAdapter != null) {
            emojiAdapter.notifyDataSetChanged();
        }
        if (stickerAdapter != null) {
            stickerAdapter.notifyDataSetChanged();
        }
        if (voiceAdapter != null) {
            voiceAdapter.notifyDataSetChanged();
        }
        if (videoAdapter != null) {
            videoAdapter.notifyDataSetChanged();
        }
        if (gifAdapter != null) {
            gifAdapter.notifyDataSetChanged();
        }
        if (attachmentAdapter != null) {
            attachmentAdapter.notifyDataSetChanged();
        }
    }

    private void setScrollY(float value) {
        actionBar.setTranslationY(value);
        for (int a = 0; a < animationEditorPages.length; a++) {
            animationEditorPages[a].listView.setPinnedSectionOffsetY((int) value);
        }
        fragmentView.invalidate();
    }

    private void updateTabs() {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        scrollSlidingTextTabStrip.addTextTab(0, "Background");
        scrollSlidingTextTabStrip.addTextTab(1, "Short Text");
        scrollSlidingTextTabStrip.addTextTab(2, "Long Text");
        scrollSlidingTextTabStrip.addTextTab(3, "Link");
        scrollSlidingTextTabStrip.addTextTab(4, "Emoji");
        scrollSlidingTextTabStrip.addTextTab(5, "Sticker");
        scrollSlidingTextTabStrip.addTextTab(6, "Voice Message");
        scrollSlidingTextTabStrip.addTextTab(7, "Video Message");
        scrollSlidingTextTabStrip.addTextTab(8, "Gif");
        scrollSlidingTextTabStrip.addTextTab(9, "Attachment");

        scrollSlidingTextTabStrip.setVisibility(View.VISIBLE);
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            animationEditorPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (int a = 0; a < animationEditorPages.length; a++) {
            animationEditorPages[a].listView.stopScroll();
        }
        int a = animated ? 1 : 0;
        RecyclerView.Adapter currentAdapter = animationEditorPages[a].listView.getAdapter();
        animationEditorPages[a].listView.setPinnedHeaderShadowDrawable(null);

        if (animationEditorPages[a].selectedType == 0) {
            if (currentAdapter != backgroundAdapter) {
                animationEditorPages[a].listView.setAdapter(backgroundAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 1) {
            if (currentAdapter != shortTextAdapter) {
                animationEditorPages[a].listView.setAdapter(shortTextAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 2) {
            if (currentAdapter != longTextAdapter) {
                animationEditorPages[a].listView.setAdapter(longTextAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 3) {
            if (currentAdapter != linkAdapter) {
                animationEditorPages[a].listView.setAdapter(linkAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 4) {
            if (currentAdapter != emojiAdapter) {
                animationEditorPages[a].listView.setAdapter(emojiAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 5) {
            if (currentAdapter != stickerAdapter) {
                animationEditorPages[a].listView.setAdapter(stickerAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 6) {
            if (currentAdapter != voiceAdapter) {
                animationEditorPages[a].listView.setAdapter(voiceAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 7) {
            if (currentAdapter != videoAdapter) {
                animationEditorPages[a].listView.setAdapter(videoAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 8) {
            if (currentAdapter != gifAdapter) {
                animationEditorPages[a].listView.setAdapter(gifAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 9) {
            if (currentAdapter != attachmentAdapter) {
                animationEditorPages[a].listView.setAdapter(attachmentAdapter);
            }
        }
        animationEditorPages[a].listView.setVisibility(View.VISIBLE);

        if (actionBar.getTranslationY() != 0) {
            animationEditorPages[a].layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabActiveText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabUnactiveText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, new Drawable[]{scrollSlidingTextTabStrip.getSelectorDrawable()}, null, Theme.key_actionBarTabSelector));

        for (int a = 0; a < animationEditorPages.length; a++) {
            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
            arrayList.add(new ThemeDescription(animationEditorPages[a].listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        }


        return arrayList;
    }

    private static class AnimationEditorPage extends FrameLayout {
        private RecyclerListView listView;
        private LinearLayoutManager layoutManager;
        private int selectedType;

        public AnimationEditorPage(Context context) {
            super(context);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;
        private FourGradientBackgroundView backgroundView;
        private AnimationParams typeSetting;

        private final AnimationItemType currentType;

        private  int durationRow= -1;
        private  int durationSection2Row= -1;

        private  int backgroundSectionRow= -1;
        private  int backgroundRow= -1;
        private  int backgroundOpenFullScreenRow= -1;
        private  int backgroundSection2Row= -1;

        private  int colorsSectionRow= -1;
        private  int color1Row= -1;
        private  int color2Row= -1;
        private  int color3Row= -1;
        private  int color4Row= -1;
        private  int colorsSection2Row= -1;

        private  int sendMessageSectionRow= -1;
        private  int durationSendMessageRow= -1;
        private  int sendMessagePickerRow= -1;
        private  int sendMessageSection2Row= -1;

        private  int openChatSectionRow= -1;
        private  int durationOpenChatRow= -1;
        private  int openChatPickerRow= -1;
        private  int openChatSection2Row= -1;

        private  int jumpToMessageSectionRow= -1;
        private  int durationJumpToMessageRow= -1;
        private  int jumpToMessagePickerRow= -1;
        private  int jumpToMessageSection2Row= -1;

        private  int xPositionSectionRow= -1;
        private  int xPositionPickerRow= -1;
        private  int xPositionSection2Row= -1;

        private  int yPositionSectionRow= -1;
        private  int yPositionPickerRow= -1;
        private  int yPositionSection2Row= -1;

        private  int bubbleShapeSectionRow= -1;
        private  int bubbleShapePickerRow= -1;
        private  int bubbleShapeSection2Row= -1;

        private  int textScaleSectionRow= -1;
        private  int textScalePickerRow= -1;
        private  int textScaleSection2Row= -1;

        private  int colorChangeSectionRow= -1;
        private  int colorChangePickerRow= -1;
        private  int colorChangeSection2Row= -1;

        private  int timeAppearsSectionRow= -1;
        private  int timeAppearsPickerRow= -1;
        private  int timeAppearsSection2Row= -1;

        private  int emojiScaleSectionRow= -1;
        private  int emojiScalePickerRow= -1;
        private  int emojiScaleSection2Row= -1;

        private  int voiceScaleSectionRow= -1;
        private  int voiceScalePickerRow= -1;
        private  int voiceScaleSection2Row= -1;

        private  int gifScaleSectionRow= -1;
        private  int gifScalePickerRow= -1;
        private  int gifScaleSection2Row= -1;

        private int rowCount;

        private AnimationEditorView.CurveChangedListener curveChangedListener = (sender, param) -> {
            saveParams();
        };

        public void saveParams() {
            if (typeSetting == null) return;
            AnimationParamsHolder.instance.getAnimationParamsForType(typeSetting.animationType).apply(typeSetting);
        }

        public void getParams() {
            if (currentType == null) return;
            AnimationParams original = AnimationParamsHolder.instance.getAnimationParamsForType(currentType);
            typeSetting = original.clone();
        }

        public ListAdapter(Context context, AnimationItemType type) {
            mContext = context;
            currentType = type;

            getParams();

            rowCount = 0;

            if (type == AnimationItemType.ShortText || type == AnimationItemType.LongText || type == AnimationItemType.Link ||
                    type == AnimationItemType.Emoji || type == AnimationItemType.Sticker || type == AnimationItemType.VoiceMessage ||
                    type == AnimationItemType.VideoMessage || type == AnimationItemType.Gif || type == AnimationItemType.Attachment) {
                durationRow = rowCount++;
                durationSection2Row = rowCount++;
            }

            if (type == AnimationItemType.Background) {
                backgroundSectionRow = rowCount++;
                backgroundRow = rowCount++;
                backgroundOpenFullScreenRow = rowCount++;
                backgroundSection2Row = rowCount++;

                colorsSectionRow = rowCount++;
                color1Row = rowCount++;
                color2Row = rowCount++;
                color3Row = rowCount++;
                color4Row = rowCount++;
                colorsSection2Row = rowCount++;

                sendMessageSectionRow = rowCount++;
                durationSendMessageRow = rowCount++;
                sendMessagePickerRow = rowCount++;
                sendMessageSection2Row = rowCount++;

                openChatSectionRow = rowCount++;
                durationOpenChatRow = rowCount++;
                openChatPickerRow = rowCount++;
                openChatSection2Row = rowCount++;

                jumpToMessageSectionRow = rowCount++;
                durationJumpToMessageRow = rowCount++;
                jumpToMessagePickerRow = rowCount++;
                jumpToMessageSection2Row = rowCount++;
            }

            if (type == AnimationItemType.ShortText || type == AnimationItemType.LongText || type == AnimationItemType.Link ||
                    type == AnimationItemType.Emoji || type == AnimationItemType.Sticker || type == AnimationItemType.VoiceMessage ||
                    type == AnimationItemType.VideoMessage || type == AnimationItemType.Gif || type == AnimationItemType.Attachment) {
                xPositionSectionRow = rowCount++;
                xPositionPickerRow = rowCount++;
                xPositionSection2Row = rowCount++;

                yPositionSectionRow = rowCount++;
                yPositionPickerRow = rowCount++;
                yPositionSection2Row = rowCount++;
            }

            if (type == AnimationItemType.ShortText || type == AnimationItemType.LongText || type == AnimationItemType.Link ||
                    type == AnimationItemType.VoiceMessage || type == AnimationItemType.Gif) {
                bubbleShapeSectionRow = rowCount++;
                bubbleShapePickerRow = rowCount++;
                bubbleShapeSection2Row = rowCount++;

                if (type != AnimationItemType.Gif) {
                    if (type != AnimationItemType.VoiceMessage) {
                        textScaleSectionRow = rowCount++;
                        textScalePickerRow = rowCount++;
                        textScaleSection2Row = rowCount++;
                    }

                    colorChangeSectionRow = rowCount++;
                    colorChangePickerRow = rowCount++;
                    colorChangeSection2Row = rowCount++;
                }
            }

            if (type == AnimationItemType.VoiceMessage) {
                voiceScaleSectionRow = rowCount++;
                voiceScalePickerRow = rowCount++;
                voiceScaleSection2Row = rowCount++;
            }

            if (type == AnimationItemType.Emoji || type == AnimationItemType.Sticker) {
                emojiScaleSectionRow = rowCount++;
                emojiScalePickerRow = rowCount++;
                emojiScaleSection2Row = rowCount++;
            }

            if (type == AnimationItemType.Gif) {
                gifScaleSectionRow = rowCount++;
                gifScalePickerRow = rowCount++;
                gifScaleSection2Row = rowCount++;
            }

            if (type == AnimationItemType.ShortText || type == AnimationItemType.LongText || type == AnimationItemType.Link || type == AnimationItemType.Emoji ||
                    type == AnimationItemType.Sticker || type == AnimationItemType.VoiceMessage || type == AnimationItemType.VideoMessage || type == AnimationItemType.Gif ||
                    type == AnimationItemType.Attachment) {
                timeAppearsSectionRow = rowCount++;
                timeAppearsPickerRow = rowCount++;
                timeAppearsSection2Row = rowCount++;
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;

                    if (position == backgroundOpenFullScreenRow) {
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText2);
                        textCell.setText("Open Full Screen", false);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                    } else {
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

                        if (position == durationRow) {
                            int dur  = (int) (typeSetting.getDuration() / 60f * 1000);
                            textCell.setTextAndValue("Duration", dur + "ms", false);
                        } else if (position == durationSendMessageRow) {
                            int dur  = (int) (typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnSendMessage).duration / 60f * 1000);
                            textCell.setTextAndValue("Duration", dur + "ms", true);
                        } else if (position == durationOpenChatRow) {
                            int dur  = (int) (typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnOpenChat).duration / 60f * 1000);
                            textCell.setTextAndValue("Duration", dur + "ms", true);
                        } else if (position == durationJumpToMessageRow) {
                            int dur  = (int) (typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnJumpToMessage).duration / 60f * 1000);
                            textCell.setTextAndValue("Duration", dur + "ms", true);
                        }
                    }

                    break;
                }
                case 2: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == backgroundSectionRow) {
                        headerCell.setText("Background Preview");
                    } else if (position == xPositionSectionRow) {
                        headerCell.setText("X Position");
                    } else if (position == yPositionSectionRow) {
                        headerCell.setText("Y Position");
                    } else if (position == bubbleShapeSectionRow) {
                        headerCell.setText("Bubble Shape");
                    } else if (position == colorChangeSectionRow) {
                        headerCell.setText("Color Change");
                    } else if (position == colorsSectionRow) {
                        headerCell.setText("Colors");
                    } else if (position == emojiScaleSectionRow) {
                        headerCell.setText("Emoji Scale");
                    } else if (position == voiceScaleSectionRow) {
                        headerCell.setText("Voice Scale");
                    } else if (position == jumpToMessageSectionRow) {
                        headerCell.setText("Jump To Message");
                    } else if (position == openChatSectionRow) {
                        headerCell.setText("Open Chat");
                    } else if (position == sendMessageSectionRow) {
                        headerCell.setText("Send Message");
                    } else if (position == textScaleSectionRow) {
                        headerCell.setText("Text Scale");
                    } else if (position == timeAppearsSectionRow) {
                        headerCell.setText("Time Appears");
                    } else if (position == gifScaleSectionRow) {
                        headerCell.setText("Gif Scale");
                    }
                    break;
                }
                case 3: {
                    FourGradientBackgroundView bg = (FourGradientBackgroundView) holder.itemView;
                    bg.setData((BackgroundGradientParams) typeSetting);
                    break;
                }
                case 4: {
                    ColorCell colorCell = (ColorCell) holder.itemView;
                    int number = 0;
                    int color = 0;
                    BackgroundGradientParams params = (BackgroundGradientParams) typeSetting;
                    if (position == color1Row) {
                        number = 1;
                        color = params.color1;
                    } else if (position == color2Row) {
                        number = 2;
                        color = params.color2;
                    } else if (position == color3Row) {
                        number = 3;
                        color = params.color3;
                    } else if (position == color4Row) {
                        number = 4;
                        color = params.color4;
                    }
                    colorCell.setData(number, color);
                    break;
                }
                case 5: {
                    AnimationEditorView ed = (AnimationEditorView) holder.itemView;
                    if (position == xPositionPickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.XPosition), curveChangedListener);
                    } else if (position == yPositionPickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.YPosition), curveChangedListener);
                    } else if (position == bubbleShapePickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.BubbleShape), curveChangedListener);
                    } else if (position == colorChangePickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.ColorChange), curveChangedListener);
                    } else if (position == emojiScalePickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.EmojiScale), curveChangedListener);
                    } else if (position == voiceScalePickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.VoiceScale), curveChangedListener);
                    } else if (position == jumpToMessagePickerRow) {
                        ed.setDuration(typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnJumpToMessage).duration);
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnJumpToMessage), curveChangedListener);
                    } else if (position == openChatPickerRow) {
                        ed.setDuration(typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnOpenChat).duration);
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnOpenChat), curveChangedListener);
                    } else if (position == sendMessagePickerRow) {
                        ed.setDuration(typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnSendMessage).duration);
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.BackgroundGradientChangeOnSendMessage), curveChangedListener);
                    } else if (position == textScalePickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.TextScale), curveChangedListener);
                    } else if (position == timeAppearsPickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.TimeAppears), curveChangedListener);
                    } else if (position == gifScalePickerRow) {
                        ed.setDuration(typeSetting.getDuration());
                        ed.setData(typeSetting.getInterpolation(AnimationParamType.GifScale), curveChangedListener);
                    }
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() == durationRow || holder.getAdapterPosition() == durationJumpToMessageRow ||
                    holder.getAdapterPosition() == durationOpenChatRow || holder.getAdapterPosition() == durationSendMessageRow ||
                    holder.getAdapterPosition() == backgroundOpenFullScreenRow || holder.getAdapterPosition() == color1Row ||
                    holder.getAdapterPosition() == color2Row || holder.getAdapterPosition() == color3Row || holder.getAdapterPosition() == color4Row;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = backgroundView = new FourGradientBackgroundView(context, true, null);
                    view.setMinimumHeight(AndroidUtilities.dp(200));
                    break;
                case 4:
                    view = new ColorCell(context);
                    break;
                case 5:
                    view = new AnimationEditorView(context, (shouldBlockTouch -> {
                        shouldBlockTouches = shouldBlockTouch;
                    }));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == xPositionSection2Row || position == yPositionSection2Row || position == backgroundSection2Row ||
                    position == bubbleShapeSection2Row || position == colorChangeSection2Row || position == colorsSection2Row ||
                    position == durationSection2Row || position == emojiScaleSection2Row || position == jumpToMessageSection2Row ||
                    position == openChatSection2Row || position == sendMessageSection2Row || position == textScaleSection2Row ||
                    position == timeAppearsSection2Row || position == voiceScaleSection2Row || position == gifScaleSection2Row) {
                return 0;
            } else if (position == durationJumpToMessageRow || position == durationOpenChatRow || position == durationRow ||
                    position == durationSendMessageRow || position == backgroundOpenFullScreenRow) {
                return 1;
            } else if (position == xPositionSectionRow || position == yPositionSectionRow || position == backgroundSectionRow ||
                    position == bubbleShapeSectionRow || position == colorChangeSectionRow || position == colorsSectionRow ||
                    position == timeAppearsSectionRow || position == emojiScaleSectionRow || position == jumpToMessageSectionRow ||
                    position == openChatSectionRow || position == sendMessageSectionRow || position == textScaleSectionRow ||
                    position == voiceScaleSectionRow || position == gifScaleSectionRow) {
                return 2;
            } else if (position == backgroundRow) {
                return 3;
            } else if (position == color1Row || position == color2Row || position == color3Row || position == color4Row) {
                return 4;
            } else if (position == xPositionPickerRow || position == yPositionPickerRow || position == bubbleShapePickerRow ||
                    position == colorChangePickerRow || position == emojiScalePickerRow || position == jumpToMessagePickerRow ||
                    position == openChatPickerRow || position == sendMessagePickerRow || position == textScalePickerRow ||
                    position == timeAppearsPickerRow || position == voiceScalePickerRow || position == gifScalePickerRow) {
                return 5;
            }
            return 0;
        }
    }
}
