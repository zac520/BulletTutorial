package com.zsoft.bullettutorial.Screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.Disposable;
import com.zsoft.bullettutorial.MainGame;

/**
 * Created by zac520 on 12/10/14.
 */
public class MainScreen implements Screen {


    MainGame game;

    PerspectiveCamera cam;
    CameraInputController camController;
    ModelBatch modelBatch;
    Environment environment;

    Model model;
    ModelInstance ground;
    ModelInstance ball;
    boolean collision;
    btCollisionShape groundShape;
    btCollisionShape ballShape;
    btCollisionObject groundObject;
    btCollisionObject ballObject;
    btCollisionConfiguration collisionConfig;
    btDispatcher dispatcher;

    Array<GameObject> instances;
    ArrayMap<String, GameObject.Constructor> constructors;
    float spawnTimer;

    MyContactListener contactListener;
    btBroadphaseInterface broadphase;
    btCollisionWorld collisionWorld;

    final static short GROUND_FLAG = 1<<8;
    final static short OBJECT_FLAG = 1<<9;
    final static short ALL_FLAG = -1;

    btDynamicsWorld dynamicsWorld;
    btConstraintSolver constraintSolver;

    float angle, speed = 90f;


    btGhostPairCallback ghostPairCallback;
    btPairCachingGhostObject ghostObject;
    btConvexShape ghostShape;
    btKinematicCharacterController characterController;
    Matrix4 characterTransform;
    Vector3 characterDirection = new Vector3();
    Vector3 walkDirection = new Vector3();
    GameObject characterObject;

    DebugDrawer debugDrawer;
    private final Vector3 tmpV1 = new Vector3();

    public float rotateAngle = 360f;

