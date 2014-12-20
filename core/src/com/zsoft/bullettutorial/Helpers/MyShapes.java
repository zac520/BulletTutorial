package com.zsoft.bullettutorial.Helpers;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;

/**
 * Created by zac520 on 12/18/14.
 */
public class MyShapes {


    public static abstract class BaseShape implements Shape {
        protected final static Vector3 position = new Vector3();
        public final Vector3 center = new Vector3();
        public final Vector3 dimensions = new Vector3();


        public BaseShape(BoundingBox bounds) {
            bounds.getCenter(center);
            bounds.getDimensions(dimensions);
        }
    }

    public static class Sphere extends BaseShape {
        public float radius;

        public Sphere(BoundingBox bounds) {
            super(bounds);
            radius = bounds.getDimensions().len() / 2f;
        }
        public static class Box extends BaseShape {
            public Box(BoundingBox bounds) {
                super(bounds);
            }

            @Override
            public boolean isVisible(Matrix4 transform, Camera cam) {
                return cam.frustum.boundsInFrustum(transform.getTranslation(position).add(center), dimensions);
            }

            @Override
            public float intersects(Matrix4 transform, Ray ray) {
                transform.getTranslation(position).add(center);
                if (Intersector.intersectRayBoundsFast(ray, position, dimensions)) {
                    final float len = ray.direction.dot(position.x-ray.origin.x, position.y-ray.origin.y, position.z-ray.origin.z);
                    return position.dst2(ray.origin.x+ray.direction.x*len, ray.origin.y+ray.direction.y*len, ray.origin.z+ray.direction.z*len);
                }
                return -1f;
            }
        }

        @Override
        public boolean isVisible(Matrix4 transform, Camera cam) {
            return cam.frustum.sphereInFrustum(transform.getTranslation(position).add(center), radius);
        }

        @Override
        public float intersects(Matrix4 transform, Ray ray) {
            transform.getTranslation(position).add(center);
            final float len = ray.direction.dot(position.x-ray.origin.x, position.y-ray.origin.y, position.z-ray.origin.z);
            if (len < 0f)
                return -1f;
            float dist2 = position.dst2(ray.origin.x+ray.direction.x*len, ray.origin.y+ray.direction.y*len, ray.origin.z+ray.direction.z*len);
            return (dist2 <= radius * radius) ? dist2 : -1f;
        }
    }

    public static class Box extends BaseShape {
        public Box(BoundingBox bounds) {
            super(bounds);
        }

        @Override
        public boolean isVisible(Matrix4 transform, Camera cam) {
            return cam.frustum.boundsInFrustum(transform.getTranslation(position).add(center), dimensions);
        }

        @Override
        public float intersects(Matrix4 transform, Ray ray) {
            transform.getTranslation(position).add(center);
            if (Intersector.intersectRayBoundsFast(ray, position, dimensions)) {
                final float len = ray.direction.dot(position.x-ray.origin.x, position.y-ray.origin.y, position.z-ray.origin.z);
                return position.dst2(ray.origin.x+ray.direction.x*len, ray.origin.y+ray.direction.y*len, ray.origin.z+ray.direction.z*len);
            }
            return -1f;
        }
    }

    public static class Disc extends BaseShape {
        public float radius;
        public Disc(BoundingBox bounds) {
            super(bounds);
            radius = 0.5f * (dimensions.x > dimensions.z ? dimensions.x : dimensions.z);
        }

        @Override
        public boolean isVisible (Matrix4 transform, Camera cam) {
            return cam.frustum.sphereInFrustum(transform.getTranslation(position).add(center), radius);
        }

        @Override
        public float intersects (Matrix4 transform, Ray ray) {
            transform.getTranslation(position).add(center);
            final float len = (position.y - ray.origin.y) / ray.direction.y;
            final float dist2 = position.dst2(ray.origin.x + len * ray.direction.x, ray.origin.y + len * ray.direction.y, ray.origin.z + len * ray.direction.z);
            return (dist2 < radius * radius) ? dist2 : -1f;
        }
    }

}
