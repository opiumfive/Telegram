package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SendAsAlert2 {

    //private Drawable shadowDrawable;

    private ActionBarPopupWindow sendAsPopupWindow;
    private RecyclerListView listView;
    private ViewGroup containerView;
    private HeaderCell messageTextView;
    private ShadowCell dividerCell;

    private ArrayList<TLRPC.Peer> chats;

    private boolean ignoreLayout;

    private int scrollOffsetY;
    private int scrollY;

    private int[] location = new int[2];

    private TLRPC.Peer selectedPeer;
    private TLRPC.Peer currentPeer;

    private SendAsAlertDelegate delegate;
    private int currentAccount;



    private static ArrayList<TLRPC.Peer> cachedChats;
    private static long lastCacheTime;
    private static long lastCacheDid;
    private static int lastCachedAccount;

    public static void resetCache() {
        cachedChats = null;
    }

    public static void processDeletedChat(int account, long did) {
        if (lastCachedAccount != account || cachedChats == null || did > 0) {
            return;
        }
        for (int a = 0, N = cachedChats.size(); a < N; a++) {
            if (MessageObject.getPeerId(cachedChats.get(a)) == did) {
                cachedChats.remove(a);
                break;
            }
        }
        if (cachedChats.isEmpty()) {
            cachedChats = null;
        }
    }

    public interface SendAsAlertDelegate {
        void didSelectSendAs(ActionBarPopupWindow popup, TLRPC.InputPeer inputPeer, TLRPC.Peer peer, View fromView, int popupHeight);
    }

    public static void checkFewUsers(Context context, long did, AccountInstance accountInstance, MessagesStorage.BooleanCallback callback) {
        if (lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 60 * 1000) {
            callback.run(cachedChats.size() == 1);
            return;
        }
        final AlertDialog progressDialog = new AlertDialog(context, 3);
        TLRPC.TL_channels_getSendAs req = new TLRPC.TL_channels_getSendAs();
        req.peer = accountInstance.getMessagesController().getInputPeer(did);
        int reqId = accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (response != null) {
                TLRPC.TL_channels_sendAsPeers res = (TLRPC.TL_channels_sendAsPeers) response;
                cachedChats = res.peers;
                lastCacheDid = did;
                lastCacheTime = SystemClock.elapsedRealtime();
                lastCachedAccount = accountInstance.getCurrentAccount();
                accountInstance.getMessagesController().putChats(res.chats, false);
                accountInstance.getMessagesController().putUsers(res.users, false);
                callback.run(res.peers.size() == 1);
            }
        }));
        progressDialog.setOnCancelListener(dialog -> accountInstance.getConnectionsManager().cancelRequest(reqId, true));
        try {
            progressDialog.showDelayed(500);
        } catch (Exception ignore) {

        }
    }

    public static void loadInit(Context context, long did, AccountInstance accountInstance, boolean forceUpdatePeers, MessagesStorage.BooleanCallback callback) {
        if (context == null || callback == null) return;
        if (!forceUpdatePeers && lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 60 * 1000) {
            callback.run(cachedChats.size() > 1);
        } else {
            TLRPC.TL_channels_getSendAs req = new TLRPC.TL_channels_getSendAs();
            req.peer = accountInstance.getMessagesController().getInputPeer(did);
            int reqId = accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    TLRPC.TL_channels_sendAsPeers res = (TLRPC.TL_channels_sendAsPeers) response;
                    cachedChats = res.peers;
                    lastCacheDid = did;
                    lastCacheTime = SystemClock.elapsedRealtime();
                    lastCachedAccount = accountInstance.getCurrentAccount();
                    accountInstance.getMessagesController().putChats(res.chats, false);
                    accountInstance.getMessagesController().putUsers(res.users, false);
                    callback.run(res.peers.size() > 1);
                }
            }));
        }
    }

    public static void open(View parent, long did, AccountInstance accountInstance, TLRPC.Peer defaultPeer, SendAsAlertDelegate delegate) {
        if (parent == null || delegate == null) {
            return;
        }
        if (lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 60 * 1000) {
            showAlert(parent, did, cachedChats, accountInstance.getCurrentAccount(), defaultPeer, delegate);
        } else {
            final AlertDialog progressDialog = new AlertDialog(parent.getContext(), 3);
            TLRPC.TL_channels_getSendAs req = new TLRPC.TL_channels_getSendAs();
            req.peer = accountInstance.getMessagesController().getInputPeer(did);
            int reqId = accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (response != null) {
                    TLRPC.TL_channels_sendAsPeers res = (TLRPC.TL_channels_sendAsPeers) response;
                    if (res.peers.size() == 1) {
                        TLRPC.InputPeer peer = accountInstance.getMessagesController().getInputPeer(MessageObject.getPeerId(res.peers.get(0)));
                        delegate.didSelectSendAs(null, peer, res.peers.get(0), null, 0);
                        return;
                    }
                    cachedChats = res.peers;
                    lastCacheDid = did;
                    lastCacheTime = SystemClock.elapsedRealtime();
                    lastCachedAccount = accountInstance.getCurrentAccount();
                    accountInstance.getMessagesController().putChats(res.chats, false);
                    accountInstance.getMessagesController().putUsers(res.users, false);
                    showAlert(parent, did, res.peers, accountInstance.getCurrentAccount(), defaultPeer, delegate);
                }
            }));
            progressDialog.setOnCancelListener(dialog -> accountInstance.getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.showDelayed(500);
            } catch (Exception ignore) {

            }
        }
    }

    private static void showAlert(View parent, long dialogId, ArrayList<TLRPC.Peer> peers, int account, TLRPC.Peer defaultPeer, SendAsAlertDelegate delegate) {
        SendAsAlert2 alert = new SendAsAlert2(parent, dialogId, peers, account, defaultPeer, delegate);
        alert.show(parent);
    }

    private void show(View parent) {
        int y = AndroidUtilities.getRealScreenSize().y - (int)parent.getY();
        sendAsPopupWindow.showAtLocation(parent, Gravity.LEFT | Gravity.BOTTOM, 0, y);
    }

    private SendAsAlert2(View parent, long dialogId, ArrayList<TLRPC.Peer> arrayList, int currentAcc, TLRPC.Peer defaultPeer, SendAsAlertDelegate delegate) {
        chats = new ArrayList<>(arrayList);
        this.delegate = delegate;
        currentAccount = currentAcc;

        selectedPeer = defaultPeer;

        ViewGroup internalLayout;

        AtomicBoolean justSelected = new AtomicBoolean(false);

        containerView = new FrameLayout(parent.getContext()) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = AndroidUtilities.dp(350);
                measureChildWithMargins(messageTextView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int contentSize = chats.size() * AndroidUtilities.dp(58);
                if (contentSize > height) {
                    contentSize = height;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(contentSize + AndroidUtilities.dp(65), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
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

        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);

        listView = new RecyclerListView(parent.getContext()) {
            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new ListAdapter(parent.getContext()));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setSelectorDrawableColor(0);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            final float dyThreshold = 30f;
            int totalScrolled = 0;

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
                float alpha = 0f;
                totalScrolled += dy;
                if (Math.abs(totalScrolled) < dyThreshold) {
                    alpha = Math.abs(totalScrolled) / dyThreshold;
                } else {
                    alpha = 1f;
                }
                dividerCell.setAlpha(alpha);
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (chats.get(position) == selectedPeer || justSelected.get()) {
                return;
            }
            justSelected.set(true);
            selectedPeer = chats.get(position);

            ((GroupCreateUserCell) view).setChecked(true, true);

            for (int a = 0, N = listView.getChildCount(); a < N; a++) {
                View child = listView.getChildAt(a);
                if (child != view) {
                    ((GroupCreateUserCell) child).setChecked(false, true);
                }
            }
            TLRPC.InputPeer peer = AccountInstance.getInstance(currentAcc).getMessagesController().getInputPeer(MessageObject.getPeerId(chats.get(position)));

            listView.postDelayed(((GroupCreateUserCell) view)::hidePhoto, 170);
            listView.postDelayed(() -> delegate.didSelectSendAs(sendAsPopupWindow, peer, chats.get(position), view, containerView.getHeight()), 150);
        });

        internalLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 45, 0, 0));

        messageTextView = new HeaderCell(parent.getContext());
        dividerCell = new ShadowCell(parent.getContext());

        //messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        boolean hasGroup = false;
        for (int a = 0, N = chats.size(); a < N; a++) {
            long peerId = MessageObject.getPeerId(chats.get(a));
            if (peerId < 0) {
                TLRPC.Chat peerChat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                if (!ChatObject.isChannel(peerChat) || peerChat.megagroup) {
                    hasGroup = true;
                    break;
                }
            }
        }
        messageTextView.setText(LocaleController.getString("SendMessageAs", R.string.SendMessageAs));
        dividerCell.setAlpha(0f);
        internalLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, -5, -5, 0, 0));
        internalLayout.addView(dividerCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 45, 0, 0));

        LinearLayout sendAsPopupContainerLayout = new LinearLayout(parent.getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && sendAsPopupWindow != null && sendAsPopupWindow.isShowing()) {
                    sendAsPopupWindow.dismiss(false);
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
                    if (sendAsPopupWindow != null && sendAsPopupWindow.isShowing()) {
                        View contentView = sendAsPopupWindow.getContentView();
                        contentView.getLocationInWindow(pos);
                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            sendAsPopupWindow.dismiss(false);
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (sendAsPopupWindow != null && sendAsPopupWindow.isShowing()) {
                        sendAsPopupWindow.dismiss(false);
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

        sendAsPopupWindow = new ActionBarPopupWindow(sendAsPopupContainerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss(false);
            }
        };
        sendAsPopupWindow.setPauseNotifications(true);
        sendAsPopupWindow.setDismissAnimationDuration(300);
        sendAsPopupWindow.setOutsideTouchable(true);
        sendAsPopupWindow.setClippingEnabled(true);
        sendAsPopupWindow.setFocusable(true);
        sendAsPopupWindow.setAnimationEnabled(true);
        sendAsPopupWindow.setAnimationStyle(R.style.PopupContextAnimationFromBottom);
        sendAsPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        sendAsPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        sendAsPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        sendAsPopupWindow.getContentView().setFocusableInTouchMode(true);

        sendAsPopupWindow.setOnDismissListener(() -> {
            delegate.didSelectSendAs(null, null, null, null, 0);
        });
    }

    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.dp(9);
        scrollY = child.getTop();
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (scrollOffsetY != newOffset) {
            //textView.setTranslationY(top + AndroidUtilities.dp(19));
            messageTextView.setTranslationY(top + AndroidUtilities.dp(56));
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            containerView.invalidate();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return chats.size();
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
            View view = new GroupCreateUserCell(context, 2, 0, false, false);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            long did = MessageObject.getPeerId(selectedPeer);

            GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
            Object object = cell.getObject();
            long id = 0;
            if (object != null) {
                if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else {
                    id = ((TLRPC.User) object).id;
                }
            }
            cell.setChecked(did == id, false);

        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            long did = MessageObject.getPeerId(chats.get(position));
            TLObject object;
            String status;
            if (did > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(did);
                status = LocaleController.getString("VoipGroupPersonalAccount", R.string.VoipGroupPersonalAccount);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-did);
                status = null;
            }

            GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
            cell.setObject(object, null, status, false);
        }
    }
}
