package org.telegram.ui.Components.MessageAnimations.FourGradientBackground;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glUniform1f;
import static android.opengl.GLES20.glUniform2fv;
import static android.opengl.GLES20.glUniform4fv;
import static android.opengl.GLES20.glUniformMatrix4fv;

public class Shader {

    final int id;

    Shader(int vsResourceID, int fsResourceID, Context context) {

        int vs = compileShader(GL_VERTEX_SHADER, readShader(vsResourceID, context));
        int fs = compileShader(GL_FRAGMENT_SHADER, readShader(fsResourceID, context));
        id = glCreateProgram();
        glAttachShader(id, vs);
        glAttachShader(id, fs);
        glLinkProgram(id);
        glDeleteProgram(vs);
        glDeleteProgram(fs);
    }

    private String readShader(int resourceID, Context context) {

        InputStream rawResource = context.getResources().openRawResource(resourceID);
        BufferedReader reader = new BufferedReader(new InputStreamReader(rawResource));
        StringBuilder builder = new StringBuilder();
        String line;

        try {
            while ((line = reader.readLine()) != null) builder.append(line + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            rawResource.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return builder.toString();
    }

    private int compileShader(int type, String shaderCode) {

        int shader = glCreateShader(type);
        glShaderSource(shader, shaderCode);
        glCompileShader(shader);
        return shader;
    }

    int getAttributeLocation(String name) {
        return glGetAttribLocation(id, name);
    }

    int getUniformLocation(String name) {
        return glGetUniformLocation(id, name);
    }

    void setUniformM(String name, float[] value) {
        glUniformMatrix4fv(glGetUniformLocation(id, name), 1, false, value, 0);
    }

    void setUniform1f(int loc, float value) {
        glUniform1f(loc, value);
    }

    void setUniform2fv(int loc, float[] value) {
        glUniform2fv(loc, 1, value, 0);
    }

    void setUniform4fv(int loc, float[] value) {
        glUniform4fv(loc, 1, value, 0);
    }

}