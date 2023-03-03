package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPRateView extends FrameLayout {



    public VoIPRateView(Context context, VoIPRatingView.OnRatingChangeListener listener) {
        super(context);

        setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(24), 0x10000000));
        setClipChildren(false);
        setClipToPadding(false);

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setText(LocaleController.getString("CallRateCallTitle", R.string.CallRateCallTitle));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        TextView title2 = new TextView(context);
        title2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title2.setText(LocaleController.getString("CallRateCallSubtitle", R.string.CallRateCallSubtitle));
        title2.setTextColor(Color.WHITE);
        title2.setGravity(Gravity.CENTER);

        VoIPRatingView bar = new VoIPRatingView(context);

        addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 16, 16, 0));
        addView(title2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 42, 16, 0));
        addView(bar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 76, 8, 16));


        bar.setOnRatingChangeListener((newRating, x) -> {
            if (listener != null) listener.onRatingChanged(newRating, x);
        });
    }
}
