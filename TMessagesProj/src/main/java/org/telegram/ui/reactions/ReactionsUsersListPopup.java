package org.telegram.ui.reactions;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;

import java.util.concurrent.atomic.AtomicBoolean;

public class ReactionsUsersListPopup {

    private ActionBarPopupWindow reactionsPopupWindow;

    private ViewGroup containerView;

    private boolean ignoreLayout;

    public static void showAlert(View parent, ChatActivity chatActivity, MessageObject messageObject, String reaction) {

        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.peer = chatActivity.getMessagesController().getInputPeer(messageObject.getDialogId());
        req.id = messageObject.getId();
        req.limit = 50;
        req.reaction = reaction;
        req.offset = null;
        if (reaction != null) {
            req.flags |= 1;
        }
        chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_messageReactionsList) {
                TLRPC.TL_messages_messageReactionsList list = (TLRPC.TL_messages_messageReactionsList) response;
                ReactionsUsersListPopup alert = new ReactionsUsersListPopup(parent, chatActivity, messageObject, reaction, list);
                alert.show(parent);
            }
        }));
    }

    private void show(View parent) {
        int[] position = new int[2];
        parent.getLocationOnScreen(position);
        int y = AndroidUtilities.getRealScreenSize().y - (int)parent.getY();
        reactionsPopupWindow.showAtLocation(parent, Gravity.TOP | Gravity.LEFT, position[0], position[1] + parent.getHeight() / 2  );
    }

    private ReactionsUsersListPopup(View parent, ChatActivity chatActivity, MessageObject messageObject, String reaction, TLRPC.TL_messages_messageReactionsList list) {
        ViewGroup internalLayout;

        AtomicBoolean justSelected = new AtomicBoolean(false);

        containerView = new FrameLayout(parent.getContext()) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = AndroidUtilities.dp(300);
                int width = AndroidUtilities.dp(250);
                int contentSize = list.users.size() * AndroidUtilities.dp(48);
                if (list.users.size() == 1) contentSize += AndroidUtilities.dp(58);
                if (contentSize > height) {
                    contentSize = height;
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(contentSize, MeasureSpec.EXACTLY));
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };

        internalLayout = containerView;
        containerView.setWillNotDraw(false);


        ReactionUsersListView listView = new ReactionUsersListView(chatActivity, messageObject, reaction, list, () -> {
            if (reactionsPopupWindow != null && reactionsPopupWindow.isShowing()) reactionsPopupWindow.dismiss(false);
        }, null, false, null, list.users.size() == 1);

        internalLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        LinearLayout sendAsPopupContainerLayout = new LinearLayout(parent.getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && reactionsPopupWindow != null && reactionsPopupWindow.isShowing()) {
                    reactionsPopupWindow.dismiss(false);
                }
                return super.dispatchKeyEvent(event);
            }
        };

        Rect rect = new Rect();

        sendAsPopupContainerLayout.setOnTouchListener(new View.OnTouchListener() {

            private int[] pos = new int[2];

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (reactionsPopupWindow != null && reactionsPopupWindow.isShowing()) {
                        View contentView = reactionsPopupWindow.getContentView();
                        contentView.getLocationInWindow(pos);
                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            reactionsPopupWindow.dismiss(false);
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (reactionsPopupWindow != null && reactionsPopupWindow.isShowing()) {
                        reactionsPopupWindow.dismiss(false);
                    }
                }
                return false;
            }
        });
        sendAsPopupContainerLayout.setOrientation(LinearLayout.VERTICAL);

        Rect backgroundPaddings = new Rect();

        Drawable shadowDrawable2 = ContextCompat.getDrawable(parent.getContext(), R.drawable.popup_fixed_alert).mutate();
        shadowDrawable2.getPadding(backgroundPaddings);
        shadowDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        sendAsPopupContainerLayout.setBackground(shadowDrawable2);

        sendAsPopupContainerLayout.addView(containerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        reactionsPopupWindow = new ActionBarPopupWindow(sendAsPopupContainerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss(false);
            }
        };
        reactionsPopupWindow.setPauseNotifications(true);
        reactionsPopupWindow.setDismissAnimationDuration(300);
        reactionsPopupWindow.setOutsideTouchable(true);
        reactionsPopupWindow.setClippingEnabled(true);
        reactionsPopupWindow.setFocusable(true);
        reactionsPopupWindow.setAnimationEnabled(true);
        reactionsPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        sendAsPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        reactionsPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        reactionsPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        reactionsPopupWindow.getContentView().setFocusableInTouchMode(true);

        reactionsPopupWindow.setOnDismissListener(() -> {

        });
    }
}
