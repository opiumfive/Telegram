package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextPaint;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BluredView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DeleteHistoryCalendarActivity extends BaseFragment {

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextView bottomOverlayChatText;

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;
    private BluredView blurredView;

    int startFromYear;
    int startFromMonth;
    int monthCount;

    CalendarAdapter adapter;

    SparseArray<SparseArray<PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;
    boolean isInSelectMode = false;
    int minDate, maxDate = 0;

    public DeleteHistoryCalendarActivity(Bundle args, int selectedDate) {
        super(args);

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        contentView = new FrameLayout(context);
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, AndroidUtilities.dp(20)));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, AndroidUtilities.dp(20)));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (isInSelectMode) {
                        if (minDate == 0) {
                            bottomOverlayChatText.setAlpha(1.0f);
                            bottomOverlayChatText.setText(LocaleController.getString("SelectDays", R.string.SelectDays).toUpperCase());
                            actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
                            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
                            bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                            isInSelectMode = false;
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage("You have range selected, are you sure to skip it?");
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                                bottomOverlayChatText.setAlpha(1.0f);
                                bottomOverlayChatText.setText(LocaleController.getString("SelectDays", R.string.SelectDays).toUpperCase());
                                minDate = 0;
                                maxDate = 0;
                                actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
                                actionBar.setBackButtonImage(R.drawable.ic_ab_back);
                                bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                                isInSelectMode = false;
                                redraw();
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            builder.show();
                        }
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        fragmentView = contentView;

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }


        loadNext();
        updateColors();
        activeTextPaint.setColor(Color.WHITE);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);

        bottomOverlayChatText = new TextView(context);
        bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayChatText.setText(LocaleController.getString("SelectDays", R.string.SelectDays).toUpperCase());
        bottomOverlayChatText.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            bottomOverlayChatText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        bottomOverlayChatText.setBackgroundResource(outValue.resourceId);
        contentView.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(20), Gravity.CENTER | Gravity.BOTTOM));
        bottomOverlayChatText.setOnClickListener((v) -> {
            if (isInSelectMode) {
                if (bottomOverlayChatText.getAlpha() == 0.5f) {
                    Toast.makeText(context, "No range selected", Toast.LENGTH_SHORT).show();
                } else {
                    TLRPC.Chat currentChat = null;
                    TLRPC.User currentUser = null;
                    TLRPC.EncryptedChat currentEncryptedChat = null;
                    if (DialogObject.isEncryptedDialog(dialogId)) {
                        currentEncryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                    } else if (DialogObject.isUserDialog(dialogId)) {
                        currentUser = getMessagesController().getUser(dialogId);
                    } else {
                        currentChat = getMessagesController().getChat(-dialogId);
                    }

                    int[] dateRange = new int[] { minDate, maxDate };

                    long mergeDialogId = 0;

                    ChatActivity chatActivity = getChatActivity();

                    if (chatActivity == null) {
                        TLRPC.ChatFull chatInfo = getMessagesController().getChatFull(-dialogId);
                        if (chatInfo != null) {
                            mergeDialogId = -chatInfo.migrated_from_chat_id;
                        }
                    } else {
                        mergeDialogId = chatActivity.getMergeDialogId();
                    }

                    AlertsCreator.createDeleteMessagesAlert(this, currentUser, currentChat, currentEncryptedChat, null, mergeDialogId, null, null, null, false, 1, dateRange, () -> {

                        Calendar cal1 = Calendar.getInstance();

                        long loop = minDate * 1000L;
                        while (loop < maxDate * 1000L) {
                            cal1.setTimeInMillis(loop);
                            int month = cal1.get(Calendar.YEAR) * 100 + cal1.get(Calendar.MONTH);
                            int day = cal1.get(Calendar.DAY_OF_MONTH) - 1;
                            try {
                                messagesByYearMounth.get(month).delete(day);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            loop += 86400000;
                        }

                        bottomOverlayChatText.setAlpha(1.0f);
                        bottomOverlayChatText.setText(LocaleController.getString("SelectDays", R.string.SelectDays).toUpperCase());
                        minDate = 0;
                        maxDate = 0;
                        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
                        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
                        bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                        isInSelectMode = false;
                        redraw();
                    }, null);
                }
            } else {
                bottomOverlayChatText.setText(LocaleController.getString("ClearHistory", R.string.ClearHistory).toUpperCase());
                bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));
                bottomOverlayChatText.setAlpha(0.5f);
                actionBar.setTitle(LocaleController.getString("SelectDays", R.string.SelectDays));
                actionBar.setBackButtonImage(R.drawable.ic_close_white);
            }
            isInSelectMode = !isInSelectMode;
        });

        DividerCell divider = new DividerCell(context);
        //divider.setBackgroundColor(0xff3b3b3b);
        contentView.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, AndroidUtilities.dp(17)));

        return fragmentView;
    }

    private void redraw() {
        for (int a = 0, N = listView.getChildCount(); a < N; a++) {
            View child = listView.getChildAt(a);
            child.invalidate();
        }
        if (isInSelectMode) {
            if (minDate != 0) {
                int days = (maxDate - minDate + 1) / 86400;
                actionBar.setTitle(LocaleController.formatPluralString("Days", days));

                bottomOverlayChatText.setAlpha(1.0f);
            } else {
                bottomOverlayChatText.setAlpha(0.5f);
            }
        } else {
            bottomOverlayChatText.setAlpha(1.0f);
        }
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        req.filter = new TLRPC.TL_inputMessagesFilterPhotos();

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }

                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        };
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    ChatActivity getChatActivity() {
        if (getParentActivity() instanceof LaunchActivity) {
            ActionBarLayout actionBarLayout = ((LaunchActivity) getParentActivity()).getActionBarLayout();
            if (actionBarLayout != null) {
                List<BaseFragment> stack = actionBarLayout.fragmentsStack;
                for(BaseFragment f: stack) {
                    if (f instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) f;
                        return chatActivity;
                    }
                }
            }
        }
        return null;
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

        SparseArray<PeriodDay> animatedFromMessagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> animatedFromImagesByDays = new SparseArray<>();

        boolean attached;
        float animationProgress = 1f;

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime= (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        boolean pressed;
        float pressedX;
        float pressedY;
        Handler hander = new Handler();
        boolean scheduledLongPress = false;
        Runnable showPreviewRunnable = new Runnable() {
            @Override
            public void run() {
                scheduledLongPress = false;
                boolean found = false;
                if (isInSelectMode) return;

                int date = getDateByTouch(pressedX, pressedY);

                if (date != 0) {
                    TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
                    req.peer = getAccountInstance().getMessagesController().getInputPeer(dialogId);
                    req.add_offset = -30;
                    req.limit = 30;
                    req.offset_id = 0;
                    req.offset_date = date;
                    int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (response != null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;

                                if (res.messages != null && !res.messages.isEmpty()) {

                                    int mid = 0;
                                    TLRPC.Message m = res.messages.get(res.messages.size() - 1);

                                    Calendar cal2 = Calendar.getInstance();
                                    cal2.setTimeInMillis(date * 1000);
                                    int minDate = res.messages.get(res.messages.size() - 1).date;
                                    mid = res.messages.get(res.messages.size() - 1).id;
                                    for (int a = res.messages.size() - 1; a >= 0; a--) {
                                        TLRPC.Message message = res.messages.get(a);
                                        Calendar cal1 = Calendar.getInstance();
                                        cal1.setTimeInMillis(res.messages.get(0).date * 1000);
                                        boolean sameDay = cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
                                        if (message.date > date && sameDay && message.date <= minDate) {
                                            m = message;
                                            mid = message.id;
                                            minDate = message.date;
                                        }
                                    }

                                    MessageObject messageObject = new MessageObject(currentAccount, m, false, false);
                                    Calendar cal1 = Calendar.getInstance();
                                    cal1.setTimeInMillis(m.date * 1000);
                                    boolean sameDay = cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
                                    if (sameDay) {
                                        showChatPreview(messageObject.getId(), date);
                                    } else {
                                        tryFindMediaMessageOrShowNotFound(date);
                                    }
                                }

                            } else {
                                tryFindMediaMessageOrShowNotFound(date);
                            }
                        });
                    });
                }
            }
        };

        private void tryFindMediaMessageOrShowNotFound(int date) {
            boolean found = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    if (imagesByDays.valueAt(i).getDrawRegion().contains(pressedX, pressedY)) {
                        found = true;
                        try {
                            PeriodDay periodDay = messagesByDays.valueAt(i);
                            showChatPreview(periodDay.messageObject.getId(), periodDay.startOffset);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
            if (!found) {
                Toast.makeText(getContext(), "There are no messages on " + LocaleController.formatDate(date), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                pressedX = event.getX();
                pressedY = event.getY();
                if (!scheduledLongPress) {
                    hander.postDelayed(showPreviewRunnable, 500);
                    scheduledLongPress = true;
                }


            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                hander.removeCallbacks(showPreviewRunnable);
                if (pressed) {

                }
                if (scheduledLongPress) {
                    scheduledLongPress = false;
                    int date = getDateByTouch(pressedX, pressedY);

                    if (date != 0 && isInSelectMode) {
                        if (minDate == 0) {
                            minDate = date;
                            maxDate = date + 86400 - 1;
                        } else {
                            if (maxDate - minDate <= 86400) {
                                if (date < minDate) {
                                    maxDate = minDate + 86400 - 1;
                                    minDate = date;
                                } else {
                                    minDate = maxDate - 86400 + 1;
                                    Calendar min = Calendar.getInstance();
                                    min.setTimeInMillis(date * 1000L);
                                    min.set(Calendar.HOUR_OF_DAY, 23);
                                    min.set(Calendar.MINUTE, 59);
                                    min.set(Calendar.SECOND, 59);
                                    maxDate = (int) (min.getTimeInMillis() / 1000L);
                                }
                            } else {
                                minDate = date;
                                maxDate = date + 86400 - 1;
                            }
                        }
                        redraw();
                    }
                }

                pressed = false;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                hander.removeCallbacks(showPreviewRunnable);
                scheduledLongPress = false;
                pressed = false;
            }
            return pressed;
        }

        private int getDateByTouch(float x, float y) {
            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);

                if (Math.abs(cx - x) <= xStep / 2f && Math.abs(cy - y) <= yStep / 2f) {
                    int day = i + 1;

                    Calendar calendar = Calendar.getInstance();
                    calendar.set(currentYear, currentMonthInYear, day, 0, 0, 0);
                    if (nowTime >= startMonthTime + (i + 1) * 86400) {
                        return (int) (calendar.getTimeInMillis() / 1000L);
                    }
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
            return 0;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);

                Calendar cal1 = Calendar.getInstance();
                cal1.set(currentYear, currentMonthInYear, i + 1, 12, 0, 0);
                int curr = (int) (cal1.getTimeInMillis() / 1000L);

                boolean isBetween = false;

                if (isInSelectMode && curr > minDate && curr < maxDate) {
                    isBetween = true;
                    Calendar cal2 = Calendar.getInstance();
                    cal2.setTimeInMillis(minDate * 1000L);
                    boolean isStartDay = cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
                    cal2.setTimeInMillis(maxDate * 1000L);
                    boolean isEndDay = cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
                    boolean isStartWeek = currentColumn == 0 || i == 0;
                    boolean isEndWeek = currentColumn == 6 || i == daysInMonth - 1;
                    boolean isSingleDayRange = maxDate - minDate <= 86400;

                    if (isStartDay) {
                        if (!isSingleDayRange && !isEndWeek) {
                            selectedBgPaint.setColor(0xFFe3f0fb);
                            selectedBgPaint.setStyle(Paint.Style.FILL);
                            canvas.drawRect(
                                    xStep * currentColumn + xStep / 2,
                                    yStep * currentCell + AndroidUtilities.dp(48),
                                    xStep * currentColumn + xStep + 1,
                                    yStep * currentCell + yStep + AndroidUtilities.dp(40),
                                    selectedBgPaint
                            );
                        }
                        selectedBgPaint.setColor(0xFF4fa6e7);
                        canvas.drawCircle(xStep * currentColumn + xStep / 2, yStep * currentCell + yStep / 2 + AndroidUtilities.dp(44) - 0.5f, yStep / 2 - AndroidUtilities.dp(4), selectedBgPaint);
                        selectedBgPaint.setColor(0xFFffffff);
                        canvas.drawCircle(xStep * currentColumn + xStep / 2, yStep * currentCell + yStep / 2 + AndroidUtilities.dp(44) - 0.5f, yStep / 2 - AndroidUtilities.dp(7), selectedBgPaint);
                        selectedBgPaint.setColor(0xFF4fa6e7);
                        canvas.drawCircle(xStep * currentColumn + xStep / 2, yStep * currentCell + yStep / 2 + AndroidUtilities.dp(44) - 0.5f, yStep / 2 - AndroidUtilities.dp(9), selectedBgPaint);
                    } else if (isEndDay && !isSingleDayRange) {
                        if (!isStartWeek) {
                            selectedBgPaint.setColor(0xFFe3f0fb);
                            selectedBgPaint.setStyle(Paint.Style.FILL);
                            canvas.drawRect(
                                    xStep * currentColumn,
                                    yStep * currentCell + AndroidUtilities.dp(48),
                                    xStep * currentColumn + xStep / 2,
                                    yStep * currentCell + yStep + AndroidUtilities.dp(40),
                                    selectedBgPaint
                            );
                        }
                        selectedBgPaint.setColor(0xFF4fa6e7);
                        canvas.drawCircle(xStep * currentColumn + xStep / 2, yStep * currentCell + yStep / 2 + AndroidUtilities.dp(44) - 0.5f, yStep / 2 - AndroidUtilities.dp(4), selectedBgPaint);
                        selectedBgPaint.setColor(0xFFffffff);
                        canvas.drawCircle(xStep * currentColumn + xStep / 2, yStep * currentCell + yStep / 2 + AndroidUtilities.dp(44) - 0.5f, yStep / 2 - AndroidUtilities.dp(7), selectedBgPaint);
                        selectedBgPaint.setColor(0xFF4fa6e7);
                        canvas.drawCircle(xStep * currentColumn + xStep / 2, yStep * currentCell + yStep / 2 + AndroidUtilities.dp(44) - 0.5f, yStep / 2 - AndroidUtilities.dp(9), selectedBgPaint);
                    } else if (isStartWeek) {
                        selectedBgPaint.setColor(0xFFe3f0fb);
                        selectedBgPaint.setStyle(Paint.Style.FILL);
                        canvas.drawRect(
                                xStep * currentColumn + xStep / 2,
                                yStep * currentCell + AndroidUtilities.dp(48),
                                xStep * currentColumn + xStep + 1,
                                yStep * currentCell + yStep + AndroidUtilities.dp(40),
                                selectedBgPaint
                        );
                        canvas.drawCircle(xStep * currentColumn + xStep / 2, yStep * currentCell + yStep / 2 + AndroidUtilities.dp(44) - 0.5f, yStep / 2 - AndroidUtilities.dp(4), selectedBgPaint);
                    } else if (isEndWeek) {
                        selectedBgPaint.setColor(0xFFe3f0fb);
                        selectedBgPaint.setStyle(Paint.Style.FILL);
                        canvas.drawRect(
                                xStep * currentColumn,
                                yStep * currentCell + AndroidUtilities.dp(48),
                                xStep * currentColumn + xStep / 2,
                                yStep * currentCell + yStep + AndroidUtilities.dp(40),
                                selectedBgPaint
                        );
                        canvas.drawCircle(xStep * currentColumn + xStep / 2, yStep * currentCell + yStep / 2 + AndroidUtilities.dp(44) - 0.5f, yStep / 2 - AndroidUtilities.dp(4), selectedBgPaint);
                    } else {
                        selectedBgPaint.setColor(0xFFe3f0fb);
                        selectedBgPaint.setStyle(Paint.Style.FILL);
                        canvas.drawRect(
                                xStep * currentColumn,
                                yStep * currentCell + AndroidUtilities.dp(48),
                                xStep * currentColumn + xStep + 1,
                                yStep * currentCell + yStep + AndroidUtilities.dp(40),
                                selectedBgPaint
                        );
                    }
                }

                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !messagesByDays.get(i).wasDrawn) {
                            messagesByDays.get(i).enterAlpha = 0f;
                            messagesByDays.get(i).startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (messagesByDays.get(i).startEnterDelay > 0) {
                            messagesByDays.get(i).startEnterDelay -= 16;
                            if (messagesByDays.get(i).startEnterDelay < 0) {
                                messagesByDays.get(i).startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                            messagesByDays.get(i).enterAlpha += 16 / 220f;
                            if (messagesByDays.get(i).enterAlpha > 1f) {
                                messagesByDays.get(i).enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = messagesByDays.get(i).enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s,cx, cy);
                        }
                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        imagesByDays.get(i).setImageCoords(cx - AndroidUtilities.dp(isBetween? 34:44) / 2f, cy - AndroidUtilities.dp(isBetween ? 34:44) / 2f, AndroidUtilities.dp(isBetween? 34:44), AndroidUtilities.dp(isBetween? 34:44));
                        imagesByDays.get(i).draw(canvas);
                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(isBetween? 34:44) / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
                        }
                    }
                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }

                } else {
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            hander.removeCallbacks(showPreviewRunnable);
            scheduledLongPress = false;
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                updateColors();
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);


        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }

    private boolean showChatPreview(int message_id, int startOffset) {
        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        } else {
            if (DialogObject.isUserDialog(dialogId)) {
                args.putLong("user_id", dialogId);
            } else {
                long did = dialogId;
                if (message_id != 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-did);
                    if (chat != null && chat.migrated_to != null) {
                        args.putLong("migrated_to", did);
                        did = -chat.migrated_to.channel_id;
                    }
                }
                args.putLong("chat_id", -did);
            }
        }
        if (message_id != 0) {
            args.putInt("message_id", message_id);
        }

        if (getMessagesController().checkCanOpenChat(args, DeleteHistoryCalendarActivity.this)) {
            //checkShowBlur();
            presentFragmentAsPreview(new ChatActivity(args));

        }

        return true;
    }

    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        int w = (int) (fragmentView.getMeasuredWidth() / 6.0f);
        int h = (int) (fragmentView.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        fragmentView.draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
    }

    private void checkShowBlur() {
        boolean show = (parentLayout != null && parentLayout.isInPreviewMode() && !inPreviewMode);

        if (show && (blurredView == null || blurredView.getTag() == null)) {

            if (blurredView == null) {
                blurredView = new BluredView(fragmentView.getContext(), fragmentView, null) {
                    @Override
                    public void setAlpha(float alpha) {
                        super.setAlpha(alpha);
                        fragmentView.invalidate();
                    }

                    @Override
                    public void setVisibility(int visibility) {
                        super.setVisibility(visibility);
                        fragmentView.invalidate();
                    }
                };
                contentView.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            } else {
                int idx = contentView.indexOfChild(blurredView);
                if (idx != contentView.getChildCount() - 1) {
                    contentView.removeView(blurredView);
                    contentView.addView(blurredView);
                }
                blurredView.update();
                blurredView.setVisibility(View.VISIBLE);
            }

            blurredView.setAlpha(0.0f);
            blurredView.animate().setListener(null).cancel();
            blurredView.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    fragmentView.invalidate();
                }
            }).start();

            blurredView.setTag(1);
        } else if (!show && blurredView != null && blurredView.getTag() != null) {
            blurredView.animate().setListener(null).cancel();
            blurredView.animate().setListener(new HideViewAfterAnimation(blurredView)).alpha(0).start();
            blurredView.setTag(null);
            fragmentView.invalidate();
        }
    }
}

