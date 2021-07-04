package graphics.postprocessing;

import disuguisedphoenix.Main;
import graphics.objects.Vao;
import graphics.occlusion.ShadowEffect;
import graphics.shaders.Shader;
import graphics.shaders.ShaderFactory;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;

public class QuadRenderer {

    Vao quad;
    Shader shader;
    Shader testShader;

    public QuadRenderer() {
        quad = new Vao();
        quad.addDataAttributes(0, 3, new float[]{-1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                -1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f,
        });
        quad.unbind();
        ShaderFactory gResolveFactory = new ShaderFactory("postProcessing/quadVS.glsl", "postProcessing/deferred/lightingPassFS.glsl").withAttributes("pos");
        gResolveFactory.withUniforms("depthTexture", "shadowMapTexture","zFar","normalAndSpecularTexture", "colorAndGeometryCheckTexture", "ambientOcclusionTexture", "projMatrixInv", "lightPos","lightColor", "ssaoEnabled","shadowsEnabled");
        gResolveFactory.withUniformArray("shadowReprojectionMatrix",4);
        gResolveFactory.withUniformArray("splitRange",4);
        gResolveFactory.configureSampler("depthTexture", 0).configureSampler("normalAndSpecularTexture", 1).
                configureSampler("colorAndGeometryCheckTexture", 2).configureSampler("ambientOcclusionTexture", 3).configureSampler("shadowMapTexture",4);
        shader = gResolveFactory.built();
        testShader = new ShaderFactory("postProcessing/quadVS.glsl","textureTestFS.glsl")
                .withAttributes("pos").withUniforms("toTest").configureSampler("toTest",0).built();
    }


    public void renderDeferredLightingPass(Matrix4f viewMatrix, Matrix4f projMatrix, Vector3f lightPos,Vector3f lightColor, boolean ssaoIsEnabled,boolean shadowsIsEnabled,Matrix4f[] shadowReproject) {
        shader.bind();
        shader.loadInt("ssaoEnabled", ssaoIsEnabled ? 1 : 0);
        shader.loadInt("shadowsEnabled", shadowsIsEnabled ? 1 : 0);
        shader.load3DVector("lightPos", viewMatrix.transformPosition(new Vector3f(lightPos)));
        shader.load3DVector("lightColor",lightColor);
        shader.loadFloat("zFar",Main.FAR_PLANE);
        shader.loadMatrix("projMatrixInv", new Matrix4f(projMatrix).invert());
        shader.loadFloatArray("splitRange", ShadowEffect.CASCADE_DISTANCE);
        Matrix4f[] shadowReporjectionMatrix = new Matrix4f[shadowReproject.length];
        for(int i=0;i<shadowReporjectionMatrix.length;i++) {
           shadowReporjectionMatrix[i]=new Matrix4f (shadowReproject[i]).mul(new Matrix4f(viewMatrix).invert());
        }
        shader.loadMatrix4fArray("shadowReprojectionMatrix",shadowReporjectionMatrix);
        renderOnlyQuad();
        shader.unbind();
    }

    public void renderTextureToScreen(int texture) {
        testShader.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, texture);
        renderOnlyQuad();
        testShader.unbind();
    }

    public void renderOnlyQuad() {
        quad.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        Main.drawCalls++;
        quad.unbind();
    }

}
