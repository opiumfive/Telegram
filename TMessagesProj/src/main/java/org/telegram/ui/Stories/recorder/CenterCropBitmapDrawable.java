package org.telegram.ui.Stories.recorder;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.BitmapShader;
import android.graphics.RectF;
import android.graphics.Shader;

public class CenterCropBitmapDrawable extends Drawable {

    private final BitmapDrawable bitmapDrawable;
    private final Paint paint;
    private final float cornerRadius;

    public CenterCropBitmapDrawable(BitmapDrawable bitmapDrawable, float cornerRadius) {
        this.bitmapDrawable = bitmapDrawable;
        this.cornerRadius = cornerRadius;
        this.paint = new Paint();
        this.paint.setAntiAlias(true);
    }

    @Override
    public void draw(Canvas canvas) {
        Bitmap bitmap = bitmapDrawable.getBitmap();
        if (bitmap == null) {
            return;
        }

        Rect bounds = getBounds();
        float scaleX = (float) bounds.width() / bitmap.getWidth();
        float scaleY = (float) bounds.height() / bitmap.getHeight();
        float scale = Math.max(scaleX, scaleY);

        int scaledWidth = (int) (bitmap.getWidth() * scale);
        int scaledHeight = (int) (bitmap.getHeight() * scale);

        int srcLeft = 0;
        int srcTop = 0;
        int srcRight = bitmap.getWidth();
        int srcBottom = bitmap.getHeight();

        if (scaledWidth > bounds.width()) {
            int excessWidth = scaledWidth - bounds.width();
            //srcLeft += excessWidth / 2;
            //srcRight -= excessWidth / 2;
        }

        if (scaledHeight > bounds.height()) {
            int excessHeight = scaledHeight - bounds.height();
            srcTop += excessHeight / 2;
            srcBottom -= excessHeight / 2;
        }

        // Create a shader to draw the bitmap
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        RectF srcRect = new RectF(srcLeft, srcTop, srcRight, srcBottom);
        RectF dstRect = new RectF(bounds);
        shader.setLocalMatrix(calculateMatrix(srcRect, dstRect));
        paint.setShader(shader);
        canvas.drawRoundRect(dstRect, cornerRadius, cornerRadius, paint);
    }

    private android.graphics.Matrix calculateMatrix(RectF srcRect, RectF dstRect) {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setRectToRect(srcRect, dstRect, android.graphics.Matrix.ScaleToFit.FILL);
        return matrix;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return bitmapDrawable.getOpacity();
    }
}