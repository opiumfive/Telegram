package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import java.util.List;

@SuppressLint("ViewConstructor")
public class QuickShareView extends FrameLayout {

    private final float RADIUS = AndroidUtilities.dpf2(16);
    private final float POPUP_HEIGHT = AndroidUtilities.dpf2(60);
    private final float DISTANCE_BETWEEN = AndroidUtilities.dpf2(16.5f);
    private final float AVA_RADIUS = AndroidUtilities.dpf2(22);
    private final float AVA_BETWEEN = AndroidUtilities.dpf2(12);


    private final Interpolator circleUpInterpolator = new CubicBezierInterpolator(.21,.47,.45,.93);
    private final Interpolator circleDownInterpolator = new CubicBezierInterpolator(.53,.12,.45,.93);
    private final Interpolator circleStabInterpolator = new CubicBezierInterpolator(.17,.69,.7,.78);

    private PointF fromPoint;
    private float showProgress = 0;
    private final RectF rect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable drawable;
    private final Paint paint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private long startTime = 0;
    private boolean startJoiningRight, startJoiningLeft = false;
    private float x1, x2 = 0f;
    private float v = 0.8f;
    private float lastRectHeight = 0f;
    private boolean disconnected = false;
    private float startPrLeft = 0f;
    private float startPrRight = 0f;
    private int count = 0;
    private Utilities.CallbackReturn<TLRPC.Dialog, UndoView> callback;
    private List<TLRPC.Dialog> dialogs;
    private final ImageReceiver[] avatars = new ImageReceiver[5];
    private final String[] names = new String[5];
    private boolean releasing = false;
    private boolean releasingAlpha = false;
    private Paint bmpPaint = new Paint();
    private boolean showing = false;
    private ChatMessageCell cell;
    private float showAlphaProgress;
    private Bitmap bitmap;
    private boolean drawOnBitmapThisTime = false;
    private boolean bitmapIsFull = false;
    private int chosenIndex = -1;
    private UndoView undoView;
    private float targetX;
    private float targetY;
    private float currentTouchX = -1f;
    private int currentSelectedIndex = -1;
    private int prevSelectedIndex = -1;
    private Shader shader;
    private boolean showingFromRight;
    private long selectionStartTime = 0;
    private float startTrackingX = -1f;
    private float startTrackingY = -1f;
    private boolean startedSelecting = false;
    private int[] colorsTmp = new int[2];
    private float[] percTmp = new float[2];
    private Bitmap scrimBlurBitmap;
    private Paint scrimBlurBitmapPaint;
    private BitmapShader scrimBlurBitmapShader;
    private Matrix scrimBlurMatrix;
    private float actionBarBottom;

    public QuickShareView(Context context) {
        super(context);
        setWillNotDraw(false);
        android.util.Log.d("wwttff", "device class " + SharedConfig.getDevicePerformanceClass());

        paint.setStyle(Paint.Style.FILL);
        paint3.setStyle(Paint.Style.FILL);
        shadowPaint.setShadowLayer(AndroidUtilities.dpf2(3), 0, 0, 0x1f000000);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dpf2(14));
        textPaint.setTypeface(AndroidUtilities.bold());

