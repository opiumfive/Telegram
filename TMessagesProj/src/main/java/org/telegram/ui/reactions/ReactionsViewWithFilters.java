package org.telegram.ui.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTabStrip;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.ViewPagerFixed;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReactionsViewWithFilters extends LinearLayout {

    private MessageObject message;
    private ChatActivity chatActivity;


    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };
    private Context context;
    private TabLayout scrollSlidingTabStrip;
    private final ReactionsPage[] animationEditorPages = new ReactionsPage[2];
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;
    private int maximumVelocity;
    private final Paint backgroundPaint = new Paint();
    private boolean swipeBackEnabled = true;
    private ActionBar actionBar;
    ViewPager viewPager;
    private boolean firstTimeFakeDelay = true;

    public ReactionsViewWithFilters(ChatActivity chatActivity, MessageObject messageObject, Runnable onDismiss, Runnable onChoose, List<TLRPC.User> seen) {
        super(chatActivity.getContentView().getContext());
        this.context = chatActivity.getContentView().getContext();
        this.message = messageObject;
        this.chatActivity = chatActivity;
        setOrientation(VERTICAL);

        actionBar = new ActionBar(context);
        actionBar.setOnClickListener(v -> {
            if (onDismiss != null) onDismiss.run();
        });

        ArrayList<Tab> tabs = messageObject.messageOwner.reactions.results.stream().map(o1 -> new Tab(o1.reaction, o1.count)).collect(Collectors.toCollection(ArrayList::new));
        int sum = 0;
        for (TLRPC.TL_reactionCount c: messageObject.messageOwner.reactions.results) sum += c.count;
        tabs.add(0, new Tab("", sum));
        addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, sum > 10 ? 48 : 40));
        scrollSlidingTabStrip = new TabLayout(context, tabs, new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int id, boolean forward) {
                viewPager.setCurrentItem(id);
            }

            @Override
            public void onPageScrolled(float progress) {

            }
        });
        scrollSlidingTabStrip.setFadingEdgeLength(AndroidUtilities.dp(5));
        scrollSlidingTabStrip.setHorizontalFadingEdgeEnabled(true);
        if (sum > 10) {
            addView(scrollSlidingTabStrip, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 32));
        }
        addView(new DividerCell(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));
        if (sum > 10) {
            viewPager = new ViewPager(context);
            viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                }

                @Override
                public void onPageSelected(int position) {
                    scrollSlidingTabStrip.select(position);
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                }
            });

            viewPager.setAdapter(new PagerAdapter() {
                @Override
                public int getCount() {
                    return tabs.size();
                }

                @Override
                public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                    container.removeView((ReactionUsersListView) object);
                }

                @NonNull
                @Override
                public Object instantiateItem(@NonNull ViewGroup container, int position) {
                    String reaction = tabs.get(position).reaction;
                    if (TextUtils.isEmpty(reaction)) reaction = null;
                    ReactionUsersListView itemView = new ReactionUsersListView(chatActivity, message, reaction, null, null, onChoose, firstTimeFakeDelay, position == 0 ? seen : null, false);
                    if (firstTimeFakeDelay) firstTimeFakeDelay = false;
                    container.addView(itemView);

                    return itemView;
                }

                @Override
                public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                    return view == object;
                }
            });
            viewPager.setOverScrollMode(OVER_SCROLL_NEVER);

            addView(viewPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            String reaction = tabs.get(0).reaction;
            if (TextUtils.isEmpty(reaction)) reaction = null;
            ReactionUsersListView itemView = new ReactionUsersListView(chatActivity, message, reaction, null, null, onChoose, true, seen, false);
            addView(itemView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
    }

    public boolean isOnFirstTab() {
        return scrollSlidingTabStrip == null || scrollSlidingTabStrip.selected == 0;
    }


    private static class ReactionsPage extends FrameLayout {
        private ReactionUsersListView listView;
        private int selectedType;

        public ReactionsPage(Context context) {
            super(context);
        }
    }

    private static class ActionBar extends FrameLayout {
        public ActionBar(Context context) {
            super(context);

            ImageView backButtonImageView = new ImageView(getContext());
            backButtonImageView.setImageResource(R.drawable.ic_ab_back);
            backButtonImageView.setScaleType(ImageView.ScaleType.CENTER);
            backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarDefaultSelector)));
            backButtonImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
            backButtonImageView.setPadding(AndroidUtilities.dp(1), 0, 0, 0);

            SimpleTextView titleTextView = new SimpleTextView(getContext());
            titleTextView.setGravity(Gravity.LEFT);
            titleTextView.setTextSize(18);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setText("Back");

            addView(backButtonImageView, LayoutHelper.createFrame(48, 48));
            addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 54, 13, 0, 1));
        }
    }

    private static class Tab {
        String reaction;
        int count;

        public Tab(String reaction, int count) {
            this.reaction = reaction;
            this.count = count;
        }
    }

    private static class TabLayout extends FrameLayout {

        List<Tab> reactions = new ArrayList<>();
        RecyclerListView tabList;
        RecyclerListView.SelectionAdapter adapter;
        int selected = 0;
        ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate selectedListener;


        public void select(int s) {
            int oldS = selected;
            selected = s;
            adapter.notifyItemChanged(oldS);
            adapter.notifyItemChanged(selected);
            tabList.scrollToPosition(selected);
        }

        public TabLayout(Context context, List<Tab> tabs, ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate selectedListener) {
            super(context);
            this.reactions.addAll(tabs);
            this.selectedListener = selectedListener;
            tabList = new RecyclerListView(context);
            tabList.setSelectorDrawableColor(Color.TRANSPARENT);
            tabList.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
            tabList.setItemAnimator(null);
            tabList.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    int p = parent.getChildAdapterPosition(view);
                    if (p == 0) {
                        outRect.left = AndroidUtilities.dp(12);
                        outRect.right = AndroidUtilities.dp(4);
                    } else if (p == reactions.size() - 1) {
                        outRect.left = AndroidUtilities.dp(4);
                        outRect.right = AndroidUtilities.dp(12);
                    } else {
                        outRect.left = AndroidUtilities.dp(4);
                        outRect.right = AndroidUtilities.dp(4);
                    }
                }
            });
            adapter = new RecyclerListView.SelectionAdapter() {

                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    BackupImageView reaction = new BackupImageView(context);
                    ImageView reaction2 = new ImageView(context);
                    TextView count = new TextView(context);
                    count.setTextColor(0xCC378DD1);
                    count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    count.setTypeface(null, Typeface.BOLD);
                    FrameLayout par = new FrameLayout(context);
                    par.setBackgroundResource(R.drawable.reaction_tab);
                    par.addView(reaction, LayoutHelper.createFrame(21, 21, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
                    par.addView(reaction2, LayoutHelper.createFrame(26, 26, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));
                    par.addView(count, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 35, 0, 12, 0));
                    par.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.WRAP_CONTENT, AndroidUtilities.dp(30)));
                    return new RecyclerListView.Holder(par);
                }

                @Override
                public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    FrameLayout cell = (FrameLayout) holder.itemView;
                    cell.setSelected(selected == holder.getAdapterPosition());
                    BackupImageView reaction = (BackupImageView) cell.getChildAt(0);
                    ImageView reaction2 = (ImageView) cell.getChildAt(1);
                    TextView count = (TextView) cell.getChildAt(2);
                    Tab tab = reactions.get(holder.getAdapterPosition());

                    if (tab.reaction.equals("")) {
                        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions_filled);
                        drawable.setColorFilter(new PorterDuffColorFilter(0xCC378DD1, PorterDuff.Mode.MULTIPLY));
                        reaction2.setImageDrawable(drawable);
                        reaction.setImageDrawable(null);
                    } else {
                        ImageLocation lastEmoji = null;
                        Drawable lastEmojiThumb = null;

                        if (reaction != null) {
                            ArrayList<TLRPC.TL_availableReaction> availableReactions = MediaDataController.getInstance(UserConfig.selectedAccount).getAvailableReactions();
                            Optional<TLRPC.TL_availableReaction> found = availableReactions.stream().filter(r -> r.reaction.equals(tab.reaction)).findFirst();
                            if (found.isPresent()) {
                                TLRPC.TL_availableReaction data = found.get();
                                lastEmoji = ImageLocation.getForDocument(data.static_icon);
                                lastEmojiThumb = Emoji.getEmojiDrawable(data.reaction);
                            } else {
                                lastEmojiThumb = Emoji.getEmojiDrawable(tab.reaction);
                            }
                        }

                        if (lastEmoji != null) {
                            reaction.setImage(lastEmoji, "21_21", lastEmojiThumb, null);
                        } else {
                            reaction.setImageDrawable(lastEmojiThumb);
                        }
                        reaction2.setImageDrawable(null);
                    }
                    count.setText(String.format("%s", LocaleController.formatShortNumber(Math.max(1, tab.count), null)));

                }

                @Override
                public int getItemCount() {
                    return reactions.size();
                }

            };
            tabList.setOnItemClickListener((view, position) -> {
                if (selectedListener != null) {
                    int position1 = position;
                    if (position1 < 0) {
                        return;
                    }
                    if (position1 == selected) {
                        selectedListener.onSamePageSelected();
                        return;
                    }
                    boolean scrollingForward = selected < position1;
                    selectedListener.onPageSelected(position, scrollingForward);
                }
            });
            tabList.setAdapter(adapter);
            addView(tabList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30));
        }

        public int getFirstTabId() {
            return 0;
        }

        public int getNextPageId(boolean forward) {
            int ne = selected + (forward ? 1 : -1);
            if (ne < 0 || ne >= reactions.size()) return -1;
            return ne;
        }

        public void selectTabWithId(int selectedType, float i) {
            if (i == 1.0f) {
                select(selectedType);
            }
        }

        public int getCurrentTabId() {
            return selected;
        }
    }
}
