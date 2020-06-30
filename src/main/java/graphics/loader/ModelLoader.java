package graphics.loader;

import engine.collision.Box;
import engine.collision.Collider;
import engine.collision.ConvexShape;
import graphics.objects.Vao;
import graphics.world.Material;
import graphics.world.Mesh;
import graphics.world.Model;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;

public class ModelLoader {

    private static Map<String, Model> alreadyLoadedModels = new HashMap<>();

    private static final int loadFlags = Assimp.aiProcess_Triangulate | Assimp.aiProcess_JoinIdenticalVertices
            | Assimp.aiProcess_ValidateDataStructure | Assimp.aiProcess_ImproveCacheLocality | Assimp.aiProcess_RemoveRedundantMaterials
            | Assimp.aiProcess_FindInvalidData | Assimp.aiProcess_FindInstances | Assimp.aiProcess_OptimizeMeshes | Assimp.aiProcess_OptimizeGraph;

    public static Collider loadCollider(String name, boolean loadToVao) {
        Collider c = new Collider();
        int flags = 0;
        if (loadToVao) flags = flags | Assimp.aiProcess_Triangulate;
        AIScene scene = Assimp.aiImportFile("src/main/resources/models/" + name, flags);
        if (scene == null || (scene.mFlags() & Assimp.AI_SCENE_FLAGS_INCOMPLETE) == 1 || (scene.mFlags() & Assimp.AI_SCENE_FLAGS_VALIDATION_WARNING) == 1 || scene.mRootNode() == null) {
            System.err.println("ERROR::ASSIMP: " + Assimp.aiGetErrorString());
        }
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        int numMeshes = scene.mNumMeshes();
        PointerBuffer meshPointer = scene.mMeshes();
        int verticies = 0;
        for (int i = 0; i < numMeshes; i++) {
            AIMesh mesh = AIMesh.create(meshPointer.get(i));
            verticies += mesh.mNumVertices();
            ConvexShape cs = meshToCollisionShape(loadToVao, mesh);
            max.max(cs.getMax());
            min.min(cs.getMin());
            c.addCollisionShape(cs);
        }
        System.out.println(min + " " + max + " " + verticies);
        c.setBoundingBox(new Box(min, max));
        return c;
    }
    public static Model getModel(String name,String colliderPath) {
        Model rt = alreadyLoadedModels.get(name);
        if (rt == null) {
            System.out.println("Model " + name + " isnt loaded yet. Trying to load it");
            rt = loadModel(name);
            rt.collider = loadCollider(colliderPath,true);
            alreadyLoadedModels.put(name, rt);
        }
        return rt;
    }
    public static Model getModel(String name) {
        Model rt = alreadyLoadedModels.get(name);
        if (rt == null) {
            System.out.println("Model " + name + " isnt loaded yet. Trying to load it");
            rt = loadModel(name);
            alreadyLoadedModels.put(name, rt);
        }
        return rt;
    }

    public static int verticies = 0;
    public static int faces = 0;

    private static Model loadModel(String name) {
        AIScene scene = Assimp.aiImportFile("src/main/resources/models/" + name, loadFlags);
        if (scene == null || (scene.mFlags() & Assimp.AI_SCENE_FLAGS_INCOMPLETE) == 1 || (scene.mFlags() & Assimp.AI_SCENE_FLAGS_VALIDATION_WARNING) == 1 || scene.mRootNode() == null) {
            System.err.println("ERROR::ASSIMP: " + Assimp.aiGetErrorString());
        }
        List<Mesh> meshes = new ArrayList<>();
        List<Material> materials = new ArrayList<>();
        PointerBuffer meshPointer = scene.mMeshes();
        int vertexCount = 0;
        int numMeshes = scene.mNumMeshes();
        int slashIndex = name.lastIndexOf("/");
        String base = "models/" + name.substring(0, slashIndex < 0 ? 0 : slashIndex);
        PointerBuffer materialPointer = scene.mMaterials();
        for (int i = 0; i < numMeshes; i++) {
            AIMesh mesh = AIMesh.create(meshPointer.get(i));
            vertexCount += mesh.mNumVertices();
            Vao meshVao = processMesh(mesh);
            meshes.add(new Mesh(meshVao, getGlobalMaterialPointer(base, mesh.mMaterialIndex(), materialPointer)));
        }
        System.out.println("loaded " + name + ": " + numMeshes + " with " + vertexCount + " verticies! materials: " + materials.size());
        verticies += vertexCount;
        Assimp.aiReleaseImport(scene);
        return new Model(meshes.stream().toArray(Mesh[]::new));
    }

