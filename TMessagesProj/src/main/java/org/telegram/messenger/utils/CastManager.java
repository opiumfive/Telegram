package org.telegram.messenger.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;
import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.common.util.concurrent.MoreExecutors;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.secretmedia.ExtendedDefaultDataSourceFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import fi.iki.elonen.NanoHTTPD;


public class CastManager implements CastPlayer.Listener, SessionAvailabilityListener {

    private CastContext castContext;
    private CastPlayer castPlayer;
    private ArrayList<MediaItem> mediaQueue;
    private FileLocalServer currentServer;
    private boolean canPushImmediately;

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
        mediaQueue.clear();
        mediaQueue.add(mediaItem);
        castPlayer.setMediaItems(mediaQueue, true);
        if (canPushImmediately) castPlayer.setPlayWhenReady(true);
    }

    private void startFileServer(Uri uri, String mimetype) {
        try {
            if (currentServer != null) {
                try {
                    currentServer.stop();
                    android.util.Log.d("CastManager", "stopped prev server");
                } catch (Exception e) {
                    android.util.Log.d("CastManager", "stop prev server failed " + e);
                }
            }
            currentServer = new FileLocalServer(uri, mimetype);
            currentServer.start();
            android.util.Log.d("CastManager", "Server started for file " + uri.toString());
        } catch (IOException ioe) {
            android.util.Log.d("CastManager", "Couldn start server " + ioe);
        }
    }

    @SuppressLint("DefaultLocale")
    private String getLocalWifiIpAddress() {
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
        canPushImmediately = true;
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        android.util.Log.d("CastManager", "error " + error);
    }

    @Override
    public void onCastSessionUnavailable() {
        canPushImmediately = false;
        android.util.Log.d("CastManager", "onCastSessionUnavailable");
    }

    private static class FileLocalServer extends NanoHTTPD {

        public static final String EXTENDED_DEFAULT_DATA_SOURCE_FACTORY_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)";
        private static final String STREAM_START_URL = "data:application/x-mpegurl;base64,";
        private static final String STREAM_MIME_TYPE = "application/x-mpegURL";

        private final Uri uri;
        private final String mimetype;
        private final DataSource.Factory mediaDataSourceFactory;
        private final DataSource.Factory fileDataSourceFactory;

        public FileLocalServer(Uri uri, String mimetype) {
            super(8080);
            this.uri = uri;
            this.mimetype = mimetype;
            fileDataSourceFactory = new FileDataSource.Factory();
            mediaDataSourceFactory = new ExtendedDefaultDataSourceFactory(ApplicationLoader.applicationContext, EXTENDED_DEFAULT_DATA_SOURCE_FACTORY_USER_AGENT);
        }

        @Override
        public Response serve(NanoHTTPD.IHTTPSession session) {
            try {
                if (uri.toString().startsWith(STREAM_START_URL)) {
                    final byte[] bytes = Base64.decode(uri.toString().substring(STREAM_START_URL.length()), Base64.DEFAULT);
                    return newFixedLengthResponse(Response.Status.OK, mimetype, new String(bytes));
                }
                final DataSource source;
                if (uri.toString().startsWith("file://")) {
                    source = fileDataSourceFactory.createDataSource();
                } else {
                    source = mediaDataSourceFactory.createDataSource();
                }
                final DataSpec.Builder dataSpecBuilder = new DataSpec.Builder().setUri(uri);
                final long sourceSize = source.open(dataSpecBuilder.build());
                source.close();
                final boolean stream = STREAM_MIME_TYPE.equals(mimetype);
                final Pair<Long, Long> scope;
                if (stream) {
                    scope = null;
                } else {
                    String rangeHeader = session.getHeaders().get("range");
                    if (TextUtils.isEmpty(rangeHeader)) {
                        scope = null;
                    } else {
                        long from, to;
                        String rangeValue = rangeHeader.trim().substring("bytes=".length());
                        if (rangeValue.startsWith("-")) {
                            to = sourceSize - 1;
                            from = sourceSize - Long.parseLong(rangeValue.substring("-".length())) - 1;
                        } else {
                            String[] splittedScope = rangeValue.split("-");
                            from = Long.parseLong(splittedScope[0]);
                            to = splittedScope.length > 1 ? Long.parseLong(splittedScope[1]) : sourceSize - 1;
                        }
                        if (to > sourceSize - 1) to = sourceSize - 1;
                        scope = new Pair<>(from, to);
                    }
                }
                long size = (scope != null) ? (scope.second - scope.first + 1) : sourceSize;
                if (scope != null) {
                    dataSpecBuilder.setPosition(scope.first);
                    dataSpecBuilder.setLength(size);
                }
                if (stream) {
                    final byte[] readBuffer = new byte[(int) size];
                    source.open(dataSpecBuilder.build());
                    source.read(readBuffer, 0, (int) size);
                    source.close();
                    return newFixedLengthResponse(Response.Status.OK, mimetype, new String(readBuffer));
                }
                Response response;
                if (size != 0) {
                    final InputStream dataInputStream = new DataSourceInputStream(source, dataSpecBuilder.build());
                    response = newFixedLengthResponse(scope != null ? Response.Status.PARTIAL_CONTENT : Response.Status.OK, mimetype, dataInputStream, size);
                } else {
                    response = newFixedLengthResponse(Response.Status.NO_CONTENT, mimetype, "");
                }
                if (scope != null) {
                    response.addHeader("Content-Range", "bytes " + scope.first + "-" + scope.second + "/" + sourceSize);
                }
                response.addHeader("Access-Control-Allow-Origin", "*");
                response.addHeader("Access-Control-Allow-Methods", "*");
                response.addHeader("Access-Control-Allow-Headers", "*");
                response.addHeader("Access-Control-Max-Age", "3600");
                return response;
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error casting a file");
            }
        }
    }
}