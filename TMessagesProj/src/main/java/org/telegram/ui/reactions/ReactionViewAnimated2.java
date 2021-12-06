package org.telegram.ui.reactions;


import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

// faster version
public class ReactionViewAnimated2 implements NotificationCenter.NotificationCenterDelegate {

    private final String INTERACTIONS_STICKER_PACK = "EmojiAnimations";
    private final ChatActivity activity;
    private ArrayList<DrawingObject> drawingObjects = new ArrayList<>();
    HashMap<String, ArrayList<TLRPC.Document>> emojiInteractionsStickersMap = new HashMap<>();
    private FrameLayout contentLayout;
    private RecyclerListView listView;
    private long dialogId;
    private boolean attached;
    boolean inited = false;
    private Random random = new Random();
    private int index = 0;
    private TLRPC.TL_messages_stickerSet set;

    public ReactionViewAnimated2(ChatActivity activity, RecyclerListView chatListView, long dialogId) {
        this.activity = activity;
        contentLayout = activity.getContentView();
        listView = chatListView;
        this.dialogId = dialogId;
    }

    public void onScrolled(int dy) {
        for (int i = 0; i < drawingObjects.size(); i++) {
            if (!drawingObjects.get(i).viewFound) {
                drawingObjects.get(i).lastY -= dy;
                drawingObjects.get(i).fromY -= dy;
                drawingObjects.get(i).toY2 -= dy;
                drawingObjects.get(i).toY1 -= dy;
            }
        }
    }

    public boolean isCurrentlyPlaying() {
        if (drawingObjects.isEmpty()) return false;
        for (int i = 0; i < drawingObjects.size(); i++) {
            if (!drawingObjects.get(i).wasPlayed) return true;
        }
        return false;
    }

