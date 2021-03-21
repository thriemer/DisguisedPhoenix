package graphics.postprocessing;

import disuguisedphoenix.Main;
import engine.util.Maths;
import graphics.camera.Camera;
import graphics.loader.TextureLoader;
import graphics.objects.FrameBufferObject;
import graphics.objects.TimerQuery;
import graphics.shaders.Shader;
import graphics.shaders.ShaderFactory;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

public class Atmosphere {

    private static final String ATMOSPHERE_RADIUS="atmosphereRadius";
    private static final String PLANET_RADIUS="planetRadius";
    private static final String DENSITY_FALLOFF="densityFalloff";


    private Shader atmosphereShader;
    private Shader lookupGenerator;
    private int lookupTableSize = 512;
    private FrameBufferObject lookUpTable;
    private QuadRenderer renderer;
    private Vector3f wavelengths = new Vector3f(700, 530, 440);
    private Vector3f scatterCoeffiecients = new Vector3f();
    private float densityFalloff = 4f;
    private float scatteringStrength = 2f;
    private float atmosphereRadius = Main.radius + 12000;
    private float planetRadius = Main.radius;
    private int numOpticalDepthPoints = 10;
    private int blueNoiseTexture;
    private TimerQuery timer;

    public Vector3f calculateLightColor(Vector3f lightPos, Vector3f cameraPosition) {
        calculateScatterCoefficients();
        Vector3f toSun = new Vector3f(lightPos).sub(cameraPosition).normalize();
        Vector2f sphereTest = Maths.raySphere(new Vector3f(), atmosphereRadius, cameraPosition, toSun);
        float sunRayDepth = calculateOpticalDepth(cameraPosition, toSun, sphereTest.y);
        float transittanceR = (float) Math.exp(-(sunRayDepth) * scatterCoeffiecients.x);
        float transittanceG = (float) Math.exp(-(sunRayDepth) * scatterCoeffiecients.y);
        float transittanceB = (float) Math.exp(-(sunRayDepth) * scatterCoeffiecients.z);
        return new Vector3f(transittanceR, transittanceG, transittanceB);
    }

    private float calculateOpticalDepth(Vector3f rayOrigin, Vector3f rayDir, float rayLength) {
        Vector3f densitySamplePoint = new Vector3f(rayOrigin);
        float stepSize = rayLength / (numOpticalDepthPoints - 1);
        float opticalDepth = 0;
        Vector3f scaledRayDir = new Vector3f(rayDir).mul(rayDir);
        for (int i = 0; i < numOpticalDepthPoints; i++) {
            float heightAboveSurface = densitySamplePoint.length() - planetRadius;
            float height01 = Math.max(heightAboveSurface / (atmosphereRadius - planetRadius), 0f);
            float localDensity = (float) Math.exp(-height01 * densityFalloff) * (1f - height01);
            opticalDepth += localDensity * stepSize;
            densitySamplePoint.add(scaledRayDir);
        }
        return opticalDepth;
    }

