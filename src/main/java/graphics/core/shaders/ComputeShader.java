package graphics.core.shaders;

import graphics.core.objects.BufferObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL43.*;

public class ComputeShader {
    private static final FloatBuffer matrixBuffer4f = BufferUtils.createFloatBuffer(16);
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final Map<String, Integer> buffers = new HashMap<>();

    private final int shaderId;

    public ComputeShader(String shaderCode) {
        shaderId = GL20.glCreateProgram();
        int computeShader = GL20.glCreateShader(GL_COMPUTE_SHADER);
        GL20.glShaderSource(computeShader, shaderCode);
        GL20.glAttachShader(shaderId, computeShader);
        GL20.glLinkProgram(shaderId);
        if (GL20.glGetProgrami(shaderId, GL_LINK_STATUS) == GL_FALSE) {
            System.out.println(GL20.glGetProgramInfoLog(shaderId, 500));
            System.err.println("Could not link compute Shader.");
            System.exit(-1);
        }
        //shader is built and linked we can cleanup
        GL20.glDetachShader(shaderId, computeShader);
        GL20.glDeleteShader(computeShader);
    }

    public void loadUniforms(String... uniformNames) {
        for (String uniformName : uniformNames) {
            loadUniform(uniformName);
        }
    }

    public void loadUniform(String name) {
        int id = GL20.glGetUniformLocation(shaderId, name);
        uniforms.put(name, id);
        if (id == -1) {
            System.err.println("Uniform: " + name + " not found!");
        }
    }

    public void bind() {
        GL20.glUseProgram(shaderId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void loadImage(int unit, int texture, int access, int format) {
        glBindImageTexture(unit, texture, 0, false, 0, access, format);
    }

    public void connectSampler(String samplerName, int unit) {
        GL20.glUniform1i(uniforms.get(samplerName), unit);
    }

    public void dispatch(int x, int y, int z) {
        if(x==0||y==0||z==0)
            System.err.println("DON'T PUT 0 in Compute Shader dispatch");
        glDispatchCompute(x, y, z);
    }

    public void setImageAccessBarrier() {
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    public void setSSBOAccessBarrier() {
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    public void loadMatrix4f(String name, Matrix4f matrix) {
        matrixBuffer4f.clear();
        matrix.get(matrixBuffer4f);
        GL20.glUniformMatrix4fv(uniforms.get(name), false, matrixBuffer4f);
    }

    public void loadFloat(String name, float value) {
        GL20.glUniform1f(uniforms.get(name), value);
    }

    public void loadInt(String name, int value) {
        GL20.glUniform1i(uniforms.get(name), value);
    }


    public void loadVec4(String name, Vector4f value) {
        GL20.glUniform4f(uniforms.get(name), value.x,value.y,value.z,value.w);
    }

    public void loadBufferResource(String name, int bindingPoint){
       int index =  GL43.glGetProgramResourceIndex(shaderId,GL_SHADER_STORAGE_BLOCK,name);
       if(index==GL_INVALID_INDEX){
           System.err.println("ERROR: Buffer Resource "+name +" not found");
           System.exit(1);
       }
       GL43.glShaderStorageBlockBinding(shaderId,index,bindingPoint);
       buffers.put(name,bindingPoint);
    }

    public void bindBuffer(String name, BufferObject buffer){
        GL43.glBindBufferBase(buffer.getTarget(),buffers.get(name),buffer.getBufferID());
    }

}
