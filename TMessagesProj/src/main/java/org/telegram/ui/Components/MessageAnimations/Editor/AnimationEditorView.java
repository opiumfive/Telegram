package org.telegram.ui.Components.MessageAnimations.Editor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.animation.DecelerateInterpolator;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MessageAnimations.Animation.AnimationDrawingUtils;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationInterpolation;

@SuppressLint("ViewConstructor")
public class AnimationEditorView extends View {

    protected final Rect durRect = new Rect();
    private final Rect startInterpolationRect = new Rect();
    private final Rect endInterpolationRect = new Rect();
    private final Rect startDurationRect = new Rect();
    private final Rect endDurationRect = new Rect();

    private final AnimationEditorRenderer animationEditorRenderer;
    private final int sideRectWidth = AndroidUtilities.dp(30) / 2;
    private final int sideRectHeight = AndroidUtilities.dp(60) / 2;
    private final int sideRectSize = AndroidUtilities.dp(40) / 2;
    private final float minDurationDelta = 0.1f;
    private final Rect rect = new Rect();
    private CurveChangedListener changedHandler;
    private AnimationInterpolation info;
    private Rect downRect;
    private PointerType downType;
    private boolean touchCapture = false;
    private final ShouldBlockTouchListener capturedTouchListener;

    public interface ShouldBlockTouchListener {
        void invoke(boolean captured);
    }

    public interface CurveChangedListener {
        void invoke(Object sender, Object param);
    }

    public enum PointerType {
        StartDuration,
        EndDuration,
        StartInterpolation,
        EndInterpolation
    }

    public static class AnimationEditorRenderer {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect rect = new Rect();
        private final RectF rectF = new RectF();
        private final Path path = new Path();
        private final Point point = new Point();

        private final float[] points = new float[16 * 2 * 2];
        private ValueAnimator animator;
        private float animationValue;
        private Rect animationRect;
        private int animationDir;
        private AnimationEditorView animationEditor;
        private float duration;

        protected final int fullHeight;
        protected final int vertOffset;
        protected final int horOffset;
        protected final float horLineWidth;
        protected final float pointWidth;
        protected final float pointRadius;
        protected final float textPadding;

        protected final int backColor;
        protected final int borderColor;
        protected final int endBorderColor;
        protected final int lineGray;
        protected final int durationColor;
        protected final int interpolationColor;

        public AnimationEditorRenderer() {
            backColor = Theme.getColor(Theme.key_windowBackgroundWhite);
            borderColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayLine);
            endBorderColor = Color.argb(0, 255, 255, 255);
            lineGray = Color.argb(255, 231, 231, 233);
            durationColor = Color.argb(255, 255, 206, 26);
            interpolationColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            fullHeight = AndroidUtilities.dp(200);
            vertOffset = AndroidUtilities.dp(30);
            horOffset = AndroidUtilities.dp(30);
            horLineWidth = AndroidUtilities.dp(3);
            pointWidth = AndroidUtilities.dp(3);
            pointRadius = AndroidUtilities.dp(6);
            textPadding = AndroidUtilities.dp(2);
            this.animator = ObjectAnimator.ofFloat(0, 1);
            this.animator.setDuration(255);
            this.animator.setInterpolator(new DecelerateInterpolator());
            this.animator.addUpdateListener(animation -> {
                animationValue = (float) animation.getAnimatedValue();
                if (animationEditor != null) animationEditor.invalidate();
            });
            paint.setTextSize(AndroidUtilities.dp(12));
        }

        public float getDuration() {
            return duration;
        }

        public void setDuration(float duration) {
            this.duration = duration;
        }

        protected void startAnimation(AnimationEditorView editor, Rect animationRect, PointerType type, int dir) {
            this.animationEditor = editor;
            this.animationRect = animationRect;
            this.animator.cancel();
            switch (type) {
                case StartDuration:
                    animationDir = animationRect.left < animationRect.width() ? 1 : -1;
                    break;
                case EndDuration:
                    animationDir = editor.getWidth() - animationRect.right > animationRect.width() ? 1 : -1;
                    break;
                case StartInterpolation:
                    animationDir = animationRect.left - editor.durRect.left > animationRect.width() ? -1 : 1;
                    break;
                case EndInterpolation:
                    break;
            }

            if (dir > 0) {
                this.animator.setFloatValues(0, 1);
            } else {
                this.animator.setFloatValues(this.animationValue, 0);
            }
            this.animator.start();
        }

