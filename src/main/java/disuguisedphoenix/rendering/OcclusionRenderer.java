package disuguisedphoenix.rendering;

import graphics.core.objects.FrameBufferObject;
import graphics.core.renderer.MultiIndirectRenderer;
import graphics.occlusion.SSAOEffect;
import graphics.occlusion.ShadowEffect;
import graphics.postprocessing.QuadRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class OcclusionRenderer {

    protected ShadowEffect shadowEffect;
    protected SSAOEffect ssaoEffect;

    public OcclusionRenderer(QuadRenderer quadRenderer, int width, int height, Matrix4f projMatrix) {
        ssaoEffect = new SSAOEffect(quadRenderer, width, height, projMatrix);
        ssaoEffect.disable();
        shadowEffect = new ShadowEffect();
        shadowEffect.disable();
    }

    public void render(FrameBufferObject gBuffer,Matrix4f projMatrix,Matrix4f viewMatrix, float nearPlane, float farPlane, float fov, float aspectRatio, float time, Vector3f lightPos, MultiIndirectRenderer multiRenderer){
        shadowEffect.render(viewMatrix, nearPlane, farPlane, (float) Math.toRadians(fov), aspectRatio, time, lightPos, multiRenderer);
        ssaoEffect.renderEffect(gBuffer, projMatrix,farPlane);
    }

}
