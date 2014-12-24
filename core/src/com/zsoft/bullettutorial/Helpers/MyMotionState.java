package com.zsoft.bullettutorial.Helpers;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;

/**
 * Created by zac520 on 12/18/14.
 */
public class MyMotionState extends btMotionState {
    public Matrix4 transform;
    @Override
    public void getWorldTransform (Matrix4 worldTrans) {
        worldTrans.set(transform);
    }
    @Override
    public void setWorldTransform (Matrix4 worldTrans) {
        transform.set(worldTrans);
    }
}