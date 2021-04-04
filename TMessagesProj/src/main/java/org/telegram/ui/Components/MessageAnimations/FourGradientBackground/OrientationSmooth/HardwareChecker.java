package org.telegram.ui.Components.MessageAnimations.FourGradientBackground.OrientationSmooth;

import android.hardware.Sensor;
import android.hardware.SensorManager;

public class HardwareChecker {

    boolean gyroscopeIsAvailable = false;

    public HardwareChecker (SensorManager sensorManager) {
        if(sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).size() > 0) {
            gyroscopeIsAvailable = true;
        }
    }

    public boolean IsGyroscopeAvailable() {
        return gyroscopeIsAvailable;
    }

}
