package org.telegram.ui.Components.MessageAnimations.Editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.MessageAnimations.FourGradientBackground.FourGradientBackgroundView;

public class ColorCell extends FrameLayout {
    private SimpleTextView textView;
    private int color;
    private ColorView colorView;
    private float padding = 23;
    private int number;

    public ColorCell(@NonNull Context context) {
        super(context);

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setMinimumHeight(AndroidUtilities.dp(50));
        textView = new SimpleTextView(context);

        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(16);
        textView.setGravity(Gravity.LEFT);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textView);

        colorView = new ColorView(context);
        addView(colorView);
    }

    public void setData(int number, int color) {
        this.number = number;
        this.color = color;
        textView.setText("Color " + number);
        colorView.refresh();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;
        int viewTop = (height - textView.getTextHeight()) / 2;
        int viewLeft = AndroidUtilities.dp(padding);
        textView.layout(viewLeft, viewTop, viewLeft + colorView.getMeasuredWidth(), viewTop + colorView.getMeasuredHeight());

        viewTop = (height - colorView.getMeasuredHeight()) / 2;
        viewLeft = right - colorView.getMeasuredWidth() - AndroidUtilities.dp(padding);
        colorView.layout(viewLeft, viewTop, viewLeft + colorView.getMeasuredWidth(), viewTop + colorView.getMeasuredHeight());

    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (number != 4) {
            canvas.drawLine(AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    private class ColorView extends TextView {

        private final android.graphics.Rect rect = new Rect();
        private GradientDrawable shape;

        public ColorView(@NonNull Context context) {
            super(context);
            setTextSize(TypedValue.COMPLEX_UNIT_SP,16);
            setTypeface(getTypeface(), Typeface.BOLD);
            setGravity(Gravity.CENTER);
            setText(String.format("#%06X", (0xFFFFFF & color)));
            setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
            setTextColor();

            shape = new GradientDrawable();
            shape.setCornerRadius(AndroidUtilities.dp(6));
            shape.setColor(color);
            setBackground(shape);
        }

        private void setTextColor() {
            setTextColor(Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color))) < 180 ? Color.WHITE : Color.BLACK);
        }

        private void refresh() {
            setText(String.format("#%06X", (0xFFFFFF & color)));
            setTextColor();
            shape.setColor(color);
            setBackground(shape);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = AndroidUtilities.dp(90);
            int height = AndroidUtilities.dp(32);
            setMeasuredDimension(width, height);
        }
    }
}