    public void onAttachedToWindow() {
        checkStickerPack();
        NotificationCenter.getInstance(activity.getCurrentAccount()).addObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    public void onDetachedFromWindow() {
        attached = false;
        NotificationCenter.getInstance(activity.getCurrentAccount()).removeObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    public void checkStickerPack() {
        if (inited) {
            return;
        }
        set = MediaDataController.getInstance(activity.getCurrentAccount()).getStickerSetByName(INTERACTIONS_STICKER_PACK);
        if (set == null) {
            set = MediaDataController.getInstance(activity.getCurrentAccount()).getStickerSetByEmojiOrName(INTERACTIONS_STICKER_PACK);
        }
        if (set == null) {
            MediaDataController.getInstance(activity.getCurrentAccount()).loadStickersByEmojiOrName(INTERACTIONS_STICKER_PACK, false, true);
        }
        if (set != null) {
            HashMap<Long, TLRPC.Document> stickersMap = new HashMap<>();
            for (int i = 0; i < set.documents.size(); i++) {
                stickersMap.put(set.documents.get(i).id, set.documents.get(i));
            }
            for (int i = 0; i < set.packs.size(); i++) {
                TLRPC.TL_stickerPack pack = set.packs.get(i);
                if (pack.documents.size() > 0) {
                    ArrayList<TLRPC.Document> stickers = new ArrayList<>();
                    emojiInteractionsStickersMap.put(pack.emoticon, stickers);
                    for (int j = 0; j < pack.documents.size(); j++) {
                        stickers.add(stickersMap.get(pack.documents.get(j)));
                    }

                    if (pack.emoticon.equals("❤")) {
                        String[] heartEmojies = new String[]{"🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎"};
                        for (String heart : heartEmojies) {
                            emojiInteractionsStickersMap.put(heart, stickers);
                        }
                    }
                }
            }
            inited = true;
        }
    }

    public void draw(Canvas canvas) {
        if (!drawingObjects.isEmpty()) {
            for (int i = 0; i < drawingObjects.size(); i++) {
                DrawingObject drawingObject = drawingObjects.get(i);
                //drawingObject.viewFound = false;
                if (!drawingObject.reactionLocationFound) {
                    for (int k = 0; k < listView.getChildCount(); k++) {
                        View child = listView.getChildAt(k);
                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            if (cell.getMessageObject().getId() == drawingObject.messageId) {
                                RectF rect = cell.getLocationForReaction(drawingObject.reaction);
                                if (rect != null && rect.width() >= AndroidUtilities.dp(9) && rect.height() >= AndroidUtilities.dp(10)) {
                                    drawingObject.reactionLocationFound = true;
                                    drawingObject.toX2 += rect.left;
                                    drawingObject.toY2 += rect.top;
                                    drawingObject.toW2 = rect.width();
                                    drawingObject.toH2 = rect.height();
                                }
                                break;
                            }
                        }
                    }
                }

                float fraction = (System.currentTimeMillis() - drawingObject.startTime) / (drawingObject.phase == 0 ? 220f : drawingObject.phase == 1 ? 750f : 220f);

                if (fraction >= 0.999) {
                    if (drawingObject.phase == 0) {
                        drawingObject.phase = 1;
                        drawingObject.imageReceiver.setAllowStartLottieAnimation(true);
                        drawingObject.imageReceiver.startAnimation();
                        drawingObject.imageReceiverEffects.setAllowStartLottieAnimation(true);
                        //drawingObject.imageReceiverEffects.getLottieAnimation().setCurrentFrame(0);
                        drawingObject.imageReceiverEffects.startAnimation();
                        drawingObject.startTime = System.currentTimeMillis();
                    } else if (drawingObject.phase == 1) {
                        if (drawingObject.imageReceiver.getLottieAnimation() != null && drawingObject.imageReceiver.getLottieAnimation().getCurrentFrame() == drawingObject.imageReceiver.getLottieAnimation().getFramesCount() - 2) {
                            drawingObject.startTime = System.currentTimeMillis();
                            drawingObject.phase = 2;
                        }
                    } else if (drawingObject.phase == 2 ) {
                        drawingObject.viewDelivered = true;
                        boolean shouldWaitForEffects = drawingObject.imageReceiverEffects.getLottieAnimation() != null && drawingObject.imageReceiverEffects.getLottieAnimation().isRunning();
                        if (!shouldWaitForEffects || drawingObject.imageReceiverEffects.getLottieAnimation().getCurrentFrame() == drawingObject.imageReceiverEffects.getLottieAnimation().getFramesCount() - 2) {
                            drawingObject.phase = -1;
                            drawingObject.wasPlayed = true;
                        }
                        //TODO notify target message view about finished transition
                    }
                }
                if (drawingObject.phase == 0) {
                    drawingObject.lastX = getValue(drawingObject.fromX, drawingObject.toX1, fraction);
                    drawingObject.lastY = getValue(drawingObject.fromY, drawingObject.toY1, fraction);
                    drawingObject.lastW = getValue(drawingObject.fromW, drawingObject.toW1, fraction);
                    drawingObject.lastH = getValue(drawingObject.fromH, drawingObject.toH1, fraction);
                } else if (drawingObject.phase == 1) {
                    // wait for animation is done
                } else if (drawingObject.phase == 2) {
                    drawingObject.lastX = getValue(drawingObject.toX1, drawingObject.toX2, fraction);
                    drawingObject.lastY = getValue(drawingObject.toY1, drawingObject.toY2, fraction);
                    drawingObject.lastW = getValue(drawingObject.toW1, drawingObject.toW2, fraction);
                    drawingObject.lastH = getValue(drawingObject.toH1, drawingObject.toH2, fraction);
                }

                drawingObject.imageReceiver.setImageCoords(drawingObject.lastX, drawingObject.lastY, drawingObject.lastW, drawingObject.lastH);
                if (drawingObject.phase >= 1) {
                    drawingObject.imageReceiverEffects.setImageCoords(
                            AndroidUtilities.dp(-50),
                            drawingObject.toY1 - AndroidUtilities.dp(40),
                            AndroidUtilities.getRealScreenSize().x / 2f + AndroidUtilities.dp(130),
                            AndroidUtilities.getRealScreenSize().x / 2f + AndroidUtilities.dp(130)
                    );
                }

                if (drawingObject.wasPlayed) {
                    drawingObjects.remove(i);
                    i--;
                } else {
                    if (!drawingObject.viewDelivered) {
                        drawingObject.imageReceiver.draw(canvas);
                    }
                    if (drawingObject.phase >= 1) {
                        drawingObject.imageReceiverEffects.draw(canvas);
                    }
                }
            }
            contentLayout.invalidate();
        }
    }

    private static int getValue(int start, int end, float f) {
        return Math.round(start * (1 - f) + end * f);
    }

    private static float getValue(float start, float end, float f) {
        return start * (1 - f) + end * f;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.diceStickersDidLoad) {
            String name = (String) args[0];
            if (INTERACTIONS_STICKER_PACK.equals(name)) {
                checkStickerPack();
            }
        }
    }

