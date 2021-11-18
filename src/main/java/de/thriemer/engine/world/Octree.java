package de.thriemer.engine.world;

import de.thriemer.disguisedphoenix.Entity;
import de.thriemer.engine.util.Maths;
import org.joml.FrustumIntersection;
import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Octree {

    private static final float MIN_SIZE = 5f;
    private static final float LOOSENESS = 1.5f;
    private int splitSize = 40;
    private final Vector3f centerPosition;
    private final float halfWidth;
    private final float halfHeight;
    private final float halfDepth;
    private boolean hasChildren = false;
    private Octree[] nodes;
    private final Vector3f min;
    private final Vector3f max;
    private final Vector3f looseMin;
    private final Vector3f looseMax;
    private final List<Entity> entities = new ArrayList<>();

    public Octree(Vector3f centerPosition, float width, float height, float depth) {
        this.centerPosition = centerPosition;
        this.halfWidth = width / 2f;
        this.halfHeight = height / 2f;
        this.halfDepth = depth / 2f;
        min = new Vector3f(centerPosition).sub(halfWidth, halfHeight, halfDepth);
        max = new Vector3f(centerPosition).add(halfWidth, halfHeight, halfDepth);
        looseMin = new Vector3f(centerPosition).sub(LOOSENESS * halfWidth, LOOSENESS * halfHeight, LOOSENESS * halfDepth);
        looseMax = new Vector3f(centerPosition).add(LOOSENESS * halfWidth, LOOSENESS * halfHeight, LOOSENESS * halfDepth);

    }

    public void insert(Entity e) {
        if (hasChildren) {
            boolean stillFree = true;
            for (Octree node : nodes) {
                if (node.containsLoosely(e) && node.contains(e.getCenter())) {
                    node.insert(e);
                    stillFree = false;
                    break;
                }
            }
            if (stillFree) {
                entities.add(e);
            }
        } else {
            entities.add(e);
        }
        if (entities.size() > splitSize && !hasChildren && !hasMinSize()) {
            splitTree();
        }
    }

    private boolean contains(Vector3f center) {
        return Maths.pointInAabb(min, max, center);
    }

    private void splitTree() {
        nodes = new Octree[8];
        //cache all the entities to not end up in an endless loop
        List<Entity> toReinsert = new ArrayList<>(entities);
        entities.clear();
        float quarterWidth = halfWidth / 2f;
        float quarterHeight = halfHeight / 2f;
        float quarterDepth = halfDepth / 2f;
        for (int x = 0; x <= 1; x++) {
            float timesX = x * 2f - 1f;
            for (int y = 0; y <= 1; y++) {
                float timesY = y * 2f - 1f;
                for (int z = 0; z <= 1; z++) {
                    float timesZ = z * 2f - 1f;
                    nodes[x * 4 + y * 2 + z] = new Octree(new Vector3f(centerPosition).add(quarterWidth * timesX, quarterHeight * timesY, quarterDepth * timesZ), halfWidth, halfHeight, halfDepth);
                }
            }
        }
        //reinsert it in the this node and therefore leaves
        hasChildren = true;
        for (Entity e : toReinsert) {
            this.insert(e);
        }
    }

    public List<Entity> getAllVisibleEntities(FrustumIntersection frustum, Function<Entity, Boolean> visibilityFunction, List<Entity> result) {
        if (frustum.testAab(looseMin, looseMax)) {
            List<Entity> shallowCopy = new ArrayList<>(entities);
            if (!shallowCopy.isEmpty()) {
                for (Entity e : shallowCopy) {
                    if (visibilityFunction.apply(e) && frustum.testSphere(e.getCenter(), e.getRadius()))
                        result.add(e);
                }
            }
            if (hasChildren) {
                for (Octree node : nodes) {
                    if (node != null)
                        node.getAllVisibleEntities(frustum, visibilityFunction, result);
                }
            }
        }
        return result;
    }

    public void collectStats(int level, Map<Integer, Integer> levelInfo) {
        int size = entities.size();
        if (levelInfo.containsKey(level)) {
            int current = levelInfo.get(level);
            current += size;
            levelInfo.put(level, current);
        } else {
            levelInfo.put(level, size);
        }
        if (hasChildren)
            for (Octree o : nodes)
                o.collectStats(level + 1, levelInfo);
    }
    protected boolean contains(Entity e) {
        return Intersectionf.testAabSphere(min, max, e.getCenter(), e.getRadius());
    }
    protected boolean containsLoosely(Entity e) {
        return Maths.aabbFullyContainsSphere(looseMin, looseMax, e.getCenter(), e.getRadius());
    }

    private boolean hasMinSize() {
        return halfWidth <= MIN_SIZE || halfHeight <= MIN_SIZE || halfDepth <= MIN_SIZE;
    }

    public Matrix4f getTransformationMatrix() {
        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.translate(centerPosition);
        modelMatrix.scale(halfWidth, halfHeight, halfDepth);
        return modelMatrix;
    }

    public List<Matrix4f> getAllTransformationMatrices(int depth) {
        List<Matrix4f> list = new ArrayList<>();
        list.add(getTransformationMatrix());
        if (hasChildren && depth > 0) {
            for (Octree node : nodes) {
                list.addAll(node.getAllTransformationMatrices(depth - 1));
            }
        }
        return list;
    }

    public Vector3f getCenter() {
        return centerPosition;
    }

    public List<Entity> getEntities() {
        return entities;
    }

}