    public Atmosphere(QuadRenderer renderer) {
        this.renderer = renderer;
        ShaderFactory atmosphereFactory = new ShaderFactory("postProcessing/atmosphere/atmosphereVS.glsl", "postProcessing/atmosphere/atmosphereFS.glsl").withAttributes("pos");
        atmosphereFactory.withUniforms("originalTexture", "depthTexture", "noiseTexture", "lookUpTexture", "camPos", ATMOSPHERE_RADIUS, "dirToSun", PLANET_RADIUS, DENSITY_FALLOFF, "scatterCoefficients", "invProjMatrix", "zNear", "zFar");
        atmosphereFactory.configureSampler("originalTexture", 0);
        atmosphereFactory.configureSampler("depthTexture", 1);
        atmosphereFactory.configureSampler("noiseTexture", 2);
        atmosphereFactory.configureSampler("lookUpTexture", 3);

        atmosphereFactory.configureShaderConstant("numInScatterPoints", 10);
        atmosphereFactory.configureShaderConstant("numOpticalDepthPoints", numOpticalDepthPoints);
        atmosphereFactory.withUniformArray("frustumRays", 4);
        atmosphereShader = atmosphereFactory.built();
        ShaderFactory atmLookupFactory = new ShaderFactory("postProcessing/quadVS.glsl", "postProcessing/atmosphere/opticalDepthPreCompute.glsl").withAttributes("pos");
        atmLookupFactory.configureShaderConstant("numOpticalDepthPoints", 200);
        lookupGenerator = atmLookupFactory.withUniforms(PLANET_RADIUS, DENSITY_FALLOFF, ATMOSPHERE_RADIUS).built();
        lookUpTable = new FrameBufferObject(lookupTableSize, lookupTableSize, 1).addUnclampedTexture(0);
        lookUpTable.bind();
        lookupGenerator.bind();
        lookupGenerator.loadFloat(ATMOSPHERE_RADIUS, atmosphereRadius);
        lookupGenerator.loadFloat(PLANET_RADIUS, planetRadius);
        lookupGenerator.loadFloat(DENSITY_FALLOFF, densityFalloff);
        renderer.renderOnlyQuad();
        lookupGenerator.unbind();
        lookUpTable.unbind();
        //get texture values
        float[] floatValues = new float[lookupTableSize * lookupTableSize];
        glBindTexture(GL_TEXTURE_2D, lookUpTable.getTextureID(0));
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_FLOAT, floatValues);
        blueNoiseTexture = TextureLoader.loadTexture("misc/blueNoise.png", GL_REPEAT, GL_NEAREST);
        timer = new TimerQuery("Atmosphere");
    }

    public void render(Camera camera, Matrix4f projMatrix, int texture, int depthTexture, Vector3f lightPos) {
        timer.startQuery();
        atmosphereShader.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, blueNoiseTexture);
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, lookUpTable.getTextureID(0));
        atmosphereShader.load3DVector("dirToSun", new Vector3f(lightPos).normalize());
        atmosphereShader.loadFloat("zNear", 1f);
        atmosphereShader.loadFloat("zFar", 100000);
        atmosphereShader.loadFloat(ATMOSPHERE_RADIUS, atmosphereRadius);
        atmosphereShader.loadFloat(PLANET_RADIUS, planetRadius);
        calculateScatterCoefficients();
        atmosphereShader.load3DVector("scatterCoefficients", scatterCoeffiecients);
        atmosphereShader.loadFloat(DENSITY_FALLOFF, densityFalloff);
        atmosphereShader.load3DVector("camPos", camera.getPosition());
        Matrix4f projViewMatrix = new Matrix4f(projMatrix).mul(camera.getViewMatrix());
        Matrix4f projInv = new Matrix4f(projMatrix).invert();
        atmosphereShader.loadMatrix("invProjMatrix", projInv);
        atmosphereShader.load3DVector("frustumRays[0]", projViewMatrix.frustumRayDir(0, 0, new Vector3f()));
        atmosphereShader.load3DVector("frustumRays[1]", projViewMatrix.frustumRayDir(1, 0, new Vector3f()));
        atmosphereShader.load3DVector("frustumRays[2]", projViewMatrix.frustumRayDir(0, 1, new Vector3f()));
        atmosphereShader.load3DVector("frustumRays[3]", projViewMatrix.frustumRayDir(1, 1, new Vector3f()));
        renderer.renderOnlyQuad();
        atmosphereShader.unbind();
        timer.waitOnQuery();
    }

    private void calculateScatterCoefficients() {
        float scatterR = (float) Math.pow(50f / wavelengths.x, 4) * scatteringStrength;
        float scatterG = (float) Math.pow(50f / wavelengths.y, 4) * scatteringStrength;
        float scatterB = (float) Math.pow(50f / wavelengths.z, 4) * scatteringStrength;
        scatterCoeffiecients.set(scatterR, scatterG, scatterB);
    }

    public int getLookUpTable() {
        return lookUpTable.getTextureID(0);
    }

    public void printTimer() {
        timer.printResults();
    }

}