    private static String getGlobalMaterialPointer(String base, int localMaterialIndex, PointerBuffer materialPointer) {
        AIMaterial material = AIMaterial.create(materialPointer.get(localMaterialIndex));
        String materialName = getMaterialName(material);
        if (Material.allMaterials.get(materialName) == null) {
            Material.allMaterials.put(materialName, processMaterial(base, material));
        }
        return materialName;
    }


    private static Material processMaterial(String base, AIMaterial aiMaterial) {
        Material material = new Material(getMaterialName(aiMaterial));
        PointerBuffer pointer = aiMaterial.mProperties();
        for (int i = 0; i < aiMaterial.mNumProperties(); i++) {
            AIMaterialProperty prop = AIMaterialProperty.create(pointer.get(i));
            ByteBuffer data = prop.mData();
            switch (prop.mKey().dataString()) {
                case AI_MATKEY_SHININESS:
                    material.shininess = data.getFloat();
                    break;
                case AI_MATKEY_OPACITY:
                    material.opacity = data.getFloat();
                    break;
                case AI_MATKEY_COLOR_AMBIENT:
                    material.ambient = new Vector3f(data.getFloat(), data.getFloat(), data.getFloat());
                    break;
                case AI_MATKEY_COLOR_DIFFUSE:
                    material.diffuse = new Vector3f(data.getFloat(), data.getFloat(), data.getFloat());
                    break;
                case AI_MATKEY_COLOR_SPECULAR:
                    material.specular = new Vector3f(data.getFloat(), data.getFloat(), data.getFloat());
                    break;
            }
        }
        IntBuffer wrapMode = BufferUtils.createIntBuffer(1);
        AIString diffusePath = AIString.calloc();

        String texturePath = getTexturePath(aiMaterial, aiTextureType_DIFFUSE, diffusePath, wrapMode);
        if (texturePath != null && texturePath.length() > 0) {
            if (ModelLoader.class.getClassLoader().getResourceAsStream(base + "/" + texturePath) != null) {
                material.diffuseTextureId = TextureLoader.loadTexture(base + "/" + texturePath, assimpWrapToOpenGLWrap(wrapMode.get(0)), GL11.GL_LINEAR);
            } else {
                System.out.println("Cannot find texture: " + base + "/" + texturePath);
            }
        }
        AIString normalMapPath = AIString.calloc();
        texturePath = getTexturePath(aiMaterial, aiTextureType_NORMALS, normalMapPath, wrapMode);
        if (texturePath != null && texturePath.length() > 0) {
            if (ModelLoader.class.getClassLoader().getResourceAsStream(base + "/" + texturePath) != null) {
                material.normalsTextureTd = TextureLoader.loadTexture(base + "/" + texturePath, assimpWrapToOpenGLWrap(wrapMode.get(0)), GL11.GL_LINEAR);
            } else {
                System.out.println("Cannot find texture: " + base + "/" + texturePath);
            }
        }

        return material;
    }

    private static int assimpWrapToOpenGLWrap(int assimpWrap) {
        switch (assimpWrap) {
            case aiTextureMapMode_Clamp:
                return GL12.GL_CLAMP_TO_EDGE;
            case aiTextureMapMode_Mirror:
                return GL14.GL_MIRRORED_REPEAT;
            case aiTextureMapMode_Wrap:
                return GL14.GL_REPEAT;
            case aiTextureMapMode_Decal:
                return GL15.GL_CLAMP_TO_BORDER;
        }
        System.err.println(assimpWrap + " is not a assimp wrap mode");
        return -1;
    }

    private static String getTexturePath(AIMaterial aiMaterial, int type, AIString path, IntBuffer wrapMode) {
        Assimp.aiGetMaterialTexture(aiMaterial, type, 0, path, null, null, null, null, wrapMode, null);
        return path.dataString();
    }