    public boolean showAnimationForCell(ChatMessageCell view, TLRPC.TL_availableReaction reaction, RectF from, RectF to) {
        float imageH = from.height();
        float imageW = from.width();

        TLRPC.Document docSticker = reaction.activate_animation;
        TLRPC.Document docEffects = reaction.effect_animation;
        if (docSticker != null) {

            int sameAnimationsCount = 0;
            for (int i = 0; i < drawingObjects.size(); i++) {
                if (drawingObjects.get(i).messageId == view.getMessageObject().getId()) {
                    sameAnimationsCount++;
                    if (drawingObjects.get(i).imageReceiverEffects.getLottieAnimation() == null || drawingObjects.get(i).imageReceiverEffects.getLottieAnimation().isGeneratingCache()) {
                        return false;
                    }
                }
            }
            if (sameAnimationsCount >= 2) {
                return false;
            }

            DrawingObject drawingObject = new DrawingObject();
            drawingObject.reaction = reaction.reaction;
            drawingObject.messageId = view.getMessageObject().getId();
            drawingObject.document = docSticker;
            drawingObject.documentEffects = docEffects;
            drawingObject.lastX = from.left;
            drawingObject.lastY = from.top;
            drawingObject.toX1 = to.left;
            drawingObject.toY1 = to.top;
            drawingObject.toW1 = to.width();
            drawingObject.toH1 = to.height();
            drawingObject.lastW = from.width();
            drawingObject.lastH = from.height();
            drawingObject.fromX = from.left;
            drawingObject.fromY = from.top;
            drawingObject.fromW = from.width();
            drawingObject.fromH = from.height();
            drawingObject.isOut = view.getMessageObject().isOutOwner();
            drawingObject.startTime = System.currentTimeMillis();
            float viewX = listView.getX() + view.getX();
            float viewY = listView.getY() + view.getY();
            drawingObject.toX2 = viewX;
            drawingObject.toY2 = viewY;
            drawingObject.toW2 = AndroidUtilities.dp2(10);
            drawingObject.toH2 = AndroidUtilities.dp2(10);

            ImageLocation imageLocation = ImageLocation.getForDocument(docSticker);

            int w = (int) (4f * imageW / AndroidUtilities.density);
            int minW = (int) (AndroidUtilities.getRealScreenSize().x / 3f / AndroidUtilities.density);
            if (w < minW) w = minW;
            int maxW = 250;
            if (w > maxW) w = maxW;

            drawingObject.imageReceiver.setImage(imageLocation, w + "_" + w + "_pcache", null, "tgs", null, 1);
            drawingObject.imageReceiver.setLayerNum(Integer.MAX_VALUE);

            ArrayList<TLRPC.Document> arrayList = emojiInteractionsStickersMap.get(reaction.reaction);
            TLRPC.Document document = null;
            boolean fromEff = false;
            if (arrayList != null && !arrayList.isEmpty()) {
                int animation = Math.abs(random.nextInt()) % arrayList.size();
                document = arrayList.get(animation);
            } else {
                fromEff = true;
                document = docEffects;
            }

            ImageLocation imageLocationEffects = ImageLocation.getForDocument(document);
            drawingObject.imageReceiverEffects.setUniqKeyPrefix(index + "_" + drawingObject.messageId + "_");
            index++;
            drawingObject.imageReceiverEffects.setImage(imageLocationEffects, w + "_" + w + (fromEff ? "" : "_pcache"), null, "tgs", set, 1);
            drawingObject.imageReceiverEffects.setLayerNum(Integer.MAX_VALUE);
            drawingObject.imageReceiverEffects.setAutoRepeat(2);

            drawingObjects.add(drawingObject);
            drawingObject.imageReceiver.onAttachedToWindow();
            drawingObject.imageReceiver.setParentView(contentLayout);
            drawingObject.imageReceiverEffects.onAttachedToWindow();
            drawingObject.imageReceiverEffects.setParentView(contentLayout);
            contentLayout.invalidate();
            return true;
        }

        return false;
    }

    private static class DrawingObject {
        public float lastX;
        public float lastY;
        public float fromX;
        public float fromY;
        public float fromW;
        public float fromH;
        public float toX1;
        public float toX2;
        public float toW1;
        public float toW2;
        public float toH1;
        public float toH2;
        public float toY1;
        public float toY2;
        public long startTime;
        public boolean viewFound;
        public float lastW;
        public float lastH;
        boolean wasPlayed;
        boolean viewDelivered;
        boolean isOut;
        int phase;
        int messageId;
        String reaction;
        public boolean reactionLocationFound;
        TLRPC.Document document;
        TLRPC.Document documentEffects;
        ImageReceiver imageReceiver = new ImageReceiver();
        ImageReceiver imageReceiverEffects = new ImageReceiver();
    }
}
