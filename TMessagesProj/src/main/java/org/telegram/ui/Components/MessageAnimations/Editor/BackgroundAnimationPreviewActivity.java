package org.telegram.ui.Components.MessageAnimations.Editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParamType;
import org.telegram.ui.Components.MessageAnimations.Editor.data.BackgroundGradientParams;
import org.telegram.ui.Components.MessageAnimations.FourGradientBackground.FourGradientBackgroundView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

import java.util.ArrayList;

public class BackgroundAnimationPreviewActivity extends BaseFragment {

    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };
    private Context context;
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private final AnimationEditorPage[] animationEditorPages = new AnimationEditorPage[2];
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;
    private int maximumVelocity;
    private final Paint backgroundPaint = new Paint();
    private boolean swipeBackEnabled = true;

    private ListAdapter onSendAdapter;
    private ListAdapter onOpenAdapter;
    private ListAdapter onJumpAdapter;
    private BackgroundGradientParams setting;

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return swipeBackEnabled;
    }

    @Override
    public boolean onBackPressed() {
        return super.onBackPressed();
    }

    public BackgroundAnimationPreviewActivity(BackgroundGradientParams setting) {
        this.setting = setting;
    }

    @Override
    public View createView(Context context) {
        this.context = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.setTitle("Background Preview");

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
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        onSendAdapter = new ListAdapter(context, AnimationParamType.BackgroundGradientChangeOnSendMessage);
        onOpenAdapter = new ListAdapter(context, AnimationParamType.BackgroundGradientChangeOnOpenChat);
        onJumpAdapter = new ListAdapter(context, AnimationParamType.BackgroundGradientChangeOnJumpToMessage);

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
                    return false;
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
                if (position == adapter.animateRow) {
                    if (adapter.currentType == AnimationParamType.BackgroundGradientChangeOnSendMessage) {
                        if (adapter.backgroundView != null) {
                            adapter.backgroundView.startAnimation(AnimationParamType.BackgroundGradientChangeOnSendMessage);
                        }
                    } else if (adapter.currentType == AnimationParamType.BackgroundGradientChangeOnOpenChat) {
                        if (adapter.backgroundView != null) {
                            adapter.backgroundView.startAnimation(AnimationParamType.BackgroundGradientChangeOnOpenChat);
                        }
                    } else if (adapter.currentType == AnimationParamType.BackgroundGradientChangeOnJumpToMessage) {
                        if (adapter.backgroundView != null) {
                            adapter.backgroundView.startAnimation(AnimationParamType.BackgroundGradientChangeOnJumpToMessage);
                        }
                    }
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
        if (onSendAdapter != null) {
            onSendAdapter.notifyDataSetChanged();
        }
        if (onOpenAdapter != null) {
            onOpenAdapter.notifyDataSetChanged();
        }
        if (onJumpAdapter != null) {
            onJumpAdapter.notifyDataSetChanged();
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
        scrollSlidingTextTabStrip.addTextTab(0, "Send Message");
        scrollSlidingTextTabStrip.addTextTab(1, "Open Chat");
        scrollSlidingTextTabStrip.addTextTab(2, "Jump To Message");

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
            if (currentAdapter != onSendAdapter) {
                animationEditorPages[a].listView.setAdapter(onSendAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 1) {
            if (currentAdapter != onOpenAdapter) {
                animationEditorPages[a].listView.setAdapter(onOpenAdapter);
            }
        } else if (animationEditorPages[a].selectedType == 2) {
            if (currentAdapter != onJumpAdapter) {
                animationEditorPages[a].listView.setAdapter(onJumpAdapter);
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
        private final AnimationParamType currentType;
        private FourGradientBackgroundView backgroundView;

        private  int backgroundRow= -1;
        private  int animateRow= -1;
        private int additionalRow = -1;

        private int rowCount;

        public ListAdapter(Context context, AnimationParamType type) {
            mContext = context;
            currentType = type;

            rowCount = 0;

            backgroundRow = rowCount++;
            animateRow = rowCount++;
            additionalRow = rowCount++;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText("ANIMATE");
                    break;
                }
                case 1: {
                    FourGradientBackgroundView bg = (FourGradientBackgroundView) holder.itemView;
                    bg.setData(setting);
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() == animateRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    ((HeaderCell) view).getTextView().setGravity(Gravity.CENTER);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view.setMinimumHeight(AndroidUtilities.dp(50));
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    break;
                case 1:
                    view = backgroundView = new FourGradientBackgroundView(context, false, null);
                    int height = Resources.getSystem().getDisplayMetrics().heightPixels;
                    WindowManager windowManager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                    final Display display = windowManager.getDefaultDisplay();
                    Point outPoint = new Point();
                    if (Build.VERSION.SDK_INT >= 19) {
                        display.getRealSize(outPoint);
                    } else {
                        display.getSize(outPoint);
                    }
                    if (outPoint.y > outPoint.x) {
                        height = outPoint.y;
                    } else {
                        height = outPoint.x;
                    }
                    view.setMinimumHeight(height - AndroidUtilities.dp(190));
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, height - AndroidUtilities.dp(190)));
                    break;
                case 2:
                    view = new View(context);
                    view.setBackgroundColor(Color.WHITE);
                    view.setMinimumHeight(AndroidUtilities.dp(20));
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(20)));
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == animateRow) return 0;
            if (position == backgroundRow) return 1;
            if (position == additionalRow) return 2;
            return 0;
        }
    }
}
