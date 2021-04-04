package org.telegram.ui.Components.MessageAnimations.FourGradientBackground.OrientationSmooth;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.List;

// Kalman filtered rotation vector by gyroscope and quaternions
public class CalibratedGyroscopeProvider implements SensorEventListener {

    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final double EPSILON = 0.1f;

    private final Quaternion deltaQuaternion = new Quaternion();
    private long timestamp;
    private double gyroscopeRotationVelocity = 0;

    
    private Quaternion correctedQuaternion = new Quaternion();
    protected final Object synchronizationToken = new Object();
    protected List<Sensor> sensorList = new ArrayList<Sensor>();
    protected final MatrixF4x4 currentOrientationRotationMatrix;
    protected final Quaternion currentOrientationQuaternion;
    protected SensorManager sensorManager;
    
    public CalibratedGyroscopeProvider(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
        currentOrientationRotationMatrix = new MatrixF4x4();
        currentOrientationQuaternion = new Quaternion();
        sensorList.add(sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
    }

    public void start() {
        for (Sensor sensor : sensorList) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stop() {
        for (Sensor sensor : sensorList) {
            sensorManager.unregisterListener(this, sensor);
        }
    }

    public void reset() {
        Matrix.setIdentityM(currentOrientationRotationMatrix.matrix, 0);
        currentOrientationQuaternion.setX(0);
        currentOrientationQuaternion.setY(0);
        currentOrientationQuaternion.setZ(0);
        currentOrientationQuaternion.setW(1);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    
    public void getRotationMatrix(MatrixF4x4 matrix) {
        synchronized (synchronizationToken) {
            matrix.set(currentOrientationRotationMatrix);
        }
    }

    
    public void getQuaternion(Quaternion quaternion) {
        synchronized (synchronizationToken) {
            quaternion.set(currentOrientationQuaternion);
        }
    }

    
    public void getEulerAngles(float angles[]) {
        synchronized (synchronizationToken) {
            SensorManager.getOrientation(currentOrientationRotationMatrix.matrix, angles);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if (timestamp != 0) {
                final float dT = (event.timestamp - timestamp) * NS2S;
                float axisX = event.values[0];
                float axisY = event.values[1];
                float axisZ = event.values[2];
                gyroscopeRotationVelocity = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

                if (gyroscopeRotationVelocity > EPSILON) {
                    axisX /= gyroscopeRotationVelocity;
                    axisY /= gyroscopeRotationVelocity;
                    axisZ /= gyroscopeRotationVelocity;
                }

                double thetaOverTwo = gyroscopeRotationVelocity * dT / 2.0f;
                double sinThetaOverTwo = Math.sin(thetaOverTwo);
                double cosThetaOverTwo = Math.cos(thetaOverTwo);
                deltaQuaternion.setX((float) (sinThetaOverTwo * axisX));
                deltaQuaternion.setY((float) (sinThetaOverTwo * axisY));
                deltaQuaternion.setZ((float) (sinThetaOverTwo * axisZ));
                deltaQuaternion.setW(-(float) cosThetaOverTwo);

                synchronized (synchronizationToken) {
                    deltaQuaternion.multiplyByQuat(currentOrientationQuaternion, currentOrientationQuaternion);
                }

                correctedQuaternion.set(currentOrientationQuaternion);
                correctedQuaternion.w(-correctedQuaternion.w());

                synchronized (synchronizationToken) {
                    SensorManager.getRotationMatrixFromVector(currentOrientationRotationMatrix.matrix, correctedQuaternion.array());
                }
            }
            timestamp = event.timestamp;
        }
    }
}
