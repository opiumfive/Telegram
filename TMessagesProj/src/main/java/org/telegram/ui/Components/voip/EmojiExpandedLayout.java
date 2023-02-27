package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerCategoriesListView;

import java.io.File;
import java.util.ArrayList;

public class EmojiExpandedLayout extends FrameLayout {

    private String[] emoji = null;
    private TextView emojiRationalTextView;

    public EmojiExpandedLayout(Context context) {
        super(context);

        setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), 0x10000000));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setText(LocaleController.getString("CallEmojiKeyTooltipTitle", R.string.CallEmojiKeyTooltipTitle));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        emojiRationalTextView = new TextView(context);
        emojiRationalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emojiRationalTextView.setTextColor(Color.WHITE);
        emojiRationalTextView.setGravity(Gravity.CENTER);

        addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 60, 16, 10));
        addView(emojiRationalTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 86, 16, 16));
    }

    public void setData(String[] emoji, TLRPC.User user) {
        this.emoji = emoji;
        //StickerCategoriesListView.search.fetch(UserConfig.selectedAccount, emoji1, list -> {
        //    if (list != null) {
                //documentIds.addAll(list.document_id);
        //    }
            //next.run();
        //});
        emojiRationalTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("CallEmojiKeyTooltipBold", R.string.CallEmojiKeyTooltipBold, UserObject.getFirstName(user))));
    }
}
