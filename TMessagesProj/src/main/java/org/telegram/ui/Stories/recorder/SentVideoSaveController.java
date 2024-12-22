package org.telegram.ui.Stories.recorder;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.Iterator;

public class SentVideoSaveController implements NotificationCenter.NotificationCenterDelegate {

    private static volatile SentVideoSaveController INSTANCE = null;

    private final ArrayList<String> set = new ArrayList<>();


    private SentVideoSaveController() {
    }

    public static SentVideoSaveController getInstance() {
        if (INSTANCE == null) { // Check 1
            synchronized (SentVideoSaveController.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SentVideoSaveController();
                }
            }
        }
        return INSTANCE;
    }

    public void startedUploading(String tempLocalPath) {
        set.add(tempLocalPath);
        sub();
    }

    private void sub() {
        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.fileNewChunkAvailable);
        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.filePreparingFailed);
    }

    private void unSub() {
        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.fileNewChunkAvailable);
        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.filePreparingFailed);
    }

    private boolean containsAndRemove(String input) {
        Iterator<String> iterator = set.iterator();
        while (iterator.hasNext()) {
            String item = iterator.next();
            if (input.contains(item)) {
                iterator.remove();
                if (set.isEmpty()) unSub();
                return true;
            }
        }
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (id == NotificationCenter.fileNewChunkAvailable) {
                long finalSize = (Long) args[3];
                if (finalSize > 0) {
                    String path = ((MessageObject) args[0]).messageOwner.params.get("originalPath");
                    if (containsAndRemove(path)) {
                        MediaController.saveFile(((MessageObject) args[0]).messageOwner.attachPath, ApplicationLoader.applicationContext, 1, null, null, uri -> {}, false);
                    }
                }
            } else if (id == NotificationCenter.filePreparingFailed) {
                String path = ((MessageObject) args[0]).messageOwner.params.get("originalPath");
                containsAndRemove(path);
            }
        } catch (Exception e) {}
    }
}