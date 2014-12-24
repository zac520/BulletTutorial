package com.zsoft.bullettutorial.Actors;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.linearmath.btTransform;
import com.badlogic.gdx.utils.Disposable;
import com.zsoft.bullettutorial.Helpers.GameObject;
import com.zsoft.bullettutorial.Helpers.MyMotionState;

/**
 * Created by zac520 on 12/24/14.
 */
public class PlayerActor extends ModelInstance implements Disposable {

    public btRigidBody body;
    public MyMotionState motionState;
    public BoundingBox bounds = new BoundingBox();
    protected final static Vector3 position = new Vector3();
    public Vector3 center = new Vector3();
    public Vector3 dimensions= new Vector3();

    public final Model model;
    public String node;
    public btCollisionShape shape;
    public btRigidBody.btRigidBodyConstructionInfo constructionInfo;
    private static Vector3 localInertia = new Vector3();

    public float MAX_SPEED = 10f;


    /**
     * It seems that to import a model, we are stuck with whatever size AND rotation values it sends. We are
     * able to rotate manually later, but it binds the physics shape. Right now, for instance, it is being
     * sent in with a 90 degree rotation. After manual rotation of the model, the capsule shape that defines its physics
     * sits on the ground, instead of on end how we want it.
     * @param model
     * @param node
     * @param shape
     * @param mass
     */

    public PlayerActor (Model model, String node, btCollisionShape shape, float mass) {
        super(model);

        //we will save the processing of always getting square root of all velocities by storing MAX_SPEED already scaled
        MAX_SPEED*=MAX_SPEED;

        if (mass > 0f)
            shape.calculateLocalInertia(mass, localInertia);
        else
            localInertia.set(0, 0, 0);


        this.constructionInfo = new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, localInertia);
        this.node = node;
        this.shape = shape;
        this.model = model;

        motionState = new MyMotionState();
        motionState.transform = transform;
        body = new btRigidBody(constructionInfo);
        body.setMotionState(motionState);

        //set the center and the dimensions
        model.calculateBoundingBox(bounds).getCenter(center);
        model.calculateBoundingBox(bounds).getDimensions(dimensions);
    }


    /**
     * This will calculate the squared value of the speed we are moving. We are going to set our max_speed
     * as the squared value of what we want, so we save processing it each time.
     * @return
     */
    public float getForwardSpeed(){


        return body.getLinearVelocity().x *  body.getLinearVelocity().x +
                body.getLinearVelocity().y * body.getLinearVelocity().y +
                body.getLinearVelocity().z * body.getLinearVelocity().z;


    }
    @Override
    public void dispose () {
        body.dispose();
        motionState.dispose();
    }


}
