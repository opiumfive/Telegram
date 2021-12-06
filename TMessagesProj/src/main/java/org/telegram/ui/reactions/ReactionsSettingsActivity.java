package org.telegram.ui.reactions;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Canvas;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ReactionsSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private long chatId = 0;
    private TLRPC.Chat currentChat = null;
    private TLRPC.ChatFull info = null;
    private ScrollView scrollView;

    private TextCheckCell approveCell;
    private LinearLayout hidebleLL;
    private boolean changedSomething = false;
    private ArrayList<TLRPC.TL_availableReaction> reactions;
    private ArrayList<String> enabledReactions;
    private ArrayList<TextImageCheckCell> checkCells = new ArrayList<>();

    public ReactionsSettingsActivity(Bundle args) {
        super(args);
        chatId = arguments.getLong("chat_id");

        currentChat = getMessagesController().getChat(chatId);
        if (currentChat != null && currentChat.default_banned_rights != null) {

        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        if (info != null) {
            //TODO update list
        }
    }

    @Override
    public boolean onFragmentCreate() {
        if (currentChat == null) {
            currentChat = getMessagesController().getChat(chatId);
        }
        if (currentChat == null) {
            currentChat = MessagesStorage.getInstance(currentAccount).getChatSync(chatId);
            if (currentChat != null) {
                getMessagesController().putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                info = MessagesStorage.getInstance(currentAccount).loadChatInfo(chatId, ChatObject.isChannel(currentChat), new CountDownLatch(1), false, false);
                if (info == null) {
                    return false;
                }
            }
        }

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                boolean infoWasEmpty = info == null;
                info = chatFull;
                updateFields();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_CHAT) != 0) {
                updateFields();
            }
        }
    }

    private void updateFields() {
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setTitle(LocaleController.getString("Reactions", R.string.Reactions));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    ArrayList<String> arrayList = getChosenReactions();
                    changedSomething = !arrayList.equals(enabledReactions);
                    if (!changedSomething) {
                        finishFragment();
                    } else {
                        onDone();
                    }
                }
            }
        });

        scrollView = new ScrollView(context);
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {

            int oldKeyboardHeight;

            @Override
            protected AdjustPanLayoutHelper createAdjustPanLayoutHelper() {
                AdjustPanLayoutHelper panLayoutHelper = new AdjustPanLayoutHelper(this) {

                    @Override
                    protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                        super.onTransitionStart(keyboardVisible, contentHeight);
                        scrollView.getLayoutParams().height = contentHeight;
                    }

                    @Override
                    protected void onTransitionEnd() {
                        super.onTransitionEnd();
                        scrollView.getLayoutParams().height = LinearLayout.LayoutParams.MATCH_PARENT;
                        scrollView.requestLayout();
                    }

                    @Override
                    protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                        super.onPanTranslationUpdate(y, progress, keyboardVisible);
                        setTranslationY(0);
                    }

                    @Override
                    protected boolean heightAnimationEnabled() {
                        return true;
                    }
                };
                panLayoutHelper.setCheckHierarchyHeight(true);
                return panLayoutHelper;
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                adjustPanLayoutHelper.onAttach();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                adjustPanLayoutHelper.onDetach();
            }
        };

        fragmentView = contentView;

        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView.setTag(Theme.key_windowBackgroundGray);

        LinearLayout linearLayout = new LinearLayout(context);
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(100);
        linearLayout.setLayoutTransition(transition);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout);

        reactions = MediaDataController.getInstance(UserConfig.selectedAccount).getAvailableReactions();
        enabledReactions = info.available_reactions;

        approveCell = new TextCheckCell(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(0, 0, getWidth(), getHeight());
                super.onDraw(canvas);
                canvas.restore();
            }
        };
        approveCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundUnchecked));
        approveCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        approveCell.setDrawCheckRipple(true);
        approveCell.setHeight(56);
        approveCell.setTag(Theme.key_windowBackgroundUnchecked);
        approveCell.setTextAndCheck(LocaleController.getString("EnableReactions", R.string.EnableReactions), enabledReactions != null && !enabledReactions.isEmpty(), false);
        approveCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        approveCell.setOnClickListener(view -> {
            TextCheckCell cell = (TextCheckCell) view;
            boolean newIsChecked = !cell.isChecked();
            cell.setBackgroundColorAnimated(newIsChecked, Theme.getColor(newIsChecked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
            cell.setChecked(newIsChecked);
            changeListVisibility(newIsChecked);
        });
        linearLayout.addView(approveCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
        contentView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        contentView.setClipChildren(false);
        scrollView.setClipChildren(false);
        linearLayout.setClipChildren(false);
        TextInfoPrivacyCell textInfoPrivacyCell = new TextInfoPrivacyCell(context);
        textInfoPrivacyCell.setText(ChatObject.isChannel(currentChat) ? LocaleController.getString("EnableReactionsHintChannel", R.string.EnableReactionsHintChannel) : LocaleController.getString("EnableReactionsHintGroup", R.string.EnableReactionsHintGroup));
        textInfoPrivacyCell.setBackground(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        linearLayout.addView(textInfoPrivacyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

        hidebleLL = new LinearLayout(context);
        hidebleLL.setVisibility(approveCell.isChecked() ? View.VISIBLE : View.GONE);
        LayoutTransition transition2 = new LayoutTransition();
        transition2.setDuration(100);
        hidebleLL.setLayoutTransition(transition2);
        hidebleLL.setOrientation(LinearLayout.VERTICAL);


        HeaderCell headerCell = new HeaderCell(context, 23);
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        headerCell.setText(LocaleController.getString("AvailableReactions", R.string.AvailableReactions));
        hidebleLL.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 60));

        View emptyCell = new View(context);
        emptyCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        hidebleLL.addView(emptyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 5));
        View.OnClickListener checkClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v instanceof TextImageCheckCell) {
                    TextImageCheckCell cell = (TextImageCheckCell) v;
                    cell.setChecked(!cell.isChecked());

                    if (getChosenReactions().isEmpty()) {
                        approveCell.setBackgroundColorAnimated(false, Theme.getColor(false ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                        approveCell.setChecked(false);
                        changeListVisibility(false);
                    }
                }
            }
        };
        checkCells.clear();
        for (int i = 0; i < reactions.size(); i++) {
            TLRPC.TL_availableReaction reaction = reactions.get(i);
            TextImageCheckCell textCheckCell = new TextImageCheckCell(context);
            textCheckCell.setTextAndImageAndCheck(reaction.reaction, ImageLocation.getForDocument(reaction.static_icon), Emoji.getEmojiDrawable(reaction.reaction), reaction.title, isReactionEnabled(reaction.reaction), i != reactions.size() - 1);
            textCheckCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            textCheckCell.setOnClickListener(checkClickListener);
            hidebleLL.addView(textCheckCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
            checkCells.add(textCheckCell);
        }

        linearLayout.addView(hidebleLL, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return contentView;
    }

    private ArrayList<String> getChosenReactions() {
        if (approveCell.isChecked()) {
            return checkCells.stream()
                    .filter(TextImageCheckCell::isChecked)
                    .map(TextImageCheckCell::getAlias)
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            return new ArrayList<>();
        }
    }

    private boolean isReactionEnabled(String reaction) {
        if (enabledReactions == null) return false;
        return enabledReactions.contains(reaction);
    }

    private void changeListVisibility(boolean newIsChecked) {
        if (newIsChecked) {
            for (TextImageCheckCell cell : checkCells) {
                cell.setChecked(true);
            }
        }
        hidebleLL.setVisibility(newIsChecked ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onBackPressed() {
        changedSomething = !getChosenReactions().equals(enabledReactions);
        if (changedSomething) {
            onDone();
            return false;
        }
        return true;
    }

    private boolean loading = false;
    private AlertDialog progressDialog;

    private void onDone() {

        if (loading) {
            return;
        }

        ArrayList<String> chosenReactions = getChosenReactions();

        if (progressDialog != null) {
            progressDialog.dismiss();
        }

        TLRPC.TL_messages_setChatAvailableReactions req = new TLRPC.TL_messages_setChatAvailableReactions();
        req.available_reactions = chosenReactions;
        req.peer = getMessagesController().getInputPeer(-chatId);

        loading = true;
        progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.showDelayed(500);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loading = false;
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            if (error == null) {
                if (response instanceof TLRPC.TL_updates) {
                    TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                    getMessagesController().processUpdates(updates, false);
                }
                info.available_reactions = chosenReactions;
                TLRPC.ChatFull full = getMessagesController().getChatFull(info.id);
                full.available_reactions = chosenReactions;
                getMessagesStorage().updateChatInfo(full, false);
                getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false);

            } else {
                //AlertsCreator.showSimpleAlert(ReactionsSettingsActivity.this, error.text);
            }
            finishFragment();
        }));
    }
}
