package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;

public class SendAsSpan extends View {

    private long uid;
    private String key;
    private static Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable deleteDrawable;
    private RectF rect = new RectF();
    private ImageReceiver imageReceiver;
    private AvatarDrawable avatarDrawable;

    private float progress;
    private boolean deleting;
    private long lastUpdateTime;
    private int[] colors = new int[8];
    private boolean inited = false;
    private TLRPC.Peer currentPeer = null;
    private boolean dontShowAvatarOneTime = false;
    private SendAsUpdateDelegate delegate;
    public static boolean canShow = false;

    public interface SendAsUpdateDelegate {
        void update();
    }

    public SendAsSpan(Context context, SendAsUpdateDelegate delegate) {
        super(context);
        this.delegate = delegate;
        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(14));
        deleteDrawable = getResources().getDrawable(R.drawable.delete);

    }

    public TLRPC.Peer getCurrentPeer() {
        return currentPeer;
    }

    public void setObject(AccountInstance accountInstance, long did2, TLRPC.Peer peer) {
        inited = true;
        currentPeer = peer;
        if (peer == null) return;

        ImageLocation imageLocation = null;
        Object imageParent = null;

        long did = MessageObject.getPeerId(peer);
        TLObject object;
        if (did > 0) {
            object = accountInstance.getMessagesController().getUser(did);
        } else {
            object = accountInstance.getMessagesController().getChat(-did);
        }

        if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            uid = user.id;
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setSmallSize(true);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageLocation = null;
                imageParent = null;
            } else {
                avatarDrawable.setInfo(user);
                imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL);
                imageParent = user;
            }
        } else if (object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            avatarDrawable.setInfo(chat);
            uid = -chat.id;
            imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            imageParent = chat;
        }

        imageReceiver = new ImageReceiver();
        imageReceiver.setRoundRadius(AndroidUtilities.dp(16));
        imageReceiver.setParentView(this);
        imageReceiver.setImageCoords(0, 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));

        imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, imageParent, 1);
        updateColors();

        invalidate();

        SendAsAlert2.loadInit(getContext(), did2, accountInstance, false, param -> {
            canShow = param;
            if (param && delegate != null) {
                delegate.update();
            }
        });
    }

    public void updateColors() {
        int color = avatarDrawable.getColor();
        int back = Theme.getColor(Theme.key_groupcreate_spanBackground);
        int delete = Theme.getColor(Theme.key_groupcreate_spanDelete);
        colors[0] = Color.red(back);
        colors[1] = Color.red(color);
        colors[2] = Color.green(back);
        colors[3] = Color.green(color);
        colors[4] = Color.blue(back);
        colors[5] = Color.blue(color);
        colors[6] = Color.alpha(back);
        colors[7] = Color.alpha(color);
        deleteDrawable.setColorFilter(new PorterDuffColorFilter(delete, PorterDuff.Mode.MULTIPLY));
        backPaint.setColor(back);
    }

    public boolean isDeleting() {
        return deleting;
    }

    public void startDeleteAnimation() {
        if (deleting) {
            return;
        }
        deleting = true;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public void cancelDeleteAnimation() {
        if (!deleting) {
            return;
        }
        deleting = false;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public void cancelDeleteAnimationFast() {
        dontShowAvatarOneTime = true;
        if (!deleting) {
            return;
        }
        deleting = false;

        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public void canShowAvatar() {
        dontShowAvatarOneTime = false;
        invalidate();
    }

    public long getUid() {
        return uid;
    }

    public String getKey() {
        return key;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!inited) return;
        if (deleting && progress != 1.0f || !deleting && progress != 0.0f) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            if (deleting) {
                progress += dt / 120.0f;
                if (progress >= 1.0f) {
                    progress = 1.0f;
                }
            } else {
                progress -= dt / 120.0f;
                if (progress < 0.0f) {
                    progress = 0.0f;
                }
            }
            invalidate();
        }
        canvas.save();
        rect.set(0, 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        backPaint.setColor(Color.argb(colors[6] + (int) ((colors[7] - colors[6]) * progress), colors[0] + (int) ((colors[1] - colors[0]) * progress), colors[2] + (int) ((colors[3] - colors[2]) * progress), colors[4] + (int) ((colors[5] - colors[4]) * progress)));
        //canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), backPaint);
        if (!dontShowAvatarOneTime) {
            canvas.save();
            canvas.scale(1f - progress, 1f - progress, AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            imageReceiver.draw(canvas);
            canvas.restore();
        }
        if (progress != 0) {
            int color = 0xFF50A7EA; //avatarDrawable.getColor();
            float alpha = Color.alpha(color) / 255.0f;
            backPaint.setColor(color);
            backPaint.setAlpha((int) (255 * progress * alpha));
            canvas.drawCircle(AndroidUtilities.dp(16), AndroidUtilities.dp(16), progress * AndroidUtilities.dp(16), backPaint);
            canvas.save();
            canvas.rotate(45 * (1.0f - progress), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            deleteDrawable.setBounds(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            deleteDrawable.setAlpha((int) (255 * progress));
            deleteDrawable.draw(canvas);
            canvas.restore();
        }

        canvas.restore();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (isDeleting() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.getId(), LocaleController.getString("Delete", R.string.Delete)));
    }
}
