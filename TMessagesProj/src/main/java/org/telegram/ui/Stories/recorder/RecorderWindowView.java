package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Bitmap;

import org.telegram.ui.Components.SizeNotifierFrameLayout;

public class RecorderWindowView extends SizeNotifierFrameLayout {

    public RecorderWindowView(Context context) {
        super(context);
    }

    public void drawBlurBitmap(Bitmap bitmap, float amount) {}

    public int getBottomPadding2() { return 0; }

    public int getPaddingUnderContainer() { return 0; }
}
