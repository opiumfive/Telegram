package org.telegram.ui.Components.MessageAnimations.FourGradientBackground;

import android.graphics.Color;
import android.graphics.PointF;

import org.telegram.ui.Components.MessageAnimations.Editor.data.BackgroundGradientParams;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class Gradient4Points {

    private static final float KALMAN_FILTER_FACTOR = 0.1f;
    private static final float KALMAN_FILTER_NOISE_FACTOR = 5f;

    private final int width;
    private final int height;

    int positionAttributeLocation;
    int resolutionLoc;
    int randSeedLoc;
    int color1Loc;
    int color2Loc;
    int color3Loc;
    int color4Loc;
    int color1PosLoc;
    int color2PosLoc;
    int color3PosLoc;
    int color4PosLoc;
    float[] color1Pos= new float[2];
    float[] color2Pos= new float[2];
    float[] color3Pos= new float[2];
    float[] color4Pos= new float[2];
    float[] color1PosFrom= new float[2];
    float[] color2PosFrom= new float[2];
    float[] color3PosFrom= new float[2];
    float[] color4PosFrom= new float[2];
    float[] color1 = new float[4];
    float[] color2 = new float[4];
    float[] color3 = new float[4];
    float[] color4 = new float[4];

    FloatBuffer vertexBuffer;

    Shader shader;

    int keyShift = 0;
    boolean animatingState = false;

    private double color1xbuff = 0.5;
    private double color2xbuff = 0.5;
    private double color3xbuff = 0.5;
    private double color4xbuff = 0.5;
    private double color1ybuff = 0.5;
    private double color2ybuff = 0.5;
    private double color3ybuff = 0.5;
    private double color4ybuff = 0.5;
    private double filterFactor = KALMAN_FILTER_FACTOR;
    private double filterNoise = KALMAN_FILTER_NOISE_FACTOR;

    private final PointF rightPoint = new PointF(0.822f, 0.081f);
    private final PointF leftPoint = new PointF(0.354f, 0.247f);
    private final PointF[] points = new PointF[8];

    Gradient4Points(Shader shader, int width, int height) {
        this.shader = shader;
        this.width = width;
        this.height = height;
    }

    public void setup(Shader shader2) {
        if (shader == null) {
            shader = shader2;
        }
        positionAttributeLocation = shader.getAttributeLocation("a_position");

        resolutionLoc = shader.getUniformLocation("resolution");
        randSeedLoc = shader.getUniformLocation("randSeed");
        color1Loc = shader.getUniformLocation("color1");
        color2Loc = shader.getUniformLocation("color2");
        color3Loc = shader.getUniformLocation("color3");
        color4Loc = shader.getUniformLocation("color4");
        color1PosLoc = shader.getUniformLocation("color1Pos");
        color2PosLoc = shader.getUniformLocation("color2Pos");
        color3PosLoc = shader.getUniformLocation("color3Pos");
        color4PosLoc = shader.getUniformLocation("color4Pos");

        float[] vertices2 = {
                -1, -1,
                1, -1,
                -1, 1,
                -1, 1,
                1, -1,
                1, 1
        };

        ByteBuffer buffer = ByteBuffer.allocateDirect(vertices2.length * 4);
        buffer.order(ByteOrder.nativeOrder());
        vertexBuffer = buffer.asFloatBuffer();
        vertexBuffer.put(vertices2);
        vertexBuffer.position(0);

        //updateTargetColors();

        points[0] = new PointF(rightPoint.x, rightPoint.y);
        points[2] = new PointF(leftPoint.x, leftPoint.y);
        points[4] = new PointF(1 - rightPoint.x, 1 - rightPoint.y);
        points[6] = new PointF(1 - leftPoint.x, 1 - leftPoint.y);

        points[1] = new PointF((points[0].x + points[2].x) / 2, (points[0].y + points[2].y) / 2);
        points[3] = new PointF((points[2].x + points[4].x) / 2, (points[2].y + points[4].y) / 2);
        points[5] = new PointF((points[4].x + points[6].x) / 2, (points[4].y + points[6].y) / 2);
        points[7] = new PointF((points[0].x + points[6].x) / 2, (points[0].y + points[6].y) / 2);

        color1Pos[0] = points[0].x;
        color1Pos[1] = points[0].y;
        color2Pos[0] = points[2].x;
        color2Pos[1] = points[2].y;
        color3Pos[0] = points[4].x;
        color3Pos[1] = points[4].y;
        color4Pos[0] = points[6].x;
        color4Pos[1] = points[6].y;

    }

    public void setData(BackgroundGradientParams backgroundGradientParams) {
        setColor1(backgroundGradientParams.color1);
        setColor2(backgroundGradientParams.color2);
        setColor3(backgroundGradientParams.color3);
        setColor4(backgroundGradientParams.color4);
        filterFactor = backgroundGradientParams.filterfactor;
    }

    private static void setColor(float[] data, int color) {
        data[0] = Color.red(color) / 255f;
        data[1] = Color.green(color) / 255f;
        data[2] = Color.blue(color) / 255f;
        data[3] = Color.alpha(color) / 255f;
    }

    private static float getValue(float start, float end, float f) {
        return start * (1 - f) + end * f;
    }

    public void setColor1(int value) {
        setColor(color1, value);
    }

    public void setColor2(int value) {
        setColor(color2, value);
    }

    public void setColor3(int value) {
        setColor(color3, value);
    }

    public void setColor4(int value) {
        setColor(color4, value);
    }

    void toggle2() {
        keyShift = (keyShift + 1) % 8;
    }

    public void updateColorsPosition(float value) {

        if (points[0] == null) return;

        if (value == 0.0) {
            double minDist = 2;
            int minDistKeyShift = keyShift;

            for (int i = 0; i < points.length; i++) {
                PointF point = points[i];
                double dist = Math.sqrt((color1Pos[0] - point.x) * (color1Pos[0] - point.x) + (color1Pos[1] - point.y) * (color1Pos[1] - point.y));
                if (dist < minDist) {
                    minDist = dist;
                    minDistKeyShift = i;
                }
            }

            keyShift = minDistKeyShift;
            color1PosFrom[0] = color1Pos[0];
            color1PosFrom[1] = color1Pos[1];
            color2PosFrom[0] = color2Pos[0];
            color2PosFrom[1] = color2Pos[1];
            color3PosFrom[0] = color3Pos[0];
            color3PosFrom[1] = color3Pos[1];
            color4PosFrom[0] = color4Pos[0];
            color4PosFrom[1] = color4Pos[1];
        }

        color1Pos[0] = getValue(color1PosFrom[0], points[(keyShift + 1) % 8].x, value);
        color1Pos[1] = getValue(color1PosFrom[1], points[(keyShift + 1) % 8].y, value);

        color2Pos[0] = getValue(color2PosFrom[0], points[(keyShift + 3) % 8].x, value);
        color2Pos[1] = getValue(color2PosFrom[1], points[(keyShift + 3) % 8].y, value);

        color3Pos[0] = getValue(color3PosFrom[0], points[(keyShift + 5) % 8].x, value);
        color3Pos[1] = getValue(color3PosFrom[1], points[(keyShift + 5) % 8].y, value);

        color4Pos[0] = getValue(color4PosFrom[0], points[(keyShift + 7) % 8].x, value);
        color4Pos[1] = getValue(color4PosFrom[1], points[(keyShift + 7) % 8].y, value);
    }

    float[] tmp1 = new float[2];
    float[] tmp2 = new float[2];
    float[] tmp3 = new float[2];
    float[] tmp4 = new float[2];

    static void setTemp(double a1, double a2, float[] tmp) {
        tmp[0] = (float) a1;
        tmp[1] = (float) a2;
    }

    void setAngles(double angle1, double angle2) {
        double oppoAngle1 = 0.5 + (angle1 - 0.5) * Math.cos(Math.PI / 2) - (angle2 - 0.5) * Math.sin(Math.PI / 2);
        double oppoAngle2 = 0.5 + (angle1 - 0.5) * Math.sin(Math.PI / 2) + (angle2 - 0.5) * Math.cos(Math.PI / 2);
        setTemp(oppoAngle1, oppoAngle2, tmp1);
        setTemp(angle1, angle2, tmp2);
        setTemp(1 - oppoAngle1, 1 - oppoAngle2, tmp3);
        setTemp(1 - angle1, 1 - angle2, tmp4);

        // apply kalman filter to prevent fast state change
        filterColor1Pos();
        filterColor2Pos();
        filterColor3Pos();
        filterColor4Pos();

        color1Pos[0] = tmp1[0];
        color1Pos[1] = tmp1[1];
        color2Pos[0] = tmp2[0];
        color2Pos[1] = tmp2[1];
        color3Pos[0] = tmp3[0];
        color3Pos[1] = tmp3[1];
        color4Pos[0] = tmp4[0];
        color4Pos[1] = tmp4[1];
        display();
    }

    void filterColor1Pos() {
        double targetColor1XPos = tmp1[0];
        double zap = targetColor1XPos;
        double targetColor1YPos = tmp1[1];
        double currentColor1XPos = color1Pos[0];
        double currentColor1YPos = color1Pos[1];

        double color1XFilterPrediction = color1xbuff + filterFactor;
        double factor = color1XFilterPrediction / (color1XFilterPrediction + filterNoise);
        targetColor1XPos = currentColor1XPos + factor * (targetColor1XPos - currentColor1XPos);
        color1xbuff = (1 - factor) * color1XFilterPrediction;

        double color1YFilterPrediction = color1ybuff + filterFactor;
        factor = color1YFilterPrediction / (color1YFilterPrediction + filterNoise);
        targetColor1YPos = currentColor1YPos + factor * (targetColor1YPos - currentColor1YPos);
        color1ybuff = (1 - factor) * color1YFilterPrediction;

        setTemp(targetColor1XPos, targetColor1YPos, tmp1);
    }

    void filterColor2Pos() {
        double targetColor2XPos = tmp2[0];
        double targetColor2YPos = tmp2[1];
        double currentColor2XPos = color2Pos[0];
        double currentColor2YPos = color2Pos[1];

        double color2XFilterPrediction = color2xbuff + filterFactor;
        double factor = color2XFilterPrediction / (color2XFilterPrediction + filterNoise);
        targetColor2XPos = currentColor2XPos + factor * (targetColor2XPos - currentColor2XPos);
        color2xbuff = (1 - factor) * color2XFilterPrediction;

        double color2YFilterPrediction = color2ybuff + filterFactor;
        factor = color2YFilterPrediction / (color2YFilterPrediction + filterNoise);
        targetColor2YPos = currentColor2YPos + factor * (targetColor2YPos - currentColor2YPos);
        color2ybuff = (1 - factor) * color2YFilterPrediction;

        setTemp(targetColor2XPos, targetColor2YPos, tmp2);
    }

    void filterColor3Pos() {
        double targetColor3XPos = tmp3[0];
        double targetColor3YPos = tmp3[1];
        double currentColor3XPos = color3Pos[0];
        double currentColor3YPos = color3Pos[1];

        double color3XFilterPrediction = color3xbuff + filterFactor;
        double factor = color3XFilterPrediction / (color3XFilterPrediction + filterNoise);
        targetColor3XPos = currentColor3XPos + factor * (targetColor3XPos - currentColor3XPos);
        color3xbuff = (1 - factor) * color3XFilterPrediction;

        double color3YFilterPrediction = color3ybuff + filterFactor;
        factor = color3YFilterPrediction / (color3YFilterPrediction + filterNoise);
        targetColor3YPos = currentColor3YPos + factor * (targetColor3YPos - currentColor3YPos);
        color3ybuff = (1 - factor) * color3YFilterPrediction;

        setTemp(targetColor3XPos, targetColor3YPos, tmp3);
    }

    void filterColor4Pos() {
        double targetColor4XPos = tmp4[0];
        double targetColor4YPos = tmp4[1];
        double currentColor4XPos = color4Pos[0];
        double currentColor4YPos = color4Pos[1];

        double color4XFilterPrediction = color4xbuff + filterFactor;
        double factor = color4XFilterPrediction / (color4XFilterPrediction + filterNoise);
        targetColor4XPos = currentColor4XPos + factor * (targetColor4XPos - currentColor4XPos);
        color4xbuff = (1 - factor) * color4XFilterPrediction;

        double color4YFilterPrediction = color4ybuff + filterFactor;
        factor = color4YFilterPrediction / (color4YFilterPrediction + filterNoise);
        targetColor4YPos = currentColor4YPos + factor * (targetColor4YPos - currentColor4YPos);
        color4ybuff = (1 - factor) * color4YFilterPrediction;

        setTemp(targetColor4XPos, targetColor4YPos, tmp4);
    }

    void display() {
        if (shader == null || vertexBuffer == null) return;
        glUseProgram(shader.id);

        glEnableVertexAttribArray(positionAttributeLocation);
        glVertexAttribPointer(positionAttributeLocation, 2, GL_FLOAT, false, 8, vertexBuffer);

        shader.setUniform1f(randSeedLoc, 1.0f);
        shader.setUniform2fv(resolutionLoc, new float[]{width, height});
        shader.setUniform4fv(color1Loc, color1);
        shader.setUniform4fv(color2Loc, color2);
        shader.setUniform4fv(color3Loc, color3);
        shader.setUniform4fv(color4Loc, color4);
        shader.setUniform2fv(color1PosLoc, color1Pos);
        shader.setUniform2fv(color2PosLoc, color2Pos);
        shader.setUniform2fv(color3PosLoc, color3Pos);
        shader.setUniform2fv(color4PosLoc, color4Pos);

        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisableVertexAttribArray(positionAttributeLocation);
    }
}