    public MainScreen(MainGame myGame) {
        //MUST init before we can use bullet
        Bullet.init();

        game = myGame;

        //renders the models
        modelBatch = new ModelBatch();

        //sets the light, etc
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        //set up the camera
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(3f, 7f, 10f);
        cam.lookAt(0, 4f, 0);
        cam.update();

        //allow for user control (user can fly the camera)
        camController = new CameraInputController(cam);
        Gdx.input.setInputProcessor(camController);

        defineModels();

        applyModels();

        makeWorld();

        //this is used to set the color of the objects when they hit the ground
        contactListener = new MyContactListener();


        makeGround();

        makePlayer();

        //debug drawing
        debugDrawer = new DebugDrawer();
        debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_MAX_DEBUG_DRAW_MODE);
        dynamicsWorld.setDebugDrawer(debugDrawer);

    }

    private void makePlayer(){
        //create a cone character
        characterObject = constructors.get("cone").construct();
        characterObject.body.setCollisionFlags(characterObject.body.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);
        characterObject.transform.set(new Vector3(0, 5, 0), characterObject.body.getOrientation());//set it to start at 5 so it falls to ground
        characterObject.body.proceedToTransform(characterObject.transform);//apply the change in position
        instances.add(characterObject);//renderable, but no physics
        characterObject.body.setAngularFactor(new Vector3(0,1,0));   //make it so it can't tip over
        characterObject.body.setFriction(1);
        dynamicsWorld.addRigidBody(characterObject.body);//physics, but no render
    }

    private void makeGround(){
        //static ground body
//		instances = new Array<GameObject>();
//		GameObject object = constructors.get("ground").construct();
//		instances.add(object);
//		//dynamicsWorld.addRigidBody(object.body, GROUND_FLAG, ALL_FLAG);
//		dynamicsWorld.addRigidBody(object.body);
//		object.body.setContactCallbackFlag(GROUND_FLAG);
//		object.body.setContactCallbackFilter(0);

        //kinematic ground body
        instances = new Array<GameObject>();
        GameObject object = constructors.get("ground").construct();
        object.transform.trn(MathUtils.random(-2.5f, 2.5f), -9000f, MathUtils.random(-2.5f, 2.5f));

        object.body.setCollisionFlags(object.body.getCollisionFlags()
                | btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT);
        instances.add(object);
        dynamicsWorld.addRigidBody(object.body);
        object.body.setContactCallbackFlag(GROUND_FLAG);
        object.body.setContactCallbackFilter(0);
        object.body.setActivationState(Collision.DISABLE_DEACTIVATION);//make it not sleepable
    }

    private void applyModels(){
        //apply those models and put them into a map
        constructors = new ArrayMap<String, GameObject.Constructor>(String.class, GameObject.Constructor.class);
        constructors.put("ground", new GameObject.Constructor(model, "ground", new btBoxShape(new Vector3(25f, .5f, 25f)), 0f));
        constructors.put("sphere", new GameObject.Constructor(model, "sphere", new btSphereShape(0.5f), 1f));
        constructors.put("box", new GameObject.Constructor(model, "box", new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f)), 1f));
        constructors.put("cone", new GameObject.Constructor(model, "cone", new btConeShape(0.5f, 2f), 1f));
        constructors.put("capsule", new GameObject.Constructor(model, "capsule", new btCapsuleShape(.5f, 1f), 1f));
        constructors.put("cylinder", new GameObject.Constructor(model, "cylinder", new btCylinderShape(new Vector3(.5f, 1f, .5f)), 1f));
        instances = new Array<GameObject>();
        instances.add(constructors.get("ground").construct());
    }
    private void defineModels(){

        //set a bunch of things the world will make randomly
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.node().id = "ground";
        mb.part("ground", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.RED)))
                .box(50f, 1f, 50f);
        mb.node().id = "sphere";
        mb.part("sphere", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.GREEN)))
                .sphere(1f, 1f, 1f, 10, 10);
        mb.node().id = "box";
        mb.part("box", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.BLUE)))
                .box(1f, 1f, 1f);
        mb.node().id = "cone";
        mb.part("cone", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.YELLOW)))
                .cone(1f, 2f, 1f, 10);
        mb.node().id = "capsule";
        mb.part("capsule", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.CYAN)))
                .capsule(0.5f, 2f, 10);
        mb.node().id = "cylinder";
        mb.part("cylinder", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.MAGENTA)))
                .cylinder(1f, 2f, 1f, 10);
        model = mb.end();
    }

    private void makeWorld(){
        //prepare the world
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        constraintSolver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -10f, 0));
    }



    @Override
    public void render (float delta) {
        camController.update();

        Gdx.gl.glClearColor(0.3f, 0.3f, 0.3f, 1.f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);


        //rendered drawing
        modelBatch.begin(cam);
        modelBatch.render(instances, environment);
        modelBatch.end();


        //debug drawing
		debugDrawer.begin(cam);
		dynamicsWorld.debugDrawWorld();
		debugDrawer.end();

        final float myDelta = Math.min(1f / 30f, delta);

        //move the ground
        angle = (angle + myDelta * speed) % 360f;
        instances.get(0).transform.setTranslation(0, MathUtils.sinDeg(angle) * 2.5f, 0f);//activate TO MOVE BOX UP AND DOWN
        //instances.get(0).body.setWorldTransform(instances.get(0).transform);
        //instances.get(0).body.activate();//wake up the sleeping object(not necessary because we deactivated that flag on creation)


        update();

        centerPlayerOnScreen();

        dynamicsWorld.stepSimulation(myDelta, 5, 1f/60f);

        if ((spawnTimer -= myDelta) < 0) {
            spawn();
            spawnTimer = 1.5f;
        }

    }


    public void update () {
//		// If the left or right key is pressed, rotate the character and update its physics update accordingly.

        float xDirection;
        float zDirection;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {


            characterObject.body.applyCentralForce(new Vector3(
                    characterObject.body.getAngularVelocity().x,
                    50,
                    characterObject.body.getAngularVelocity().y));
        }

        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {

            //get the angles we are facing
            xDirection = cam.direction.x;
            zDirection = cam.direction.z;

            //apply the force to the character
            characterObject.body.applyCentralForce(new Vector3(
                    characterObject.body.getAngularVelocity().x + xDirection*40,
                    characterObject.body.getAngularVelocity().z,
                    characterObject.body.getAngularVelocity().y + zDirection*40));

        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {

            //get the angles we are facing
            xDirection = -cam.direction.x;
            zDirection = -cam.direction.z;

            //apply the force to the character
            characterObject.body.applyCentralForce(new Vector3(
                    characterObject.body.getAngularVelocity().x + xDirection*40,
                    characterObject.body.getAngularVelocity().z,
                    characterObject.body.getAngularVelocity().y + zDirection*40));

        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {

            cam.rotateAround(characterObject.body.getCenterOfMassPosition(), Vector3.Y, 0.005f * rotateAngle);
            cam.update();
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {

            cam.rotateAround(characterObject.body.getCenterOfMassPosition(), Vector3.Y, -0.005f * rotateAngle);
            cam.update();
        }
    }

    public void centerPlayerOnScreen(){

        int camDistanceFromPlayer = 5;
        //TODO for now this is static. We want the user to be able to zoom. We need to override and use the automatic camera controls and add in something to calculate current zoom

        cam.position.set(
                characterObject.body.getCenterOfMassPosition().x - cam.direction.x * camDistanceFromPlayer,
                characterObject.body.getCenterOfMassPosition().y - cam.direction.y * camDistanceFromPlayer,
                characterObject.body.getCenterOfMassPosition().z - cam.direction.z * camDistanceFromPlayer
                );
        cam.update();
    }

    public void spawn () {
        GameObject obj = constructors.values[1 + MathUtils.random(constructors.size - 2)].construct();
        obj.transform.setFromEulerAngles(MathUtils.random(360f), MathUtils.random(360f), MathUtils.random(360f));
        obj.transform.trn(MathUtils.random(-2.5f, 2.5f), 9f, MathUtils.random(-2.5f, 2.5f));
        obj.body.proceedToTransform(obj.transform);
        obj.body.setUserValue(instances.size);
        obj.body.setCollisionFlags(obj.body.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_CUSTOM_MATERIAL_CALLBACK);
        instances.add(obj);
        //dynamicsWorld.addRigidBody(obj.body, OBJECT_FLAG, GROUND_FLAG);
        dynamicsWorld.addRigidBody(obj.body);
        obj.body.setContactCallbackFlag(OBJECT_FLAG);//only for our events. this will not trigger a change in the physics
        obj.body.setContactCallbackFilter(GROUND_FLAG);
    }

    class MyContactListener extends ContactListener {

        @Override
        public boolean onContactAdded (int userValue0, int partId0, int index0, boolean match0,
                                       int userValue1, int partId1, int index1, boolean match1) {
            if (match0)
                ((ColorAttribute)instances.get(userValue0).materials.get(0).get(ColorAttribute.Diffuse)).color.set(Color.WHITE);
            if (match1)
                ((ColorAttribute)instances.get(userValue1).materials.get(0).get(ColorAttribute.Diffuse)).color.set(Color.WHITE);
            return true;
        }


    }
    static class MyMotionState extends btMotionState {
        Matrix4 transform;
        @Override
        public void getWorldTransform (Matrix4 worldTrans) {
            worldTrans.set(transform);
        }
        @Override
        public void setWorldTransform (Matrix4 worldTrans) {
            transform.set(worldTrans);
        }
    }
    static class GameObject extends ModelInstance implements Disposable {

        public final btRigidBody body;
        public final MyMotionState motionState;

        public GameObject (Model model, String node, btRigidBody.btRigidBodyConstructionInfo constructionInfo) {
            super(model, node);
            motionState = new MyMotionState();
            motionState.transform = transform;
            body = new btRigidBody(constructionInfo);
            body.setMotionState(motionState);
        }

        @Override
        public void dispose () {
            body.dispose();
            motionState.dispose();
        }

        static class Constructor implements Disposable {
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


    @Override public void dispose () {
        for (GameObject obj : instances)
            obj.dispose();
        instances.clear();

        for (GameObject.Constructor ctor : constructors.values())
            ctor.dispose();
        constructors.clear();

        dispatcher.dispose();
        collisionConfig.dispose();

        modelBatch.dispose();
        model.dispose();
        contactListener.dispose();
        collisionWorld.dispose();
        broadphase.dispose();

        dynamicsWorld.dispose();
        constraintSolver.dispose();
    }
    @Override public void pause () {}
    @Override public void resume () {}

    @Override
    public void hide() {

    }

    @Override
    public void show() {

    }


    @Override public void resize (int width, int height) {}
}
