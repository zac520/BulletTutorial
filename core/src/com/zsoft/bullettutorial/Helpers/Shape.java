package com.zsoft.bullettutorial.Helpers;

/**
 * Created by zac520 on 12/18/14.
 */

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.Ray;

/**
 * To improve accuracy, and not hurt performance, we are giving each model a shape that is more general than its
 * rendered look. This allows us to faster determine if there is an intersection.
 */

public interface Shape {
    public abstract boolean isVisible(Matrix4 transform, Camera cam);
    /** @return -1 on no intersection, or when there is an intersection: the squared distance between the center of this
     * object and the point on the ray closest to this object when there is intersection. */
    public abstract float intersects(Matrix4 transform, Ray ray);
}