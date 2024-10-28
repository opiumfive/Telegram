package org.telegram.messenger.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.common.util.concurrent.MoreExecutors;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import fi.iki.elonen.NanoHTTPD;


public class CastManager implements CastPlayer.Listener, SessionAvailabilityListener {

    private CastContext castContext;
    private CastPlayer castPlayer;
    private ArrayList<MediaItem> mediaQueue;
    private FileLocalServer currentServer;

    private static class FileLocalServer extends NanoHTTPD {

        private final File file;
        private final String mimetype;

        public FileLocalServer(File file, String mimetype) {
            super(8080);
            this.file = file;
            this.mimetype = mimetype;
        }

        @Override
        public Response serve(NanoHTTPD.IHTTPSession session) {
            try {
                FileInputStream fis = new FileInputStream(file);
                return newFixedLengthResponse(Response.Status.OK, mimetype, fis, file.length());
            } catch (IOException e) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
            }
        }
    }

    public CastManager(Context context) {

        try {
            castContext = CastContext.getSharedInstance(context, MoreExecutors.directExecutor()).getResult();
        } catch (RuntimeException e) {

        }
        castContext.addCastStateListener(i -> {
            switch (i) {
                case CastState.NOT_CONNECTED:
                    android.util.Log.d("CastManager", "cast state listener NOT_CONNECTED");
                    break;
                case CastState.CONNECTED:
                    android.util.Log.d("CastManager", "cast state listener CONNECTED");
                    break;
                case CastState.CONNECTING:
                    android.util.Log.d("CastManager", "cast state listener CONNECTING");
                    break;
                case CastState.NO_DEVICES_AVAILABLE:
                    android.util.Log.d("CastManager", "cast state listener NO_DEVICES_AVAILABLE");
                    break;
            }
        });

        castPlayer = new CastPlayer(castContext);
        castPlayer.addListener(this);
        castPlayer.setSessionAvailabilityListener(this);
        mediaQueue = new ArrayList<>();
    }

    public void postUri(Uri uri, String mimetype) {
        android.util.Log.d("CastManager", "postUri " + uri.toString());

        startFileServer(uri, mimetype);
        Uri newUri = Uri.parse(getLocalWifiIpAddress() + ":8080/" + uri.getLastPathSegment());
        String link = "http://" + getLocalWifiIpAddress() + ":8080/" + uri.getLastPathSegment();
        android.util.Log.d("CastManager", "newUri " + newUri.toString());
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(link)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle("Video from Telegram").build())
                .setMimeType(mimetype)
                .build();
        mediaQueue.add(mediaItem);
        castPlayer.addMediaItem(mediaItem);
    }

    private void startFileServer(Uri uri, String mimetype) {
        File file = new File(uri.getPath());
        try {
            if (currentServer != null) {
                try {
                    currentServer.stop();
                    android.util.Log.d("CastManager", "stopped prev server");
                } catch (Exception e) {
                    android.util.Log.d("CastManager", "stop prev server failed " + e);
                }
            }
            currentServer = new FileLocalServer(file, mimetype);
            currentServer.start();
            android.util.Log.d("CastManager", "Server started for file " + file.getAbsolutePath());
        } catch (IOException ioe) {
            android.util.Log.d("CastManager", "Couldn start server " + ioe);
        }
    }

    @SuppressLint("DefaultLocale")
    public String getLocalWifiIpAddress() {
        WifiManager wifiManager = (WifiManager) ApplicationLoader.applicationContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            return String.format("%d.%d.%d.%d",
                    (ipAddress & 0xff),
                    (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff),
                    (ipAddress >> 24 & 0xff));
        }
        return null;
    }

    @Override
    public void onCastSessionAvailable() {
        android.util.Log.d("CastManager", "onCastSessionAvailable prepare player");

        castPlayer.setMediaItems(mediaQueue, true);
        castPlayer.setPlayWhenReady(true);
        castPlayer.prepare();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        android.util.Log.d("CastManager", "error " + error);
    }

    @Override
    public void onCastSessionUnavailable() {
        android.util.Log.d("CastManager", "onCastSessionUnavailable");
    }
}