        protected void drawEditor(Canvas canvas, AnimationEditorView editor) {
            AnimationInterpolation info = editor.getInfo();

            rect.set(1, 1, editor.getWidth() - 1, editor.getHeight() - 1);
            paint.setColor(borderColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);

            int left = rect.left + horOffset;
            int top = rect.top + vertOffset;
            int right = rect.right - horOffset;
            int bottom = rect.bottom - vertOffset;

            int twidth = right - left;
            int theight = bottom - top;

            int durationLeft = left + Math.round(twidth * info.startDuration);
            int durationRight = left + Math.round(twidth * info.endDuration);
            int durationWidth = durationRight - durationLeft;


            paint.setColor(lineGray);
            paint.setStrokeWidth(horLineWidth);

            canvas.drawLine(left, top, right, top, paint);
            canvas.drawLine(left, bottom, right, bottom, paint);

            path.reset();
            path.moveTo(durationLeft, bottom);

            float vstep = (float) horLineWidth / durationWidth;

            for (float v = 0; v <= 1; v += vstep) {
                float y = info.interpolate(0, theight, v);
                path.lineTo(durationLeft + durationWidth * v, bottom - y);
            }
            path.lineTo(durationRight, top);
            canvas.drawPath(path, paint);

            paint.setColor(interpolationColor);
            canvas.drawLine(durationLeft + Math.round(durationWidth * (1 - info.endInterpolation)), top, durationRight, top, paint);
            canvas.drawLine(durationLeft, bottom, durationLeft + Math.round(durationWidth * info.startInterpolation), bottom, paint);

            float step = (theight + horLineWidth * 2) / 16;
            int num = 0;
            for (int l = durationLeft; l <= durationRight; l += durationWidth) {
                for (float t = top + step; t < bottom - step; t += step) {
                    points[num++] = l;
                    points[num++] = t;
                }
            }

            Paint.Cap cap = paint.getStrokeCap();

            paint.setColor(durationColor);
            paint.setStrokeWidth(pointWidth);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawPoints(points, 0, num, paint);

            num = 0;
            points[num++] = durationLeft;
            points[num++] = top;

            points[num++] = durationRight;
            points[num++] = top;

            points[num++] = durationLeft;
            points[num++] = bottom;

            points[num++] = durationRight;
            points[num++] = bottom;

            paint.setColor(backColor);
            paint.setStrokeWidth(pointRadius * 2);
            canvas.drawPoints(points, 0, num, paint);

            paint.setColor(durationColor);
            paint.setStrokeWidth(pointRadius);
            canvas.drawPoints(points, 0, num, paint);

            paint.setStrokeCap(cap);
        }