        drawable = ContextCompat.getDrawable(context, R.drawable.filled_button_share);
    }

    @Override
    protected void onDraw(Canvas canv) {
        super.onDraw(canv);
        Canvas canvas = canv;
        if (fromPoint == null) return;

        float maxDuration = releasingAlpha ? 400 : releasing ? 500 : 1200;
        long duration = System.currentTimeMillis() - startTime;
        if (releasingAlpha) {
            showAlphaProgress = duration / maxDuration;
            if (bitmap == null) {
                bitmap = Bitmap.createBitmap((int) (rect.width() + AndroidUtilities.dp(20)), (int) (rect.height() + AndroidUtilities.dp(20)), Bitmap.Config.ARGB_8888);
                drawOnBitmapThisTime = true;
                canvas = new Canvas(bitmap);
            }
        } else {
            showProgress = duration / maxDuration;
        }
        if (releasing) showProgress = 1f - showProgress;
        if (showProgress < 0) showProgress = 0;
        if (showProgress > 1f) showProgress = 1f;

        if (fromPoint != null) {
            float fullWidth = count * 2f * AVA_RADIUS + (count - 1) * AVA_BETWEEN + 2f * AndroidUtilities.dpf2(9);
            float rightSideMax = getMeasuredWidth() - AndroidUtilities.dpf2(10);
            float leftSideMax = rightSideMax - fullWidth;

            if (fromPoint.x < getMeasuredWidth() / 2f) {
                showingFromRight = false;
                leftSideMax = AndroidUtilities.dpf2(10);
                rightSideMax = leftSideMax + fullWidth;
            }
            if (showingFromRight) {
                float diff = leftSideMax + POPUP_HEIGHT / 2 - fromPoint.x + AndroidUtilities.dpf2(8);
                if (diff > 0) {
                    rightSideMax -= diff;
                    leftSideMax -= diff;
                }
            } else {
                float diff = rightSideMax - POPUP_HEIGHT / 2 - fromPoint.x - AndroidUtilities.dpf2(8);
                if (diff < 0) {
                    rightSideMax -= diff;
                    leftSideMax -= diff;
                }
            }

            float circleY = fromPoint.y;
            if (showProgress <= 0.21f) {
                circleY = AndroidUtilities.lerp(fromPoint.y, fromPoint.y - AndroidUtilities.dpf2(15), circleUpInterpolator.getInterpolation(showProgress / 0.21f));
            } else if (showProgress <= 0.5f) {
                circleY = AndroidUtilities.lerp(fromPoint.y - AndroidUtilities.dpf2(15), fromPoint.y + AndroidUtilities.dpf2(2), circleDownInterpolator.getInterpolation((showProgress - 0.21f) / 0.29f));
            } else if (showProgress <= 0.8f) {
                circleY = AndroidUtilities.lerp(fromPoint.y + AndroidUtilities.dpf2(2), fromPoint.y, circleStabInterpolator.getInterpolation((showProgress - 0.5f) / 0.3f));
            }

            float rectY = fromPoint.y - AndroidUtilities.dpf2(62f);
            float rectHeight = POPUP_HEIGHT;
            if (showProgress < 0.32f) {
                rectY = AndroidUtilities.lerp(fromPoint.y, fromPoint.y - AndroidUtilities.dpf2(65f), circleUpInterpolator.getInterpolation(showProgress / 0.32f));
                rectHeight = AndroidUtilities.lerp(RADIUS, POPUP_HEIGHT + AndroidUtilities.dpf2(2), circleUpInterpolator.getInterpolation(showProgress / 0.32f));
            } else if (showProgress < 0.63f) {
                rectY = AndroidUtilities.lerp(fromPoint.y - AndroidUtilities.dpf2(65f), fromPoint.y - AndroidUtilities.dpf2(60f), circleDownInterpolator.getInterpolation((showProgress - 0.32f) / 0.31f));
                rectHeight = AndroidUtilities.lerp(POPUP_HEIGHT + AndroidUtilities.dpf2(2), POPUP_HEIGHT, circleDownInterpolator.getInterpolation((showProgress - 0.32f) / 0.31f));
            } else if (showProgress < 0.8f) {
                rectY = AndroidUtilities.lerp(fromPoint.y - AndroidUtilities.dpf2(60f), fromPoint.y - AndroidUtilities.dpf2(62f), circleStabInterpolator.getInterpolation((showProgress - 0.63f) / 0.17f)); // todo too fast
            }

            float rectLeft;
            float rectRight;
            if (showProgress < 0.12f) {
                lastRectHeight = rectHeight / 2f;
                rectLeft = fromPoint.x - lastRectHeight;
                rectRight = fromPoint.x + lastRectHeight;
            } else if (showProgress < 0.4f) {
                rectLeft = AndroidUtilities.lerp(fromPoint.x - lastRectHeight, leftSideMax - AndroidUtilities.dpf2(showingFromRight ? 20 : 10), circleUpInterpolator.getInterpolation((showProgress - 0.12f) / 0.28f));
                rectRight = AndroidUtilities.lerp(fromPoint.x + lastRectHeight, rightSideMax + AndroidUtilities.dpf2(showingFromRight ? 10 : 20), circleUpInterpolator.getInterpolation((showProgress - 0.12f) / 0.28f));
            } else if (showProgress < 0.7f) {
                rectLeft = AndroidUtilities.lerp(leftSideMax - AndroidUtilities.dpf2(showingFromRight ? 20 : 10), leftSideMax + AndroidUtilities.dpf2(2), circleDownInterpolator.getInterpolation((showProgress - 0.4f) / 0.3f));
                rectRight = AndroidUtilities.lerp(rightSideMax + AndroidUtilities.dpf2(showingFromRight ? 10 : 20), rightSideMax - AndroidUtilities.dpf2(2), circleDownInterpolator.getInterpolation((showProgress - 0.4f) / 0.3f));
            } else {
                rectLeft = AndroidUtilities.lerp(leftSideMax + AndroidUtilities.dpf2(2), leftSideMax, circleStabInterpolator.getInterpolation((showProgress - 0.7f) / 0.3f));
                rectRight = AndroidUtilities.lerp(rightSideMax - AndroidUtilities.dpf2(2), rightSideMax, circleStabInterpolator.getInterpolation((showProgress - 0.7f) / 0.3f));
            }

            rect.set(rectLeft, rectY - rectHeight / 2f, rectRight, rectY + rectHeight / 2f);

            if (drawOnBitmapThisTime) {
                rect.set(AndroidUtilities.dpf2(10), AndroidUtilities.dpf2(10), bitmap.getWidth() - AndroidUtilities.dpf2(10), bitmap.getHeight() - AndroidUtilities.dpf2(10));
            }

            if (!bitmapIsFull) {

                if (showProgress >= 0.28f) {
                    AndroidUtilities.rectTmp.set(rect.left, rect.top, rect.right, rect.bottom + 1);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rectHeight / 2, rectHeight / 2, shadowPaint);
                }

                float stopAt = 0.42f;
                if (showProgress < stopAt) {
                    float progr = circleDownInterpolator.getInterpolation(1f - (stopAt - showProgress) / stopAt);
                    float offsetGradient = AndroidUtilities.lerp(AndroidUtilities.dpf2(42), 0f, progr);
                    float rad = AndroidUtilities.lerp(AndroidUtilities.dpf2(48), AndroidUtilities.dpf2(28), progr);
                    float perc = AndroidUtilities.lerp(0, 0.7f, 1f - (stopAt - showProgress) / stopAt);
                    colorsTmp[0] = paint3.getColor();
                    colorsTmp[1] = paint.getColor();
                    percTmp[0] = perc;
                    percTmp[1] = 1f;
                    shader = new RadialGradient(fromPoint.x, circleY + offsetGradient, rad, colorsTmp, percTmp, Shader.TileMode.CLAMP);
                    gradientPaint.setShader(shader);
                }

                if (showProgress > 0f) {
                    AndroidUtilities.rectTmp.set(rect.left, rect.top, rect.right, rect.bottom + 1);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rectHeight / 2, rectHeight / 2, showProgress > stopAt ? paint : gradientPaint);
                }

                if (!releasing && !releasingAlpha) {
                    float maxDistance = AndroidUtilities.dpf2(65);
                    float handle_len_rate = 5f;
                    float rad2 = rectHeight / 2f;
                    if (!startJoiningRight) x1 = rectRight - rad2; // right
                    float y12 = rect.top + rad2;
                    if (!startJoiningLeft) x2 = rectLeft + rad2;
                    float d1 = getDistance(fromPoint.x, circleY, x1, y12);
                    float d2 = getDistance(fromPoint.x, circleY, x2, y12);
                    if (d1 > AndroidUtilities.dpf2(46)) {
                        startPrRight = showProgress;
                        startJoiningRight = true;
                    }
                    if (d2 > AndroidUtilities.dpf2(46)) {
                        startPrLeft = showProgress;
                        startJoiningLeft = true;
                    }
                    if (startJoiningRight && x1 > fromPoint.x) {
                        if (showProgress <= 0.25f) {
                            x1 = AndroidUtilities.lerp(x1, fromPoint.x, (showProgress - startPrRight) / (0.4f - startPrRight));
                        } else if (showProgress <= 0.45f) {
                            x1 = AndroidUtilities.lerp(x1, fromPoint.x, (showProgress - 0.25f) / 0.2f);
                        }
                    }
                    if (startJoiningLeft && x2 < fromPoint.x) {
                        if (showProgress <= 0.25f) {
                            x2 = AndroidUtilities.lerp(x2, fromPoint.x, (showProgress - startPrLeft) / (0.4f - startPrLeft));
                        } else if (showProgress <= 0.45f) {
                            x2 = AndroidUtilities.lerp(x2, fromPoint.x, (showProgress - 0.25f) / 0.2f);
                        }
                    }
                    if ((startJoiningRight || startJoiningLeft) && v > 0.21f) v -= 0.1f;
                    if (Math.abs(x1 - x2) <= AndroidUtilities.dpf2(2)) maxDistance = DISTANCE_BETWEEN + RADIUS + rad2 + AndroidUtilities.dpf2(1f);
                    boolean shouldDisconnect = false;
                    path.reset();
                    if (!disconnected) {
                        boolean s1 = getMetaBallSegment(path, fromPoint.x, circleY, RADIUS, x1, rect.top + rad2, rad2, true, v, handle_len_rate, maxDistance);
                        boolean s2 = getMetaBallSegment(path, fromPoint.x, circleY, RADIUS, x2, rect.top + rad2, rad2, false, v, handle_len_rate, maxDistance);
                        path.close();
                        shouldDisconnect = !s1 || !s2;
                    } else {
                        if (showProgress <= 0.45f) {
                            path.moveTo(fromPoint.x - AndroidUtilities.dpf2(4f), circleY - RADIUS + AndroidUtilities.dpf2(2));
                            float toY = AndroidUtilities.lerp(circleY - RADIUS, circleY - RADIUS - AndroidUtilities.dpf2(2), (0.45f - showProgress) / (0.45f - disconnectProgress));
                            path.lineTo(fromPoint.x, toY);
                            path.lineTo(fromPoint.x + AndroidUtilities.dpf2(4f), circleY - RADIUS + AndroidUtilities.dpf2(2));
                            path.close();
                            canvas.drawPath(path, gradientPaint);

                            path.reset();
                            path.moveTo(fromPoint.x - AndroidUtilities.dpf2(4f), rect.bottom - AndroidUtilities.dpf2(2));
                            toY = AndroidUtilities.lerp(rect.bottom, rect.bottom + AndroidUtilities.dpf2(3), (0.45f - showProgress) / (0.45f - disconnectProgress));
                            path.lineTo(fromPoint.x, toY);
                            path.lineTo(fromPoint.x + AndroidUtilities.dpf2(4f), rect.bottom - AndroidUtilities.dpf2(2));
                            path.close();
                            canvas.drawPath(path, gradientPaint);
                        }
                    }
                    if (shouldDisconnect) {
                        disconnected = true;
                        disconnectProgress = showProgress;
                        path.reset();
                        path.moveTo(fromPoint.x - AndroidUtilities.dpf2(4f), circleY - RADIUS + AndroidUtilities.dpf2(2));
                        path.lineTo(fromPoint.x, circleY - RADIUS - AndroidUtilities.dpf2(2));
                        path.lineTo(fromPoint.x + AndroidUtilities.dpf2(4f), circleY - RADIUS + AndroidUtilities.dpf2(2));
                        path.close();
                        canvas.drawPath(path, gradientPaint);

                        path.reset();
                        path.moveTo(fromPoint.x - AndroidUtilities.dpf2(4f), rect.bottom - AndroidUtilities.dpf2(2));
                        path.lineTo(fromPoint.x, rect.bottom + AndroidUtilities.dpf2(3));
                        path.lineTo(fromPoint.x + AndroidUtilities.dpf2(4f), rect.bottom - AndroidUtilities.dpf2(2));
                        path.close();
                        canvas.drawPath(path, gradientPaint);
                    } else {
                        canvas.drawPath(path, gradientPaint);
                    }
                }
                boolean drawCircle = showProgress > 0f && showProgress < 1f;

                if (drawCircle) {
                    canvas.drawCircle(fromPoint.x, circleY, RADIUS, showProgress > stopAt ? paint3 : gradientPaint);
                    final int scx = (int) (fromPoint.x), scy = (int) (circleY);
                    final int shw = drawable.getIntrinsicWidth() / 2, shh = drawable.getIntrinsicHeight() / 2;
                    drawable.setBounds(scx - shw, scy - shh, scx + shw, scy + shh);
                    float degrees = 0f;
                    if (showProgress < 0.22f) {
                        degrees = AndroidUtilities.lerp(0, -40, circleUpInterpolator.getInterpolation(showProgress / 0.22f));
                    } else if (showProgress < 0.43f) {
                        degrees = AndroidUtilities.lerp(-40, 30, circleDownInterpolator.getInterpolation((showProgress - 0.22f) / 0.21f));
                    } else if (showProgress < 0.77f) {
                        degrees = AndroidUtilities.lerp(30, -5, circleDownInterpolator.getInterpolation((showProgress - 0.43f) / 0.34f));
                    } else {
                        degrees = AndroidUtilities.lerp(-5, 0, circleUpInterpolator.getInterpolation((showProgress - 0.77f) / 0.23f));
                    }
                    canvas.save();
                    canvas.rotate(degrees, fromPoint.x, circleY);
                    drawable.draw(canvas);
                    canvas.restore();
                }

                int from = 0;
                int to = 5;
                float offsetAvaX = 0f;

                if (count == 4) {
                    to = 4;
                    offsetAvaX = AVA_RADIUS + AVA_BETWEEN / 2f;
                } else if (count == 3) {
                    offsetAvaX = AVA_RADIUS * 2f + AVA_BETWEEN / 2f + AndroidUtilities.dpf2(7f);
                    //from = 1;
                    to = 3;
                } else if (count == 2) {
                    offsetAvaX = AVA_RADIUS * 3f + AVA_BETWEEN + AndroidUtilities.dpf2(6f);
                    //from = 1;
                    to = 2;
                } else if (count == 1) {
                    offsetAvaX = AVA_RADIUS * 4f + AVA_BETWEEN * 2 + AndroidUtilities.dpf2(1f);
                    //from = 2;
                    to = 1;
                }

                float avaFromLeft = rectLeft;
                float avaToRight = rectRight;
                float avaCenter = rectY;

                if (drawOnBitmapThisTime) {
                    avaFromLeft = AndroidUtilities.dpf2(10f);
                    avaToRight = bitmap.getWidth() - AndroidUtilities.dp(10f);
                    avaCenter = bitmap.getHeight() / 2f;
                }

                int currentSelectedIndexLocal = startedSelecting ? 5 : -1;
                if (currentTouchX >= rectLeft && currentTouchX < rectRight) {
                    currentSelectedIndexLocal = 0;
                    float curL = rectLeft + AndroidUtilities.dpf2(15) + AVA_RADIUS * 2f;
                    while (curL < currentTouchX && curL < rectRight) {
                        curL += AVA_RADIUS * 2f + AVA_BETWEEN;
                        currentSelectedIndexLocal++;
                    }
                }
                if (currentSelectedIndexLocal != currentSelectedIndex) {
                    selectionStartTime = System.currentTimeMillis();
                    prevSelectedIndex = currentSelectedIndex;
                    currentSelectedIndex = currentSelectedIndexLocal;
                    android.util.Log.d("wwttff", "prevSel = " + prevSelectedIndex + " cursel = " + currentSelectedIndex);
                }

                for (int i = from; i < to; i++) {

                    float selectedProgress = currentSelectedIndex == i ? Math.min(1f, (System.currentTimeMillis() - selectionStartTime) / 220f) :
                            prevSelectedIndex == i ? 1f - Math.min(1f, (System.currentTimeMillis() - selectionStartTime) / 220f) : 0f;
                    float selectedProgressAlpha = currentSelectedIndex == i ? Math.min(1f, (System.currentTimeMillis() - selectionStartTime) / 220f) :
                            prevSelectedIndex == i || prevSelectedIndex == -1 ? 1f - Math.min(1f, (System.currentTimeMillis() - selectionStartTime) / 220f) : 0f;

                    if (i == 2) {
                        float rad;
                        if (showProgress < 0.13f) {
                            rad = 0;
                        } else if (showProgress < 0.35f) {
                            rad = AndroidUtilities.lerp(0f, AVA_RADIUS + AndroidUtilities.dpf2(2), (showProgress - 0.13f) / 0.22f);
                        } else if (showProgress < 0.7f) {
                            rad = AndroidUtilities.lerp(AVA_RADIUS + AndroidUtilities.dpf2(2), AVA_RADIUS, (showProgress - 0.35f) / 0.35f);
                        } else {
                            rad = AndroidUtilities.lerp(AVA_RADIUS, AVA_RADIUS + AndroidUtilities.dpf2(2), selectedProgress);
                        }

                        if (rad > 0) {

                            float centerX = offsetAvaX + avaFromLeft + rect.width() / 2f;
                            boolean hover = false;
                            if (drawOnBitmapThisTime) {
                                if (i == chosenIndex) {
                                    hover = true;
                                }
                            } else {
                                if (currentTouchX >= centerX - rad - AVA_BETWEEN / 2f && currentTouchX <= centerX + rad + AVA_BETWEEN / 2f) {
                                    hover = true;
                                }
                            }

                            if (hover && releasingAlpha) continue;

                            if (avatars[i] != null) {
                                avatars[i].setImageCoords((centerX - AVA_RADIUS), (avaCenter - AVA_RADIUS), AVA_RADIUS * 2f, AVA_RADIUS * 2f);
                                canvas.save();
                                canvas.saveLayerAlpha(centerX - rad, avaCenter - rad, centerX + rad, avaCenter + rad, startedSelecting ? AndroidUtilities.lerp(100, 255, selectedProgressAlpha) : 255, Canvas.ALL_SAVE_FLAG);
                                canvas.scale(rad / AVA_RADIUS, rad / AVA_RADIUS, centerX, avaCenter);
                                avatars[i].draw(canvas);
                                canvas.restore();
                                canvas.restore();
                            }

                            if (names[i] != null && (startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0) > 0) {
                                float textX = centerX;
                                float textW = textPaint.measureText(names[i]);
                                float diff = getMeasuredWidth() - (textX + textW / 2f + AndroidUtilities.dpf2(20));
                                if (diff < 0) {
                                    textX += diff;
                                }
                                diff = (textX - textW / 2f - AndroidUtilities.dpf2(20));
                                if (diff < 0) {
                                    textX -= diff;
                                }
                                float textY = rectY - AndroidUtilities.dpf2(58);
                                float radT = AndroidUtilities.dpf2(13);

                                textPaint.setAlpha(startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0);
                                canvas.save();
                                float scale = AndroidUtilities.lerp(0.8f, 1f, selectedProgress);
                                canvas.scale(scale, scale, textX, textY + AndroidUtilities.dpf2(30));
                                AndroidUtilities.rectTmp.set(textX - textW / 2f - AndroidUtilities.dpf2(13), actionBarBottom + textY - radT, textX + textW / 2f + AndroidUtilities.dpf2(13), actionBarBottom + textY + radT);
                                scrimBlurBitmapPaint.setAlpha(startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0);
                                canvas.save();
                                canvas.translate(0, -actionBarBottom);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, radT, radT, scrimBlurBitmapPaint);
                                canvas.restore();
                                canvas.drawText(names[i], textX - textW / 2f, textY + AndroidUtilities.dpf2(5), textPaint);
                                canvas.restore();
                            }
                        }
                    }
                    if (i == 1 || i == 3) {
                        float rad;
                        if (showProgress < 0.21f) {
                            rad = 0;
                        } else if (showProgress < 0.45f) {
                            rad = AndroidUtilities.lerp(0f, AVA_RADIUS + AndroidUtilities.dpf2(2), (showProgress - 0.21f) / 0.24f);
                        } else if (showProgress < 0.8f) {
                            rad = AndroidUtilities.lerp(AVA_RADIUS + AndroidUtilities.dpf2(2), AVA_RADIUS, (showProgress - 0.45f) / 0.35f);
                        } else {
                            rad = AndroidUtilities.lerp(AVA_RADIUS, AVA_RADIUS + AndroidUtilities.dpf2(2), selectedProgress);
                        }

                        if (rad > 0) {
                            float offset = AVA_RADIUS * 2f + AVA_BETWEEN;
                            float centerX = (offsetAvaX + (avaFromLeft + avaToRight) / 2f + (i < 2 ? -offset : offset));

                            boolean hover = false;
                            if (drawOnBitmapThisTime) {
                                if (i == chosenIndex) {
                                    hover = true;
                                }
                            } else {
                                if (currentTouchX >= centerX - rad - AVA_BETWEEN / 2f && currentTouchX <= centerX + rad + AVA_BETWEEN / 2f) {
                                    hover = true;
                                }
                            }

                            if (hover && releasingAlpha) continue;

                            if (avatars[i] != null) {
                                avatars[i].setImageCoords((centerX - AVA_RADIUS), (avaCenter - AVA_RADIUS), AVA_RADIUS * 2f, AVA_RADIUS * 2f);
                                canvas.save();
                                canvas.saveLayerAlpha(centerX - rad, avaCenter - rad, centerX + rad, avaCenter + rad, startedSelecting ? AndroidUtilities.lerp(100, 255, selectedProgressAlpha) : 255, Canvas.ALL_SAVE_FLAG);
                                canvas.scale(rad / AVA_RADIUS, rad / AVA_RADIUS, centerX, avaCenter);
                                avatars[i].draw(canvas);
                                canvas.restore();
                                canvas.restore();
                            }

                            if (names[i] != null && (startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0) > 0) {
                                float textX = centerX;
                                float textW = textPaint.measureText(names[i]);
                                float diff = getMeasuredWidth() - (textX + textW / 2f + AndroidUtilities.dpf2(20));
                                if (diff < 0) {
                                    textX += diff;
                                }
                                diff = (textX - textW / 2f - AndroidUtilities.dpf2(20));
                                if (diff < 0) {
                                    textX -= diff;
                                }
                                float textY = rectY - AndroidUtilities.dpf2(58);
                                float radT = AndroidUtilities.dpf2(13);

                                textPaint.setAlpha(startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0);
                                canvas.save();
                                float scale = AndroidUtilities.lerp(0.8f, 1f, selectedProgress);
                                canvas.scale(scale, scale, textX, textY + AndroidUtilities.dpf2(30));
                                AndroidUtilities.rectTmp.set(textX - textW / 2f - AndroidUtilities.dpf2(13), actionBarBottom + textY - radT, textX + textW / 2f + AndroidUtilities.dpf2(13), actionBarBottom + textY + radT);
                                scrimBlurBitmapPaint.setAlpha(startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0);
                                canvas.save();
                                canvas.translate(0, -actionBarBottom);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, radT, radT, scrimBlurBitmapPaint);
                                canvas.restore();
                                canvas.drawText(names[i], textX - textW / 2f, textY + AndroidUtilities.dpf2(5), textPaint);
                                canvas.restore();
                            }
                        }
                    }
                    if (i == 0 || i == 4) {
                        float rad;
                        if (showProgress < 0.28f) {
                            rad = 0;
                        } else if (showProgress < 0.5f) {
                            rad = AndroidUtilities.lerp(0f, AVA_RADIUS + AndroidUtilities.dpf2(2), (showProgress - 0.28f) / 0.22f);
                        } else if (showProgress < 0.85f) {
                            rad = AndroidUtilities.lerp(AVA_RADIUS + AndroidUtilities.dpf2(2), AVA_RADIUS, (showProgress - 0.5f) / 0.35f);
                        } else {
                            rad = AndroidUtilities.lerp(AVA_RADIUS, AVA_RADIUS + AndroidUtilities.dpf2(2), selectedProgress);
                        }

                        if (rad > 0) {
                            float offset = (AVA_RADIUS * 2f + AVA_BETWEEN) * 2f;
                            float centerX = offsetAvaX + (avaFromLeft + avaToRight) / 2f + (i < 2 ? -offset : offset);
                            boolean hover = false;
                            if (drawOnBitmapThisTime) {
                                if (i == chosenIndex) {
                                    hover = true;
                                }
                            } else {
                                if (currentTouchX >= centerX - rad - AVA_BETWEEN / 2f && currentTouchX <= centerX + rad + AVA_BETWEEN / 2f) {
                                    hover = true;
                                }
                            }

                            if (hover && releasingAlpha) continue;

                            if (avatars[i] != null) {
                                avatars[i].setImageCoords((centerX - AVA_RADIUS), (avaCenter - AVA_RADIUS), AVA_RADIUS * 2f, AVA_RADIUS * 2f);
                                canvas.save();
                                canvas.saveLayerAlpha(centerX - rad, avaCenter - rad, centerX + rad, avaCenter + rad, startedSelecting ? AndroidUtilities.lerp(100, 255, selectedProgressAlpha) : 255, Canvas.ALL_SAVE_FLAG);
                                canvas.scale(rad / AVA_RADIUS, rad / AVA_RADIUS, centerX, avaCenter);
                                avatars[i].draw(canvas);
                                canvas.restore();
                                canvas.restore();
                            }

                            if (names[i] != null && (startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0) > 0) {
                                float textX = centerX;
                                float textW = textPaint.measureText(names[i]);
                                float diff = getMeasuredWidth() - (textX + textW / 2f + AndroidUtilities.dpf2(20));
                                if (diff < 0) {
                                    textX += diff;
                                }
                                diff = (textX - textW / 2f - AndroidUtilities.dpf2(20));
                                if (diff < 0) {
                                    textX -= diff;
                                }
                                float textY = rectY - AndroidUtilities.dpf2(58);
                                float radT = AndroidUtilities.dpf2(13);

                                textPaint.setAlpha(startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0);
                                canvas.save();
                                float scale = AndroidUtilities.lerp(0.8f, 1f, selectedProgress);
                                canvas.scale(scale, scale, textX, textY + AndroidUtilities.dpf2(30));
                                AndroidUtilities.rectTmp.set(textX - textW / 2f - AndroidUtilities.dpf2(13), actionBarBottom + textY - radT, textX + textW / 2f + AndroidUtilities.dpf2(13), actionBarBottom + textY + radT);
                                scrimBlurBitmapPaint.setAlpha(startedSelecting ? (int) (AndroidUtilities.lerp(0, 255, selectedProgress)) : 0);
                                canvas.save();
                                canvas.translate(0, -actionBarBottom);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, radT, radT, scrimBlurBitmapPaint);
                                canvas.restore();
                                canvas.drawText(names[i], textX - textW / 2f, textY + AndroidUtilities.dpf2(5), textPaint);
                                canvas.restore();
                            }
                        }
                    }
                }
            }

            if (drawOnBitmapThisTime) {
                drawOnBitmapThisTime = false;
                bitmapIsFull = true;
            }
            if (releasingAlpha) canvas.saveLayerAlpha(rectLeft, rectY - rectHeight / 2f, rectRight, rectY + rectHeight / 2f, (int) (0xff * (1f - showAlphaProgress)), Canvas.ALL_SAVE_FLAG);
            if (releasingAlpha && bitmap != null) {
                Utilities.stackBlurBitmap(bitmap, 2);
                canv.drawBitmap(bitmap, rectLeft - AndroidUtilities.dpf2(10), rectY - rectHeight / 2f - AndroidUtilities.dpf2(10), bmpPaint);
            }
            if (releasingAlpha) {
                canvas.restore();
                if (chosenIndex != -1) {
                    if (undoView != null && showAlphaProgress < 0.5f) {
                        targetX = undoView.getX() + AndroidUtilities.dpf2(30);
                        targetY = undoView.getY() + undoView.getTranslationY() - AndroidUtilities.dp(10);
                    }
                    float rad = AndroidUtilities.lerp(AVA_RADIUS, AndroidUtilities.dp(10), showAlphaProgress);
                    float alpha = 1f;
                    float indexOffset = AVA_RADIUS;
                    if (chosenIndex > 0) indexOffset += chosenIndex * (AVA_RADIUS * 2f + AVA_BETWEEN);
                    float centerX = rectLeft + AndroidUtilities.dpf2(9) + indexOffset;
                    float newX = AndroidUtilities.lerp(centerX, targetX, CubicBezierInterpolator.EASE_OUT.getInterpolation(showAlphaProgress));
                    float newY = AndroidUtilities.lerp(rectY, targetY, CubicBezierInterpolator.EASE_IN.getInterpolation(showAlphaProgress));

                    avatars[chosenIndex].setImageCoords((newX - AVA_RADIUS), (newY - AVA_RADIUS), AVA_RADIUS * 2f, AVA_RADIUS * 2f);
                    canv.save();
                    canv.saveLayerAlpha(newX - rad, newY - rad, newX + rad, newY + rad, (int) (0xff * alpha), Canvas.ALL_SAVE_FLAG);
                    canv.scale(rad / AVA_RADIUS, rad / AVA_RADIUS, newX, newY);
                    avatars[chosenIndex].draw(canv);
                    canv.restore();
                    canv.restore();
                }
            }
            if (showProgress < 1f && !releasing || showProgress > 0 && releasing || releasingAlpha || startedSelecting) invalidate();

            if (showAlphaProgress >= 1f) {
                releasingAlpha = false;
                showAlphaProgress = 0f;
                showProgress = 0f;
                fromPoint = null;
                bitmap = null;
                drawOnBitmapThisTime = false;
                bitmapIsFull = false;
                chosenIndex = -1;
                undoView = null;
                targetX = 0f;
                targetY = 0f;
            }

            if (cell != null) cell.invalidateOutbounds();
        }
    }

    float disconnectProgress = 0f;

    private boolean getMetaBallSegment(Path path, float x1, float y1, float r1, float x2, float y2, float r2, boolean left, float v, float handle_len_rate, float maxDistance) {
        float d = getDistance(x1, y1, x2, y2);

        float radius1 = r1;
        float radius2 = r2;
        float pi2 = (float) (Math.PI / 2);
        float u1, u2;

        if (d > maxDistance) {
            return false;
        } else if (d < radius1 + radius2) {
            u1 = (float) Math.acos((radius1 * radius1 + d * d - radius2 * radius2) / (2 * radius1 * d));
            u2 = (float) Math.acos((radius2 * radius2 + d * d - radius1 * radius1) / (2 * radius2 * d));
        } else {
            u1 = 0;
            u2 = 0;
        }

        float centerMin0 = x2 - x1;
        float centerMin1 = y2 - y1;

        float angle1 = (float) Math.atan2(centerMin1, centerMin0);
        float angle2 = (float) Math.acos((radius1 - radius2) / d);
        float angle1a = angle1 + u1 + (angle2 - u1) * v;
        float angle1b = angle1 - u1 - (angle2 - u1) * v;
        float angle2a = (float) (angle1 + Math.PI - u2 - (Math.PI - u2 - angle2) * v);
        float angle2b = (float) (angle1 - Math.PI + u2 + (Math.PI - u2 - angle2) * v);

        float p1a1x = getVectorX(angle1a, radius1);
        float p1a1y = getVectorY(angle1a, radius1);
        float p1b1x = getVectorX(angle1b, radius1);
        float p1b1y = getVectorY(angle1b, radius1);

        float p2a1x = getVectorX(angle2a, radius2);
        float p2a1y = getVectorY(angle2a, radius2);

        float p2b1x = getVectorX(angle2b, radius2);
        float p2b1y = getVectorY(angle2b, radius2);

        float p1ax = p1a1x + x1; float p1ay = p1a1y + y1;
        float p1bx = p1b1x + x1; float p1by = p1b1y + y1;
        float p2ax = p2a1x + x2; float p2ay = p2a1y + y2;
        float p2bx = p2b1x + x2; float p2by = p2b1y + y2;

        float p1_p2x = p1ax - p2ax;
        float p1_p2y = p1ay - p2ay;

        float totalRadius = (radius1 + radius2);
        float d2 = Math.min(v * handle_len_rate, getLength(p1_p2x, p1_p2y) / totalRadius);
        d2 *= Math.min(1, d * 2 / (radius1 + radius2));

        radius1 *= d2;
        radius2 *= d2;

        float sp1x = getVectorX(angle1a - pi2, radius1);
        float sp1y = getVectorY(angle1a - pi2, radius1);
        float sp2x = getVectorX(angle2a + pi2, radius2);
        float sp2y = getVectorY(angle2a + pi2, radius2);
        float sp3x = getVectorX(angle2b - pi2, radius2);
        float sp3y = getVectorY(angle2b - pi2, radius2);
        float sp4x = getVectorX(angle1b + pi2, radius1);
        float sp4y = getVectorY(angle1b + pi2, radius1);
        if (left) {
            path.moveTo(p1ax, p1ay);
            path.cubicTo(p1ax + sp1x, p1ay + sp1y, p2ax + sp2x, p2ay + sp2y, p2ax, p2ay);
        } else {
            path.lineTo(p2bx, p2by);
            path.cubicTo(p2bx + sp3x, p2by + sp3y, p1bx + sp4x, p1by + sp4y, p1bx, p1by);
            path.lineTo(p1ax, p1ay);
        }
        return true;
    }

    private float getDistance(float b10, float b11, float b20, float b21) {
        float x = b10 - b20;
        float y = b11 - b21;
        float d = x * x + y * y;
        return (float) Math.sqrt(d);
    }

    private float getLength(float b0, float b1) {
        return (float) Math.sqrt(b0 * b0 + b1 * b1);
    }

    private float getVectorX(float radians, float length) {
        float x = (float) (Math.cos(radians) * length);
        return (float) (Math.cos(radians) * length);
    }

    private float getVectorY(float radians, float length) {
        float y = (float) (Math.sin(radians) * length);
        return (float) (Math.sin(radians) * length);
    }

    public void showFrom(int count, float x, float y) {
        this.count = count;
        fromPoint = new PointF(x, y);
        showProgress = 0f;
        v = 0.8f;
        disconnected = false;
        startPrLeft = 0f;
        releasing = false;
        releasingAlpha = false;
        startPrRight = 0f;
        lastRectHeight = 0f;
        startJoiningRight = false;
        startJoiningLeft = false;
        startTime = System.currentTimeMillis();
        invalidate();
    }

    public void showFrom(ChatMessageCell cell, List<TLRPC.Dialog> dialogs, float x, float y, int sideButtonColor, int popupColor, float actionBarBottom, Utilities.CallbackReturn<TLRPC.Dialog, UndoView> callback) {
        showing = true;
        paint.setColor(popupColor);
        paint3.setColor(sideButtonColor);
        this.actionBarBottom = actionBarBottom;
        this.cell = cell;
        this.count = dialogs.size();
        this.dialogs = dialogs;
        this.callback = callback;
        for (int i = 0; i < dialogs.size(); i++) {
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatars[i] = new ImageReceiver();
            avatars[i].setRoundRadius((int) AVA_RADIUS);
            int currentAccount = UserConfig.selectedAccount;
            long uid = dialogs.get(i).id;
            if (DialogObject.isUserDialog(uid)) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(uid);
                avatarDrawable.setInfo(currentAccount, user);
                if (UserObject.isReplyUser(user)) {
                    names[i] = LocaleController.getString(R.string.RepliesTitle);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                    avatars[i].setImage(null, null, avatarDrawable, null, null, 0);
                } else if (UserObject.isUserSelf(user)) {
                    names[i] = LocaleController.getString(R.string.SavedMessages);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    avatars[i].setImage(null, null, avatarDrawable, null, null, 0);
                } else {
                    if (user != null) names[i] = ContactsController.formatName(user.first_name, user.last_name);
                    Drawable thumb = avatarDrawable;
                    if (user.photo != null) {
                        if (user.photo.strippedBitmap != null) {
                            thumb = user.photo.strippedBitmap;
                        }
                    }
                    avatars[i].setImage(ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_STRIPPED), "50_50", thumb, user, 0);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-uid);
                if (chat != null) names[i] = chat.title;
                Drawable thumb = avatarDrawable;
                if (chat.photo != null) {
                    if (chat.photo.strippedBitmap != null) {
                        thumb = chat.photo.strippedBitmap;
                    }
                }
                avatarDrawable.setInfo(currentAccount, chat);
                avatars[i].setImage(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_STRIPPED), "50_50", thumb, chat, 0);
            }
        }
        fromPoint = new PointF(x, y);
        showProgress = 0f;
        v = 0.8f;
        disconnected = false;
        startPrLeft = 0f;
        releasing = false;
        startPrRight = 0f;
        lastRectHeight = 0f;
        startJoiningRight = false;
        startJoiningLeft = false;
        releasingAlpha = false;
        showAlphaProgress = 0f;
        bitmap = null;
        drawOnBitmapThisTime = false;
        bitmapIsFull = false;
        chosenIndex = -1;
        undoView = null;
        targetX = 0f;
        targetY = 0f;
        currentTouchX = -1;
        startTrackingX = -1f;
        startTrackingY = -1f;
        selectionStartTime = 0;
        startedSelecting = false;
        showingFromRight = true;
        currentSelectedIndex = -1;
        prevSelectedIndex = -1;

        startTime = System.currentTimeMillis();
        invalidate();
    }

    public void release() {
        showing = false;
        if (currentTouchX < rect.left || currentTouchX > rect.right) {
            releasing = true;
            showProgress = 1f;
            v = 0.1f;
            startTime = System.currentTimeMillis();
            invalidate();
        } else {
            releasingAlpha = true;
            showProgress = 1f;
            startTime = System.currentTimeMillis();
            invalidate();
            if (callback != null) {
                int index = 0;
                float curL = rect.left + AndroidUtilities.dpf2(15) + AVA_RADIUS * 2f;
                while (curL < currentTouchX && curL < rect.right) {
                    curL += AVA_RADIUS * 2f + AVA_BETWEEN;
                    index++;
                }
                try {
                    chosenIndex = index;
                    undoView = callback.run(dialogs.get(index));
                } catch (Exception e) {
                }
            }
        }
        callback = null;
    }

    public boolean sideButtonHidden(ChatMessageCell cell) {
        if (cell != this.cell) return false;
        float to = 0.85f;
        if (Color.alpha(paint3.getColor()) < 255) {
            to = 0.99f;
        }
        return showProgress > 0f && showProgress < to;
    }

    public boolean blockTouches() {
        return showing;
    }

    public void onMove(float x, float y) {
        if (startTrackingX == -1f) {
            startTrackingX = x;
            startTrackingY = y;
        }
        if (!startedSelecting && (Math.abs(startTrackingX - x) > AndroidUtilities.dpf2(5) || (Math.abs(startTrackingY - y) > AndroidUtilities.dpf2(5)))) {
            startedSelecting = true;
        }
        if (startedSelecting) {
            currentTouchX = x;
        }
        invalidate();
    }

    public void setBlurredChat(Bitmap bitmap) {
        scrimBlurBitmap = bitmap;

        scrimBlurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        scrimBlurBitmapPaint.setShader(scrimBlurBitmapShader = new BitmapShader(scrimBlurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        ColorMatrix colorMatrix = new ColorMatrix();
        AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, -.15f);
        AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.47f);

        scrimBlurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        scrimBlurMatrix = new Matrix();

        scrimBlurMatrix.reset();
        final float s = (float) getMeasuredWidth() / scrimBlurBitmap.getWidth();
        scrimBlurMatrix.postScale(s, s);
        scrimBlurBitmapShader.setLocalMatrix(scrimBlurMatrix);
    }
}