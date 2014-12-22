package com.zsoft.bullettutorial.Helpers;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Disposable;

/**
 * Created by zac520 on 12/18/14.
 */
public class GameObject extends ModelInstance implements Disposable {

    public final btRigidBody body;
    public final MyMotionState motionState;
    public BoundingBox bounds = new BoundingBox();
    protected final static Vector3 position = new Vector3();
    public Vector3 center = new Vector3();
    public Vector3 dimensions= new Vector3();
    public Model model;

    public GameObject (Model model, String node, btRigidBody.btRigidBodyConstructionInfo constructionInfo) {
        super(model, node);
        motionState = new MyMotionState();
        motionState.transform = transform;
        body = new btRigidBody(constructionInfo);
        body.setMotionState(motionState);
        this.model = model;

        //set the center and the dimensions
        model.calculateBoundingBox(bounds).getCenter(center);
        model.calculateBoundingBox(bounds).getDimensions(dimensions);

    }

    public boolean isVisible(Camera cam, GameObject instance) {
        //return shape == null ? false : shape.isVisible(transform, cam);

//        System.out.println("Translation: " + instance.center);
//        System.out.println("Dimensions: " + instance.dimensions);
//        System.out.println("Plane"+ cam.frustum.planes[0]);
        //this only shows the entire object if the center is in the screen. Pretty useless really.
        //return cam.frustum.pointInFrustum(instance.body.getCenterOfMassPosition());

        //this will determine if it is within the range of the screen, but not if it is covered by another. We render to infinity (or the range of the camera).
//        instance.calculateBoundingBox(bounds).mul(instance.transform);
        return cam.frustum.boundsInFrustum(bounds);
        //return cam.frustum.boundsInFrustum(instance.body.getCenterOfMassPosition(),instance.dimensions);

    }


    @Override
    public void dispose () {
        body.dispose();
        motionState.dispose();
    }

    public static class Constructor implements Disposable {
        public final Model model;
        public final String node;
        public final btCollisionShape shape;
        public final btRigidBody.btRigidBodyConstructionInfo constructionInfo;
        private static Vector3 localInertia = new Vector3();

        public Constructor (Model model, String node, btCollisionShape shape, float mass) {
            this.model = model;
            this.node = node;
            this.shape = shape;
            if (mass > 0f)
                shape.calculateLocalInertia(mass, localInertia);
            else
                localInertia.set(0, 0, 0);
            this.constructionInfo = new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, localInertia);
        }

        public GameObject construct () {
            return new GameObject(model, node, constructionInfo);
        }



        @Override
        public void dispose () {
            shape.dispose();
            constructionInfo.dispose();
        }
    }


}