        protected void drawButton(Canvas canvas, AnimationEditorView editor, Rect buttonRect, PointerType type) {
            AnimationInterpolation info = editor.getInfo();
            rect.set(buttonRect);
            int width = rect.width() / 4;
            int height = rect.height() / 4;
            rect.inset(width, height);
            int color = backColor;
            int buttonColor = (type == PointerType.StartDuration || type == PointerType.EndDuration) ? durationColor : interpolationColor;

            boolean animate = buttonRect == this.animationRect && animationValue > 0;

            if (animate) {
                color = AnimationDrawingUtils.getColor(backColor, buttonColor, animationValue);
                rect.inset(-Math.round(width * animationValue), -Math.round(height * animationValue));
            }

            float r = rect.width() / 2;

            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            rectF.set(rect);
            canvas.drawRoundRect(rectF, r, r, paint);

            AnimationDrawingUtils.instance.setCanvas(canvas);
            AnimationDrawingUtils.instance.drawRoundRectGradient(rect, r, r, borderColor, endBorderColor, AndroidUtilities.dp(2));

            float angle = 0;
            String text = null;
            int dir = animationDir;
            switch (type) {
                case StartDuration:
                    text = Math.round(duration * info.startDuration / 60f * 1000) + "ms";
                    angle = (float) (Math.PI * animationValue / 2);
                    if (!animate) dir = buttonRect.left < buttonRect.width() ? 1 : -1;
                    break;
                case EndDuration:
                    text = Math.round(duration * info.endDuration / 60f * 1000) + "ms";
                    angle = (float) (Math.PI * animationValue / 2);
                    if (!animate) dir = editor.getWidth() - buttonRect.right > buttonRect.width() ? 1 : -1;
                    break;
                case StartInterpolation:
                    text = Math.round(100 * info.startInterpolation) + "%";
                    angle = (float) (Math.PI * (2 * animationValue - 1) / 2);
                    if (!animate) dir = buttonRect.left - editor.durRect.left > buttonRect.width() ? -1 : 1;
                    break;
                case EndInterpolation:
                    text = Math.round(100 * info.endInterpolation) + "%";
                    break;
            }

            AnimationDrawingUtils.instance.getTextSize(text, point, paint);

            float x = rect.left + (rect.width() - point.x) / 2;
            float y = rect.top + (rect.height() + point.y) / 2;

            float dx = 0;
            float dy = 0;
            if (animate) {
                dx = dir * (float) Math.cos(angle);
                dy = -(float) Math.sin(angle);
            }

            switch (type) {
                case StartDuration:
                case EndDuration:
                    x += dir * (point.x + rect.width() + AndroidUtilities.dp(6)) / 2;
                    if (animate) {
                        x += (dx - dir) * rect.width();
                        y += dy * rect.height() * 0.8f;
                    }
                    break;
                case StartInterpolation:
                    y += rect.height();
                    if (animate) {
                        y += (dy - 1) * rect.height();
                        x += dx * rect.width();
                    }
                    break;
                case EndInterpolation:
                    y = point.y + AndroidUtilities.dp(6);
                    if (animate) x += (dx - dir) * rect.width();
                    break;
            }

            paint.setColor(backColor);
            rectF.set(x - textPadding, y - point.y - textPadding, x + point.x + textPadding, y + textPadding);
            canvas.drawRoundRect(rectF, point.y / 2, point.y / 2, paint);
            paint.setColor(buttonColor);
            canvas.drawText(text, x, y, paint);
        }
    }

    public AnimationEditorView(Context context, ShouldBlockTouchListener capturedTouchListener) {
        super(context);
        this.changedHandler = null;
        this.capturedTouchListener = capturedTouchListener;
        this.animationEditorRenderer = new AnimationEditorRenderer();
        this.info = null;

        setBackgroundColor(animationEditorRenderer.backColor);
    }

    public void setData(AnimationInterpolation info, CurveChangedListener changedHandler) {
        this.changedHandler = changedHandler;
        this.info = info;
    }

    public float getDuration() {
        return animationEditorRenderer.getDuration();
    }

    public void setDuration(float duration) {
        animationEditorRenderer.setDuration(duration);
    }

    public AnimationInterpolation getInfo() {
        return info;
    }

    public void setInfo(AnimationInterpolation info) {
        this.info = info;
        this.layout();
        this.invalidate();
    }

    public boolean isTouchCaptured() {
        return touchCapture;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthMeasureSpec, animationEditorRenderer.fullHeight);
    }

    private void layout() {
        rect.set(animationEditorRenderer.horOffset, animationEditorRenderer.vertOffset, getWidth() - animationEditorRenderer.horOffset, getHeight() - animationEditorRenderer.vertOffset);
        durRect.set(rect.left + Math.round(rect.width() * info.startDuration), rect.top, rect.left + Math.round(rect.width() * info.endDuration), rect.bottom);

        int btop = rect.top + rect.height() / 2 - sideRectHeight;
        int bbot = rect.bottom - rect.height() / 2 + sideRectHeight;

        startDurationRect.set(durRect.left - sideRectWidth, btop, durRect.left + sideRectWidth, bbot);
        endDurationRect.set(durRect.right - sideRectWidth, btop, durRect.right + sideRectWidth, bbot);

        int bleft = durRect.left + Math.round(durRect.width() * info.startInterpolation);
        startInterpolationRect.set(bleft - sideRectSize, rect.bottom - sideRectSize, bleft + sideRectSize, rect.bottom + sideRectSize);

        bleft = durRect.left + Math.round(durRect.width() * (1 - info.endInterpolation));
        endInterpolationRect.set(bleft - sideRectSize, rect.top - sideRectSize, bleft + sideRectSize, rect.top + sideRectSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        this.layout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        animationEditorRenderer.drawEditor(canvas, this);
        animationEditorRenderer.drawButton(canvas, this, startDurationRect, PointerType.StartDuration);
        animationEditorRenderer.drawButton(canvas, this, endDurationRect, PointerType.EndDuration);
        animationEditorRenderer.drawButton(canvas, this, startInterpolationRect, PointerType.StartInterpolation);
        animationEditorRenderer.drawButton(canvas, this, endInterpolationRect, PointerType.EndInterpolation);
    }

    public void apply() {
    }

    public void restore() {
        this.invalidate();
    }

    private void onChanged() {
        if (changedHandler != null) changedHandler.invoke(this, null);
        layout();
        invalidate();
    }

    private void setStartDuration(float value) {
        value = Math.max(0, Math.min(info.endDuration - minDurationDelta, value));
        if (info.startDuration == value) return;
        info.startDuration = value;
        onChanged();
    }

    private void setEndDuration(float value) {
        value = Math.max(info.startDuration + minDurationDelta, Math.min(1, value));
        if (info.endDuration == value) return;
        info.endDuration = value;
        onChanged();
    }

    private void setStartInterpolation(float value) {
        value = Math.max(0, Math.min(1, value));
        if (info.startInterpolation == value) return;
        info.startInterpolation = value;
        onChanged();
    }

    private void setEndInterpolation(float value) {
        value = Math.max(0, Math.min(1, value));
        if (info.endInterpolation == value) return;
        info.endInterpolation = value;
        onChanged();
    }

    private void setDuration(float start, float end) {
        if (info.startDuration == start && info.endDuration == end) return;
        info.startDuration = start;
        info.endDuration = end;
        onChanged();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getPointerCount() == 1) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    int x = (int) e.getX();
                    int y = (int) e.getY();
                    if (startDurationRect.contains(x, y)) {
                        downRect = startDurationRect;
                        downType = PointerType.StartDuration;
                    } else if (endDurationRect.contains(x, y)) {
                        downRect = endDurationRect;
                        downType = PointerType.EndDuration;
                    } else if (startInterpolationRect.contains(x, y)) {
                        downRect = startInterpolationRect;
                        downType = PointerType.StartInterpolation;
                    } else if (endInterpolationRect.contains(x, y)) {
                        downRect = endInterpolationRect;
                        downType = PointerType.EndInterpolation;
                    }
                    if (downType != null) animationEditorRenderer.startAnimation(this, downRect, downType, 1);
                    touchCapture = downRect != null;
                    capturedTouchListener.invoke(touchCapture);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (downRect != null) {
                        if (downRect == startDurationRect) {
                            setStartDuration((e.getX() - rect.left) / rect.width());
                        } else if (downRect == endDurationRect) {
                            setEndDuration((e.getX() - rect.left) / rect.width());
                        } else if (downRect == startInterpolationRect) {
                            setStartInterpolation((e.getX() - durRect.left) / durRect.width());
                        } else if (downRect == endInterpolationRect) {
                            setEndInterpolation(1 - (e.getX() - durRect.left) / durRect.width());
                        } else if (downRect == durRect) {
                            float durDelta = info.endDuration - info.startDuration;
                            float dur = Math.max(0, Math.min(1 - durDelta, e.getX() / rect.width()));
                            setDuration(dur, dur + durDelta);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    touchCapture = false;
                    capturedTouchListener.invoke(touchCapture);
                    if (downType != null) animationEditorRenderer.startAnimation(this, downRect, downType, -1);
                    downRect = null;
                    downType = null;
                    break;
            }
            return true;
        }
        return super.onTouchEvent(e);
    }
}
