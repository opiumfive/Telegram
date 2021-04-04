package org.telegram.ui.Components.MessageAnimations.FourGradientBackground.OrientationSmooth;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.Choreographer;

import com.google.android.exoplayer2.util.Log;
import org.telegram.messenger.ApplicationLoader;

public class TiltProvider {

    private CalibratedGyroscopeProvider orientationProvider;
    private Quaternion quaternion = new Quaternion();
    private TiltListener tiltListener;
    private GetTiltDaemon daemon;

    public interface TiltListener {
        void sync(double angle1, double angle2);
    }

    public TiltProvider() {
        SensorManager sensorManager = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            return;
        } else {
            HardwareChecker checker = new HardwareChecker(sensorManager);
            if(!checker.IsGyroscopeAvailable()) return;
        }
        this.orientationProvider = new CalibratedGyroscopeProvider(sensorManager);
    }

    public void start(TiltListener tiltListener) {
        if (orientationProvider != null) {
            orientationProvider.start();
            this.tiltListener = tiltListener;
            startThread();
        }
    }

    public void resetAngle() {
        if (orientationProvider != null) {
            orientationProvider.reset();
        }
    }

    public void stop() {
        if (orientationProvider != null) {
            orientationProvider.stop();
            tiltListener = null;
            stopThread();
        }
    }

    private void startThread() {
        daemon = new GetTiltDaemon(this, tiltListener);
        daemon.start();
    }

    private void stopThread() {
        if (daemon != null && daemon.running) {
            daemon.stopThread();
            daemon.interrupt();
            daemon = null;
        }
    }

    public double[] getRotation() {
        if (orientationProvider != null) {
            orientationProvider.getQuaternion(quaternion);
            double[] angles = quaternion.toEulerAngles();
            return angles;
        }
        return null;
    }

    static class GetTiltDaemon extends Thread {

        private boolean running = true;
        private TiltProvider tiltProvider;
        private TiltListener tiltListener;
        private long time = 0;
        private long lastResetTime = 0;

        public GetTiltDaemon(TiltProvider tiltProvider, TiltListener tiltListener) {
            this.tiltProvider = tiltProvider;
            this.tiltListener = tiltListener;
        }

        @Override
        public void run() {
            while (running) {
                time = System.nanoTime();
                if (lastResetTime == 0) lastResetTime = System.nanoTime();
                if (tiltListener == null || tiltProvider == null) {
                    running = false;
                } else {
                    double[] angles = tiltProvider.getRotation();
                    if (angles == null) continue;
                    double tilt1 = Math.toDegrees(angles[0]);
                    double tilt2 = Math.toDegrees(angles[2]);
                    tiltListener.sync(tilt1, tilt2);
                }
                while(running && System.nanoTime() - time < 16000000); // 60hz
            }
        }

        void stopThread() {
            running = false;
        }
    }
}
