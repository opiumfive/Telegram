package org.telegram.ui.reactions;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PollVotesAlert;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ReactionUsersListView extends LinearLayout {

    public RecyclerListView listView;
    public ListAdapter listAdapter;
    private MessageObject message;
    private ChatActivity chatActivity;
    private Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient placeholderGradient;
    private Matrix placeholderMatrix;
    private float totalTranslation;
    private float gradientWidth;
    private boolean loadingResults = true;
    private RectF rect = new RectF();
    private ArrayList<TLRPC.User> users = new ArrayList<>();
    private ArrayList<TLRPC.TL_messageUserReaction> reactions = new ArrayList<>();
    private int maxCount = 0;
    private String offset = null;
    public LinearLayoutManager linearLayoutManager;
    private boolean addedSeen = false;
    private List<TLRPC.User> seen;
    private boolean inited = false;

    public ReactionUsersListView(ChatActivity chatActivity, MessageObject messageObject, String reaction, TLRPC.TL_messages_messageReactionsList initial, Runnable onShouldDismiss, Runnable onChoose, boolean fakeDelay, List<TLRPC.User> seen, boolean showBack) {
        super(chatActivity.getContentView().getContext());
        this.message = messageObject;
        this.chatActivity = chatActivity;
        this.seen = seen;
        setOrientation(VERTICAL);

        listAdapter = new ListAdapter(chatActivity.getContentView().getContext());
        listView = new RecyclerListView(chatActivity.getContentView().getContext()) {
            long lastUpdateTime;

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (loadingResults) {
                    long newUpdateTime = SystemClock.elapsedRealtime();
                    long dt = Math.abs(lastUpdateTime - newUpdateTime);
                    if (dt > 17) {
                        dt = 16;
                    }
                    lastUpdateTime = newUpdateTime;
                    totalTranslation += dt * gradientWidth / 450.0f;
                    while (totalTranslation >= gradientWidth * 2) {
                        totalTranslation -= gradientWidth * 2;
                    }
                    placeholderMatrix.setTranslate(totalTranslation, 0);
                    placeholderGradient.setLocalMatrix(placeholderMatrix);
                    invalidateViews();
                    invalidate();
                }
                super.dispatchDraw(canvas);
            }
        };
        linearLayoutManager = new LinearLayoutManager(chatActivity.getContentView().getContext(), LinearLayoutManager.VERTICAL, false);
        linearLayoutManager.setItemPrefetchEnabled(false);
        listView.setLayoutManager(linearLayoutManager);
        listView.setAdapter(listAdapter);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setSelectorDrawableColor(0);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(linearLayoutManager, (page, totalItemsCount, view) -> {
            if (users.size() > 0 && users.size() < maxCount && inited) {
                load(reaction);
            } else if (users.size() == maxCount && maxCount != 0) {
                addSeen();
            }
        });
        listView.setOnScrollListener(scrollListener);
        listView.setOnItemClickListener((view, position) -> {
            UserCell userCell = (UserCell) view;
            if (userCell.currentUser == null) {
                return;
            }
            TLRPC.User currentUser = chatActivity.getCurrentUser();
            Bundle args = new Bundle();
            args.putLong("user_id", userCell.currentUser.id);
            if (onShouldDismiss != null) onShouldDismiss.run();
            if (onChoose != null) onChoose.run();
            ProfileActivity fragment = new ProfileActivity(args);
            fragment.setPlayProfileAnimation(currentUser != null && currentUser.id == userCell.currentUser.id ? 1 : 0);
            chatActivity.presentFragment(fragment);
        });
        if (showBack) {
            TextView nameView = new TextView(chatActivity.getContentView().getContext());
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            nameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            nameView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameView.setLines(1);
            nameView.setText("Back");
            nameView.setPadding(AndroidUtilities.dp(64), AndroidUtilities.dp(12), 0, 0);
            TypedValue outValue = new TypedValue();
            chatActivity.getContentView().getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            nameView.setBackgroundResource(outValue.resourceId);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            nameView.setOnClickListener((v) -> {
                if (onShouldDismiss != null) onShouldDismiss.run();
            });
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
            View finalMessageReactionsView = listView;
            FrameLayout temp = new FrameLayout(chatActivity.getContentView().getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(finalMessageReactionsView.getMeasuredWidth()), MeasureSpec.EXACTLY), heightMeasureSpec);
                }
            };
            ShadowSectionCell dividerCell = new ShadowSectionCell(chatActivity.getContentView().getContext(), 10, Theme.getColor(Theme.key_windowBackgroundGray));
            temp.addView(dividerCell);
            addView(temp);
        }
        addView(listView);
        updatePlaceholder();

        listAdapter.update();
        if (fakeDelay) {
            loadingResults = true;

            postDelayed(() -> {
                preload(reaction, initial);
            }, 220);
        } else {
            preload(reaction, initial);
        }

    }

    private void addSeen() {
        if (!addedSeen && seen != null && !seen.isEmpty()) {
            addedSeen = true;
            for (TLRPC.User s : seen) {
                boolean contains = false;
                for (TLRPC.User u : users) {
                    if (u.id == s.id) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    users.add(s);
                }
            }

            listAdapter.update();
        } else {
            addedSeen = true;
            listAdapter.update();
        }
    }

    private void preload(String reaction, TLRPC.TL_messages_messageReactionsList initial) {
        if (initial == null) {
            load(reaction);
        } else {
            users.addAll(initial.users);
            reactions.addAll(initial.reactions);
            if (maxCount == 0) {
                maxCount = initial.count;
            }
            offset = initial.next_offset;


            if (maxCount == users.size()) {
                addSeen();
            } else {
                listAdapter.update();
            }
            listView.post(() -> {
                inited = true;
            });
        }
    }

    private void load(String reaction) {
        loadingResults = true;
        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.peer = chatActivity.getMessagesController().getInputPeer(message.getDialogId());
        req.id = message.getId();
        req.limit = 50;
        req.reaction = reaction;
        req.offset = offset;
        if (reaction != null) {
            req.flags |= 1;
        }
        if (offset != null) {
            req.flags |= 2;
        }
        chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingResults = false;
            if (response instanceof TLRPC.TL_messages_messageReactionsList) {
                TLRPC.TL_messages_messageReactionsList list = (TLRPC.TL_messages_messageReactionsList) response;
                users.addAll(list.users);
                reactions.addAll(list.reactions);
                if (maxCount == 0) {
                    maxCount = list.count;
                }
                offset = list.next_offset;

                if (maxCount == users.size()) {
                    addSeen();
                } else {
                    listAdapter.update();
                }
                listView.post(() -> {
                    inited = true;
                });

            }
        }));
    }

    public static final Property<UserCell, Float> USER_CELL_PROPERTY = new AnimationProperties.FloatProperty<UserCell>("placeholderAlpha") {
        @Override
        public void setValue(UserCell object, float value) {
            object.setPlaceholderAlpha(value);
        }

        @Override
        public Float get(UserCell object) {
            return object.getPlaceholderAlpha();
        }
    };

    private void updatePlaceholder() {
        if (placeholderPaint == null) {
            return;
        }
        int color0 = Theme.getColor(Theme.key_dialogBackground);
        int color1 = Theme.getColor(Theme.key_dialogBackgroundGray);
        color0 = AndroidUtilities.getAverageColor(color1, color0);
        placeholderPaint.setColor(color1);
        placeholderGradient = new LinearGradient(0, 0, gradientWidth = AndroidUtilities.dp(200), 0, new int[]{color1, color0, color1}, new float[]{0.0f, 0.18f, 0.36f}, Shader.TileMode.REPEAT);
        placeholderPaint.setShader(placeholderGradient);
        placeholderMatrix = new Matrix();
        placeholderGradient.setLocalMatrix(placeholderMatrix);
    }

    public class UserCell extends FrameLayout {

        private BackupImageView avatarImageView;
        private SimpleTextView nameTextView;
        private BackupImageView emojiImageView;
        private AvatarDrawable avatarDrawable;
        private TLRPC.User currentUser;

        private String lastName;
        private int lastStatus;
        private ImageLocation lastEmoji;
        private Drawable lastEmojiThumb;
        private TLRPC.FileLocation lastAvatar;

        private int currentAccount = UserConfig.selectedAccount;

        private boolean needDivider;
        private int placeholderNum;
        private boolean drawPlaceholder;
        private float placeholderAlpha = 1.0f;

        private ArrayList<Animator> animators;
        private String currentReaction = null;

        public UserCell(Context context) {
            super(context);

            setWillNotDraw(false);

            avatarDrawable = new AvatarDrawable();

            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(18));
            addView(avatarImageView, LayoutHelper.createFrame(36, 36, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 14, 6, LocaleController.isRTL ? 14 : 0, 0));

            nameTextView = new SimpleTextView(context);
            nameTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameTextView.setTextSize(14);
            nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 65, 15, LocaleController.isRTL ? 65 : 40, 0));

            emojiImageView = new BackupImageView(context);
            addView(emojiImageView, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 21 : 64, 11, LocaleController.isRTL ? 64 : 12, 0));
        }

        public void setData(TLRPC.User user, String reaction) {
            currentUser = user;
            needDivider = false;
            drawPlaceholder = user == null;
            lastEmoji = null;
            currentReaction = reaction;
            if (reaction != null) {
                ArrayList<TLRPC.TL_availableReaction> availableReactions = MediaDataController.getInstance(UserConfig.selectedAccount).getAvailableReactions();
                Optional<TLRPC.TL_availableReaction> found = availableReactions.stream().filter(r -> r.reaction.equals(reaction)).findFirst();
                if (found.isPresent()) {
                    TLRPC.TL_availableReaction data = found.get();
                    lastEmoji = ImageLocation.getForDocument(data.static_icon);
                    lastEmojiThumb = Emoji.getEmojiDrawable(data.reaction);
                } else {
                    lastEmojiThumb = Emoji.getEmojiDrawable(reaction);
                }
            }

            placeholderNum = 10;
            if (user == null) {
                nameTextView.setText("");
                avatarImageView.setImageDrawable(null);
                emojiImageView.setImageDrawable(null);
            } else {
                update(0);
            }
            if (animators != null) {
                animators.add(ObjectAnimator.ofFloat(avatarImageView, View.ALPHA, 0.0f, 1.0f));
                animators.add(ObjectAnimator.ofFloat(nameTextView, View.ALPHA, 0.0f, 1.0f));
                animators.add(ObjectAnimator.ofFloat(this, USER_CELL_PROPERTY, 1.0f, 0.0f));
            } else if (!drawPlaceholder) {
                placeholderAlpha = 0.0f;
            }
        }

        @Keep
        public void setPlaceholderAlpha(float value) {
            placeholderAlpha = value;
            invalidate();
        }

        @Keep
        public float getPlaceholderAlpha() {
            return placeholderAlpha;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        public void update(int mask) {
            TLRPC.FileLocation photo = null;
            String newName = null;
            if (currentUser != null && currentUser.photo != null) {
                photo = currentUser.photo.photo_small;
            }

            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                    if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                        continueUpdate = true;
                    }
                }
                if (currentUser != null && !continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    int newStatus = 0;
                    if (currentUser.status != null) {
                        newStatus = currentUser.status.expires;
                    }
                    if (newStatus != lastStatus) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    if (currentUser != null) {
                        newName = UserObject.getUserName(currentUser);
                    }
                    if (!newName.equals(lastName)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate) {
                    return;
                }
            }

            avatarDrawable.setInfo(currentUser);
            if (currentUser.status != null) {
                lastStatus = currentUser.status.expires;
            } else {
                lastStatus = 0;
            }

            if (currentUser != null) {
                lastName = newName == null ? UserObject.getUserName(currentUser) : newName;
            } else {
                lastName = "";
            }
            nameTextView.setText(lastName);

            lastAvatar = photo;
            if (currentUser != null) {
                avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
            } else {
                avatarImageView.setImageDrawable(avatarDrawable);
            }

            if (currentReaction != null) {
                if (lastEmoji != null) {
                    emojiImageView.setImage(lastEmoji, "32_32", lastEmojiThumb, null);
                } else {
                    emojiImageView.setImageDrawable(lastEmojiThumb);
                }
            } else {
                emojiImageView.setImageDrawable(null);
            }
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawPlaceholder || placeholderAlpha != 0) {
                placeholderPaint.setAlpha((int) (255 * placeholderAlpha));
                int cx = avatarImageView.getLeft() + avatarImageView.getMeasuredWidth() / 2;
                int cy = avatarImageView.getTop() + avatarImageView.getMeasuredHeight() / 2;
                canvas.drawCircle(cx, cy, avatarImageView.getMeasuredWidth() / 2, placeholderPaint);

                cx = emojiImageView.getLeft() + emojiImageView.getMeasuredWidth() / 2;
                cy = emojiImageView.getTop() + emojiImageView.getMeasuredHeight() / 2;
                canvas.drawCircle(cx, cy, emojiImageView.getMeasuredWidth() / 2, placeholderPaint);

                int w;

                if (placeholderNum % 2 == 0) {
                    cx = AndroidUtilities.dp(65);
                    w = AndroidUtilities.dp(48);
                } else {
                    cx = AndroidUtilities.dp(65);
                    w = AndroidUtilities.dp(60);
                }
                if (LocaleController.isRTL) {
                    cx = getMeasuredWidth() - cx - w;
                }
                rect.set(cx, cy - AndroidUtilities.dp(4), cx + w, cy + AndroidUtilities.dp(4));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), placeholderPaint);

                if (placeholderNum % 2 == 0) {
                    cx = AndroidUtilities.dp(119);
                    w = AndroidUtilities.dp(60);
                } else {
                    cx = AndroidUtilities.dp(131);
                    w = AndroidUtilities.dp(80);
                }
                if (LocaleController.isRTL) {
                    cx = getMeasuredWidth() - cx - w;
                }
                rect.set(cx, cy - AndroidUtilities.dp(4), cx + w, cy + AndroidUtilities.dp(4));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), placeholderPaint);
            }
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(64), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(64) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    public class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return users.size() == 0 ? 10 : users.size();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new UserCell(context);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            UserCell cell = (UserCell) holder.itemView;
            if (users.size() == 0) {
                cell.setData(null, null);
            } else {
                TLRPC.User u = users.get(holder.getAdapterPosition());
                Optional<TLRPC.TL_messageUserReaction> opt = reactions.stream().filter(r -> r.user_id == u.id).findFirst();
                String react = opt.map(tl_messageUserReaction -> tl_messageUserReaction.reaction).orElse(null);
                cell.setData(u, react);
            }
        }

        public void update() {
            notifyDataSetChanged();
        }
    }

    private static class EndlessRecyclerViewScrollListener extends RecyclerView.OnScrollListener {
        private static final int VISIBLE_THRESHOLD = 5;
        private static final int STARTING_PAGE_INDEX = 0;

        private final int visibleThreshold;
        private int currentPage = 0;
        private int previousTotalItemCount = 0;
        private boolean loading = true;

        private final RecyclerView.LayoutManager layoutManager;
        private final LastVisibleItemPositionFinder lastVisibleItemPositionFinder;

        private final LoadMoreListener loadMoreListener;

        public interface LoadMoreListener {
            // Defines the process for actually loading more data based on page
            void onLoadMore(int page, int totalItemsCount, RecyclerView view);
        }

        private interface LastVisibleItemPositionFinder {
            int find();
        }

        @SuppressWarnings("unused")
        public EndlessRecyclerViewScrollListener(LinearLayoutManager layoutManager, LoadMoreListener loadMoreListener) {
            this.layoutManager = layoutManager;
            this.visibleThreshold = VISIBLE_THRESHOLD;
            this.lastVisibleItemPositionFinder = layoutManager::findLastVisibleItemPosition;
            this.loadMoreListener = loadMoreListener;
        }

        @Override
        public void onScrolled(RecyclerView view, int dx, int dy) {
            final int lastVisibleItemPosition = lastVisibleItemPositionFinder.find();
            final int totalItemCount = layoutManager.getItemCount();

            if (totalItemCount < previousTotalItemCount) {
                this.currentPage = STARTING_PAGE_INDEX;
                this.previousTotalItemCount = totalItemCount;

                if (totalItemCount == 0) {
                    this.loading = true;
                }
            }

            if (loading && (totalItemCount > previousTotalItemCount)) {
                loading = false;
                previousTotalItemCount = totalItemCount;
            }

            if (!loading && (lastVisibleItemPosition + visibleThreshold) > totalItemCount) {
                currentPage++;
                loadMoreListener.onLoadMore(currentPage, totalItemCount, view);
                loading = true;
            }
        }

        private int getLastVisibleItem(int[] lastVisibleItemPositions) {
            int maxSize = 0;

            for (int i = 0; i < lastVisibleItemPositions.length; i++) {
                if (i == 0 || lastVisibleItemPositions[i] > maxSize) {
                    maxSize = lastVisibleItemPositions[i];
                }
            }

            return maxSize;
        }

        @SuppressWarnings("unused")
        public void resetState() {
            this.currentPage = STARTING_PAGE_INDEX;
            this.previousTotalItemCount = 0;
            this.loading = true;
        }

    }
}
