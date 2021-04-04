package org.telegram.ui.Components.MessageAnimations.Editor.popup;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class ColorPickerPopup extends FrameLayout {

    private TextView textView;
    private TextView textViewApply;
    private org.telegram.ui.Components.ColorPicker colorPicker;
    private PopupWindow popupWindow;
    private int color;
    private int curColor = 0;
    private ColorSelectedListener colorSelectedListener = null;

    public ColorPickerPopup(@NonNull Context context, int color) {
        super(context);

        this.color = color;
        curColor = color;
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setMinimumHeight(AndroidUtilities.dp(300));
        setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

        colorPicker = new org.telegram.ui.Components.ColorPicker(getContext(), false,
                (color1, num, applyNow) -> {
                    curColor = color1;
                });
        colorPicker.setType(1, false, false, false, false, 0, false);
        colorPicker.setColor(color, 0);
        addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(160), Gravity.CENTER_HORIZONTAL));

        textView = new TextView(context);
        textView.setText("     Cancel"); // TODO sorry, need to be padding
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        textView.setTextSize(16);
        textView.setGravity(Gravity.LEFT);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        textView.setOnClickListener((view -> {
            if (popupWindow != null) popupWindow.dismiss();
            colorSelectedListener = null;
        }));

        textViewApply = new TextView(context);
        textViewApply.setText("Apply     ");
        textViewApply.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        textViewApply.setTextSize(16);
        textViewApply.setGravity(Gravity.END);

        textViewApply.setOnClickListener(view -> {
            colorSelectedListener.invoke(curColor);
            if (popupWindow != null) popupWindow.dismiss();
        });
        addView(textViewApply, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END));
        ((FrameLayout.LayoutParams) textViewApply.getLayoutParams()).gravity = Gravity.BOTTOM | Gravity.END;
    }

    public void show(View anchor, ColorSelectedListener colorSelectedListener) {
        popupWindow = null;
        this.colorSelectedListener = colorSelectedListener;
        FrameLayout container = new FrameLayout(getContext());
        container.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        container.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(16));
        container.addView(this);

        popupWindow = new PopupWindow(container, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setOnDismissListener(() -> {
            ColorPickerPopup.this.colorSelectedListener = null;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popupWindow.showAtLocation(anchor, Gravity.BOTTOM, 0, 0);
        } else {
            popupWindow.showAsDropDown(anchor);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawLine(0, 0, getMeasuredWidth(), 0, Theme.dividerPaint);
        canvas.drawLine(0, getMeasuredHeight() - AndroidUtilities.dp(36), getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(36), Theme.dividerPaint);
    }

    public interface ColorSelectedListener {
        void invoke(int color);
    }
}