    private static Vao processMesh(AIMesh mesh) {
        Vao vao = new Vao();
        AIVector3D.Buffer verticies = mesh.mVertices();
        AIVector3D.Buffer normals = mesh.mNormals();
        int count = mesh.mNumVertices();
        vao.addDataAttributes(0, 3, verticies.address(), verticies.remaining() * AIVector3D.SIZEOF);
        vao.addDataAttributes(1, 2, getTextureCoords(count, mesh.mTextureCoords(0)));
        if (normals != null)
            vao.addDataAttributes(2, 3, normals.address(), normals.remaining() * AIVector3D.SIZEOF);
        else
            System.out.println(mesh.mNormals() + " but normals are null " + mesh.mName().dataString());
        int faceCount = mesh.mNumFaces();
        faces += faceCount;
        int[] indicies = new int[faceCount * 3];
        for (int i = 0; i < faceCount; i++) {
            AIFace face = mesh.mFaces().get(i);
            if (face.mNumIndices() != 3) {
                System.out.println("Your triangulation doesnt work " + face.mNumIndices());
            } else {
                IntBuffer faceIndiecies = face.mIndices();
                faceIndiecies.get(indicies, i * 3, 3);
            }
        }
        vao.addIndicies(indicies);
        return vao;
    }

    private static ConvexShape meshToCollisionShape(boolean useVao, AIMesh mesh) {
        Set<Vector3f> axes = new HashSet<>();
        Set<Vector3f> cornerPoints = new HashSet<>();
        AIVector3D.Buffer verticies = mesh.mVertices();
        AIVector3D.Buffer normals = mesh.mNormals();
        AIFace.Buffer faces = mesh.mFaces();
        int faceCount = mesh.mNumFaces();
        for (int i = 0; i < faceCount; i++) {
            AIFace face = faces.get(i);
            IntBuffer indiecies = face.mIndices();
            for (int j = 0; j < indiecies.remaining(); j++) {
                int index = indiecies.get(j);
                AIVector3D normal = normals.get(index);
                axes.add(new Vector3f(normal.x(), normal.y(), normal.z()));
                AIVector3D vertex = verticies.get(index);
                cornerPoints.add(new Vector3f(vertex.x(), vertex.y(), vertex.z()));
            }
        }
        //remove all axis pointing in the same direction
        Iterator<Vector3f> axeItr = axes.iterator();
        while (axeItr.hasNext()) {
            Vector3f negated = new Vector3f(axeItr.next());
            negated.x = negated.x != 0 ? -negated.x : 0;
            negated.y = negated.y != 0 ? -negated.y : 0;
            negated.z = negated.z != 0 ? -negated.z : 0;
            if (axes.contains(negated)) {
                axeItr.remove();
            }
        }
        System.out.println(axes.size() + " " + cornerPoints.size());
        ConvexShape cs = null;
        if (useVao) {
            Vao meshVao = processMesh(mesh);
            cs = new ConvexShape(cornerPoints.stream().toArray(Vector3f[]::new), axes.stream().toArray(Vector3f[]::new), meshVao);
        } else {
            cs = new ConvexShape(cornerPoints.stream().toArray(Vector3f[]::new), axes.stream().toArray(Vector3f[]::new));
        }
        return cs;
    }

    private static float[] getTextureCoords(int verticesCount, AIVector3D.Buffer buffer) {
        float[] array = new float[verticesCount * 2];
        if (buffer == null || buffer.remaining() != verticesCount) {
            Arrays.fill(array, 0f);
            return array;
        }
        for (int i = 0; i < verticesCount; i++) {
            AIVector3D vec = buffer.get(i);
            array[i * 2] = vec.x();
            array[i * 2 + 1] = vec.y();
        }
        return array;
    }

    private static String getMaterialName(AIMaterial mat) {
        AIString materialName = AIString.calloc();
        Assimp.aiGetMaterialString(mat, AI_MATKEY_NAME, 0, 0, materialName);
        return materialName.dataString();
    }

    private static boolean readMaterialFloat(final AIMaterial mat, final String key, final FloatBuffer store,
                                             final IntBuffer inOut) {
        store.clear();
        boolean rt = Assimp.aiGetMaterialFloatArray(mat, key, Assimp.aiTextureType_NONE, 0, store,
                inOut) == Assimp.aiReturn_SUCCESS && inOut.get(0) == 1;
        System.out.println("Key: " + key + " " + store.get(0) + " " + inOut.get(0));
        return rt;
    }

}
