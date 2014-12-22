package com.zsoft.bullettutorial.Screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.Disposable;
import com.zsoft.bullettutorial.Helpers.GameObject;
import com.zsoft.bullettutorial.Helpers.MeshMerger;
import com.zsoft.bullettutorial.Helpers.MyRenderable;
import com.zsoft.bullettutorial.MainGame;

import java.util.AbstractList;
import java.util.ArrayList;

/**
 * Created by zac520 on 12/10/14.
 */
public class MainScreen implements Screen {

    MainGame game;

    /** Level setup vars**/
    PerspectiveCamera cam;
    CameraInputController camController;
    ModelBatch modelBatch;
    Environment environment;
    DebugDrawer debugDrawer;
    public float rotateAngle = 360f;
    float angle,speed = 90f;
    float visibleCount;
    float spawnTimer;


    /** Level sizing vars **/
    public float GROUND_WIDTH = 500;
    public float GROUND_HEIGHT = 500;
    public float GROUND_THICKNESS = 0.1f;

    public float MAZE_WALL_HEIGHT = 10f;
    public float MAZE_WALL_WIDTH = 10f;
    public float MAZE_WALL_THICKNESS=1f;

    public float PLAYER_HEIGHT = 1;
    public float PlAYER_RADIUS = 0.25f;
    public int PLAYER_DIVISIONS = 10;
    public float PLAYER_MASS = 1f;

    public float MAX_VISIBLE_CAMERA_DISTANCE = 75f;

    /**Maze calculation vars**/
    public int totalVisitedSquares;
    public int blocksHigh = (int) (GROUND_HEIGHT / MAZE_WALL_HEIGHT);
    public int blocksWide = (int) (GROUND_WIDTH / MAZE_WALL_HEIGHT);
    public int totalSquaresToVisit;
    Array<Vector2> positionStack;
    public int lastMovementDirection;
    private int [][] visitedSquares;
    private int currentDistance;
    private int longestDistance;
    private Vector2 longestDistanceLocation;
    private int [][] verticalMazeWalls;
    private int [][] horizontalMazeWalls;
    private Vector3 originForVerticalMazeWall;
    private Vector3 originForHorizontalMazeWall;

    /**Rendering Vars**/
    Model model;
    Model squareModel;
    Array<GameObject> instances;
    private Texture texture;
    public Array<MyRenderable> renderables;
    public DefaultShader shader;
    public RenderContext renderContext;
    Mesh [][] meshes;
    Mesh[] testMeshes;
    Matrix4 []testTransforms;

    /**Bullet (physics) vars**/
    ArrayMap<String, GameObject.Constructor> constructors;
    btCollisionConfiguration collisionConfig;
    btDispatcher dispatcher;
    MyContactListener contactListener;
    btBroadphaseInterface broadphase;
    btCollisionWorld collisionWorld;
    final static short GROUND_FLAG = 1<<8;
    final static short OBJECT_FLAG = 1<<9;
    final static short ALL_FLAG = -1;
    GameObject characterObject;
    GameObject myGroundObject;
    btDynamicsWorld dynamicsWorld;
    btConstraintSolver constraintSolver;

    /**HUD vars**/
    protected Stage stage;
    protected Label label;
    protected BitmapFont font;
    protected StringBuilder stringBuilder;
    HUD userInterfaceStage;
    OrthographicCamera HUDCam;


    public MainScreen(MainGame myGame) {
        //MUST init before we can use bullet
        Bullet.init();

        game = myGame;

        setUpBasics();

        //define the model's rendering shapes
        defineModels();

        //take those shapes, and give them physics by making a GameObject that includes both, and store into a map
        applyModels();

        //set up the 3d world
        makeWorld();

        //debug had to have the world already made, so belongs here.
        setUpDebug();

        //this is used to set the color of the objects when they hit the ground
        contactListener = new MyContactListener();

        //Make just the ground
        instances = new Array<GameObject>();//initialize the renderables array before adding anything
        makeGround();

        //make a border around the maze
        createMazeBorder();

        //calculate the maze
        visitedSquares = new int[blocksWide][blocksHigh];
        zeroOutVisitedArray();
        initializeMazeWallArrays();
        createMazeWithoutRecursion();

        //construct the calculated maze
        createAllMazeWalls();

        //merge all instances we can into fewer Renderables. This vastly improves processing.
        optimizeByMeshing();

        //create the player
        makePlayer();

    }

    private void setUpBasics(){
        //renders the models
        modelBatch = new ModelBatch();

        //sets the light, etc
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        //set up the camera for the 3d stuff
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(3f, 7f, 10f);
        cam.lookAt(0, 4f, 0);
        cam.far = MAX_VISIBLE_CAMERA_DISTANCE;
        cam.update();

        //set up the UI cam with its own separate stage
        HUDCam =new OrthographicCamera();
        HUDCam.setToOrtho(false, game.SCREEN_WIDTH, game.SCREEN_HEIGHT);
        userInterfaceStage =new HUD(game, this);
        userInterfaceStage.getViewport().setCamera(HUDCam);

        //allow for user control (user can fly the camera)
        camController = new CameraInputController(cam);
        Gdx.input.setInputProcessor(new InputMultiplexer(userInterfaceStage, camController));

        //set up the texture for the walls
        texture = new Texture(Gdx.files.internal("assets/concrete.jpg"));
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
    }
    private void setUpDebug(){
        //debug drawing
        debugDrawer = new DebugDrawer();
        debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_MAX_DEBUG_DRAW_MODE);
        dynamicsWorld.setDebugDrawer(debugDrawer);

        //this is for the FPS, and the "visible instances" label. used for Debugging.
        stage = new Stage();
        font = new BitmapFont();
        label = new Label(" ", new Label.LabelStyle(font, Color.WHITE));
        stage.addActor(label);
        stringBuilder = new StringBuilder();
    }

    /** This class takes all of the instances (currently 2700 of them), and merges them into a 2d array of
     * meshes. Each mesh is the size of the camera views. We then make a similar Renderable based on those meshes.
     * Renderable has been extended into MyRenderable, so that we can store the bounding box. During rendering, we
     * can check and see if any of this bounding box is visible to the camera, and only render if it is.
     */
    private void optimizeByMeshing(){
        //from http://www.badlogicgames.com/forum/viewtopic.php?f=11&t=10842&hilit=merge+3D&start=20
        ArrayList<Mesh> meshesToMerge = new ArrayList<com.badlogic.gdx.graphics.Mesh>();
        ArrayList<Matrix4> transforms = new ArrayList<Matrix4>();

        //we are going to divide the meshes into blocks of the size of cam.far. This we way will only render the meshes we can see
        float divisions = cam.far;

        //since the ground is at the origin, 0, a lot of values are negative. We will shift the locations so we can put into proper indexes
        float xMin = (GROUND_WIDTH/2);
        float yMin = (GROUND_HEIGHT/2);

        //create an array of meshes
        meshes = new Mesh[(int)(GROUND_HEIGHT/divisions)+1][(int)(GROUND_WIDTH/divisions)+1];

        Array<Array<Array<GameObject>>> tempInstances = new Array<Array<Array<GameObject>>>();
        //initialize the 3d array. We have to do this because we ".get()" certain indexes later, and it needs something there.
        for(int x = 0; x< (int)(GROUND_HEIGHT/divisions)+1;x++){
            tempInstances.add(new Array<Array<GameObject>>());
            for(int y = 0; y<(int)(GROUND_WIDTH/divisions)+1; y++){
                tempInstances.get(x).add(new Array<GameObject>());
            }
        }

        //sort the instances
        Vector3 tempTransform = new Vector3();
        int xIndex;
        int yIndex;
        for(int x = 0; x<instances.size;x++){
            xIndex = (int) ((instances.get(x).transform.getTranslation(tempTransform).x + xMin) / divisions);
            yIndex = (int) ((instances.get(x).transform.getTranslation(tempTransform).z + yMin)/divisions);

            //take the instance and put it into a findable index for later
            tempInstances.get(xIndex)
                    .get(yIndex)
                    .add(instances.get(x));
        }

        //make an array of meshes of the size of the camera
        MeshMerger meshMerger = new MeshMerger();
        for(int x = 0; x< tempInstances.size; x++){
            for(int y = 0; y<tempInstances.get(x).size;y++){
                //create the array list of meshes and transforms
                for(int z = 0; z<tempInstances.get(x).get(y).size;z++) {
                    ModelInstance mi = tempInstances.get(x).get(y).get(z);
                    com.badlogic.gdx.graphics.Mesh mesh = mi.model.meshes.get(0);
                    if (mesh == null) continue;
                    meshesToMerge.add(mesh);
                    transforms.add(mi.transform);
                }
                meshes[x][y]=meshMerger.mergeMeshes(meshesToMerge, transforms);
                meshesToMerge.clear();
                transforms.clear();
            }
        }

        //create array of renderables based on above meshes
        Model tempModel;
        renderables = new Array<MyRenderable>();
        for(int x = 0; x< meshes.length-1;x++){
            for(int y = 0; y<meshes[x].length-1;y++){
                System.out.println("x: " + x + "y: "+ y);
                tempModel = convertMeshToModel("node1", meshes[x][y]);
                NodePart blockPart = tempModel.getNode("node1").parts.get(0);
                MyRenderable myTempRenderable = new MyRenderable();
                blockPart.setRenderable(myTempRenderable);
                myTempRenderable.boundingBox = myTempRenderable.mesh.calculateBoundingBox();

                //apply the texture
                myTempRenderable.material = new Material( new TextureAttribute(TextureAttribute.Diffuse, texture));

                myTempRenderable.environment = environment;
                myTempRenderable.worldTransform.idt();
                renderables.add(myTempRenderable);
                //renderable.primitiveType = GL20.GL_POINTS;

            }
        }

        //initialize the renderContext and shader
        renderContext = new RenderContext(new DefaultTextureBinder(DefaultTextureBinder.WEIGHTED, 1));
        shader = new DefaultShader(renderables.first());
        shader.init();
    }

    public Model convertMeshToModel(final String id, final Mesh mesh) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        builder.part(id, mesh, GL20.GL_TRIANGLES, new Material(new TextureAttribute(TextureAttribute.Ambient, texture)));
        return builder.end();
    }


    private void createAllMazeWalls(){
        for(int x = 0; x < horizontalMazeWalls.length; x++){
            for(int y = 0; y<horizontalMazeWalls[x].length ;y++) {
                if(horizontalMazeWalls[x][y]==1) {
                    makeSquare(true, x, y);
                }
            }
        }

        for(int x = 0; x < verticalMazeWalls.length; x++){
            for(int y = 0; y<verticalMazeWalls[x].length ;y++) {
                if(verticalMazeWalls[x][y]==1) {
                    makeSquare(false, x, y);
                }
            }
        }
    }

    private void zeroOutVisitedArray(){
        //initialize the visited array
        for (int x = 0; x < blocksWide; x++) {
            for (int y = 0; y < blocksHigh; y++) {
                //make all as zero to represent false, or unvisited
                visitedSquares[x][y] = 0;
            }
        }
    }

    private void initializeMazeWallArrays(){
        //initialize the arrays
        verticalMazeWalls = new int[blocksWide][blocksHigh];
        horizontalMazeWalls = new int[blocksWide][blocksHigh];

        //start them all as walls, and we will delete them as we go. After the calculations, we will create only what is left
        for (int x = 0; x < blocksWide; x++) {
            for (int y = 0; y < blocksHigh; y++) {
                //make all as 1. As we traverse, we will delete the unneeded walls
                verticalMazeWalls[x][y] = 1;
                horizontalMazeWalls[x][y] = 1;
            }
        }



    }

    private void createMazeWithoutRecursion(){
        //had to do it without recursion because stack is way too large to make a maze

        //reset the visited squares for calculation
        totalVisitedSquares =0;


        //calculate the total number of squares to visit
        totalSquaresToVisit = blocksHigh * blocksWide;

        //put us at position 0,0, and mark that square as visited
        Vector2 currentPosition = new Vector2(0, blocksHigh-1);
        //visitedSquares[0][blocksHigh-1] = 1;

        positionStack = new Array<Vector2>();
        int nextSquareDirection;
        int biasDecider;
        positionStack.add(currentPosition);
        while (positionStack.size > 0) {

            //to make longer walls, will randomly give a bias for using the last direction
            biasDecider = game.rand.nextInt((6 - 1) + 1) + 1;//1,2,3, or 4 or 5

            if(biasDecider<5){
                nextSquareDirection = lastMovementDirection;
            }
            else {
                //choose a random direction
                nextSquareDirection = game.rand.nextInt((5 - 1) + 1) + 1;//1,2,3, or 4
            }
            switch (nextSquareDirection) {
                case 1:
                    //if it's too high, or we have already visited that square then check the next direction
                    if ((currentPosition.y + 1 > blocksHigh - 1) || (visitedSquares[(int) currentPosition.x][(int) currentPosition.y + 1] == 1)) {
                        break;
                    }
                    //if it isn't too high, then add to the stack, and check everything again from there
                    else {
                        //remove the wall from our vertical array
                        verticalMazeWalls[(int) currentPosition.x ][(int) currentPosition.y+1] = 0;

                        //travel to that spot now that we can get there
                        currentPosition.y += 1;

                        //add to the current distance
                        currentDistance +=1;

                        //add the current position to the stack
                        positionStack.add(new Vector2(currentPosition));

                        //add to the total squares visited
                        totalVisitedSquares +=1;

                        //save our direction for use in tweaking the maze
                        lastMovementDirection = 1;
                    }

                    break;
                case 2:


                    //if it's too high, or we have already visited that square then check the next direction
                    if ((currentPosition.x + 1 > blocksWide - 1) || (visitedSquares[(int) currentPosition.x + 1][(int) currentPosition.y] == 1)) {
                        break;
                    }
                    //if it isn't too high, then add to the stack, and check everything again from there
                    else {
                        //remove the wall from our vertical array
                        horizontalMazeWalls[(int) currentPosition.x +1][(int) currentPosition.y] = 0;

                        //travel to that spot now that we can get there
                        currentPosition.x += 1;

                        //add to the current distance
                        currentDistance +=1;

                        //add the current position to the stack
                        positionStack.add(new Vector2(currentPosition));

                        //add to the total squares visited
                        totalVisitedSquares +=1;

                        //save our direction for use in tweaking the maze
                        lastMovementDirection = 2;
                    }


                    break;
                case 3:


                    //if it's too low, or we have already visited that square then check the next direction
                    if ((currentPosition.y - 1 < 0) || (visitedSquares[(int) currentPosition.x][(int) currentPosition.y - 1] == 1)) {
                        break;
                    }
                    //if it isn't too high, then add to the stack, and check everything again from there
                    else {
                        //remove the wall from our vertical array
                        verticalMazeWalls[(int) currentPosition.x ][(int) currentPosition.y] = 0;

                        //travel to that spot now that we can get there
                        currentPosition.y -= 1;

                        //add to the current distance
                        currentDistance +=1;

                        //add the current position to the stack
                        positionStack.add(new Vector2(currentPosition));

                        //add to the total squares visited
                        totalVisitedSquares +=1;

                        //save our direction for use in tweaking the maze
                        lastMovementDirection = 3;
                    }

                    break;
                case 4:


                    //if it's too high, or we have already visited that square then check the next direction
                    if ((currentPosition.x - 1 < 0) || (visitedSquares[(int) currentPosition.x - 1][(int) currentPosition.y] == 1)) {
                        break;
                    }
                    //if it isn't too high, then add to the stack, and check everything again from there
                    else {

                        //remove the wall from our vertical array
                        horizontalMazeWalls[(int) currentPosition.x ][(int) currentPosition.y] = 0;

                        //travel to that spot now that we can get there
                        currentPosition.x -= 1;

                        //add to the current distance
                        currentDistance +=1;

                        //add the current position to the stack
                        positionStack.add(new Vector2(currentPosition));

                        //add to the total squares visited
                        totalVisitedSquares +=1;

                        //save our direction for use in tweaking the maze
                        lastMovementDirection = 4;
                    }

                    break;
                default:
                    break;
            }


            visitedSquares[(int)currentPosition.x][(int)currentPosition.y] = 1;


            //now that we have checked our random integer, check all of the other directions. If they all pass, pop off stack
            if (deadEndCheck(currentPosition)) {

                //check to see if this is the longest current spot, if so, make a note of it
                if (currentDistance > longestDistance){
                    longestDistance = currentDistance;
                    longestDistanceLocation = currentPosition;
                }

                //remove one from the current distance
                currentDistance -=1;
                //go back to the previous position
                currentPosition = positionStack.pop();

            }


        }

        //create the end at the longest recorded location
        //createEnd((int)longestDistanceLocation.x, (int) longestDistanceLocation.y);

        //reset the loading progress to 0 so we don't print it anywhere.
        //game.loadingProgressPercent = 0;

    }

    public boolean deadEndCheck(Vector2 currentPosition){

        //check the surrounding areas. If any are reachable, then return false. Else return true;
        if ((currentPosition.y + 1 < blocksHigh) && (visitedSquares[(int) currentPosition.x][(int) currentPosition.y + 1] == 0)) {
            return false;
        }
        if ((currentPosition.x + 1 < blocksWide) && (visitedSquares[(int) currentPosition.x + 1][(int) currentPosition.y] == 0)) {
            return false;
        }
        if ((currentPosition.y - 1 > 0) && (visitedSquares[(int) currentPosition.x][(int) currentPosition.y - 1] == 0)) {
            return false;
        }
        if ((currentPosition.x - 1 > 0) && (visitedSquares[(int) currentPosition.x - 1][(int) currentPosition.y] == 0)) {
            return false;
        }
        return true;
    }




    private void createMazeBorder(){

        //I got confused while writing this, so the x and y's don't make sense but it works. Oh well.

        //create the top and bottom border
        for(int y = 0; y < GROUND_WIDTH / MAZE_WALL_WIDTH; y++){
            for(int x = 0; x<=GROUND_HEIGHT/MAZE_WALL_HEIGHT ;x+=GROUND_HEIGHT/MAZE_WALL_HEIGHT) {
                makeSquare(true, x, y);
            }
        }

        //create the side borders
        for(int y = 0; y < GROUND_WIDTH / MAZE_WALL_WIDTH; y++){
            for(int x = 0; x<=GROUND_HEIGHT/MAZE_WALL_HEIGHT ;x+=GROUND_HEIGHT/MAZE_WALL_HEIGHT) {
                makeSquare(false, y, x);
            }
        }

    }


    private void makeSquare(boolean horizontal, int positionX, int positionY){

        GameObject object = constructors.get("square").construct();
        //object.transform.trn(MathUtils.random(-2.5f, 2.5f), 0f, MathUtils.random(-2.5f, 2.5f));

        object.body.setCollisionFlags(object.body.getCollisionFlags()
                | btCollisionObject.CollisionFlags.CF_STATIC_OBJECT);
        object.body.setContactCallbackFlag(GROUND_FLAG);
        object.body.setContactCallbackFilter(0);
        object.body.setAngularFactor(new Vector3(0, 1, 0));   //make it so it can't tip over
        object.body.setActivationState(Collision.DISABLE_DEACTIVATION);//make it not sleepable

        if(horizontal) {

            //set the transform
            Vector3 newTransform = new Vector3(
                    originForHorizontalMazeWall.x + positionX * MAZE_WALL_WIDTH,
                    originForHorizontalMazeWall.y ,
                    originForHorizontalMazeWall.z+ positionY * MAZE_WALL_HEIGHT);


            //set the rotation
            object.transform.setToRotation(0, 90, 0, 90);
            object.body.proceedToTransform(object.transform);//apply the change in position

            //set the location
            object.transform.set(newTransform,
                    object.body.getOrientation());
            object.body.proceedToTransform(object.transform);//apply the change in position
            //object.center = newTransform;

        }
        else {

            //transform
            Vector3 newTransform = new Vector3(
                    originForVerticalMazeWall.x + positionX * MAZE_WALL_WIDTH,
                    originForVerticalMazeWall.y,
                    originForVerticalMazeWall.z + positionY * MAZE_WALL_HEIGHT);

            //set the location
            object.transform.set(newTransform,
                    object.body.getOrientation());
            object.body.proceedToTransform(object.transform);//apply the change in position
            //object.center = object.model.calculateBoundingBox(bounds).getCenter(object.center);
            //object.center = newTransform;

        }


        object.bounds = object.calculateBoundingBox(object.bounds).mul(object.transform);

        //add to rendering instances, and the dynamic world
        instances.add(object);
        dynamicsWorld.addRigidBody(object.body);
    }

    private void makePlayer(){
        //create a capsule character
        characterObject = constructors.get("capsule").construct();
        characterObject.nodes.get(0).parts.get(0).material.clear();
        characterObject.nodes.get(0).parts.get(0).material.set(new Material());

        characterObject.body.setCollisionFlags(characterObject.body.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);
        characterObject.transform.set(new Vector3(
                originForHorizontalMazeWall.x*0,
                20,
                originForHorizontalMazeWall.y*0), characterObject.body.getOrientation());//set it to start at 15 so it falls to ground
        characterObject.body.proceedToTransform(characterObject.transform);//apply the change in position
        characterObject.body.setAngularFactor(new Vector3(0, 0, 0));   //make it so it can't tip over
        characterObject.body.setFriction(1);
        characterObject.body.setActivationState(Collision.DISABLE_DEACTIVATION);//make it not sleepable


        //instances.add(characterObject);//renderable, but no physics
        dynamicsWorld.addRigidBody(characterObject.body);//physics, but no render
    }

    private void makeGround(){
        myGroundObject = constructors.get("ground").construct();
//        object.body.setCollisionFlags(object.body.getCollisionFlags()
//                | btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT);
        instances.add(myGroundObject);
        dynamicsWorld.addRigidBody(myGroundObject.body);
        myGroundObject.body.setContactCallbackFlag(GROUND_FLAG);
        myGroundObject.body.setContactCallbackFilter(0);
        myGroundObject.body.setActivationState(Collision.DISABLE_DEACTIVATION);//make it not sleepable
        myGroundObject.body.setFriction(2);

        //save the originForVerticalMazeWall here, so we can use it later. Saves calculating a bunch of times later
        originForVerticalMazeWall = new Vector3(
                myGroundObject.body.getCenterOfMassPosition().x-GROUND_WIDTH/2 + MAZE_WALL_WIDTH/2 ,
                myGroundObject.body.getCenterOfMassPosition().y + MAZE_WALL_HEIGHT/2 + GROUND_THICKNESS/2,
                myGroundObject.body.getCenterOfMassPosition().z-GROUND_HEIGHT/2 /*- MAZE_WALL_THICKNESS/2*/ );
        originForHorizontalMazeWall = new Vector3(
                myGroundObject.body.getCenterOfMassPosition().x-GROUND_WIDTH/2 /*+ MAZE_WALL_THICKNESS/2*/ ,
                myGroundObject.body.getCenterOfMassPosition().y + MAZE_WALL_HEIGHT/2 + GROUND_THICKNESS/2,
                myGroundObject.body.getCenterOfMassPosition().z-GROUND_HEIGHT/2 + MAZE_WALL_HEIGHT/2 );
    }


    /**
     * This class takes the models we built earlier, and uses them to attach physical shapes to them, in the GameObject class.
     * It is a map, so we can pull them up with just the string id.
     */
    private void applyModels(){
        //apply those models and put them into a map
        constructors = new ArrayMap<String, GameObject.Constructor>(String.class, GameObject.Constructor.class);
        constructors.put("ground", new GameObject.Constructor(model, "ground", new btBoxShape(new Vector3(GROUND_WIDTH/2, GROUND_THICKNESS/2, GROUND_HEIGHT/2f)), 0f));
        constructors.put("sphere", new GameObject.Constructor(model, "sphere", new btSphereShape(0.5f), 1f));
        constructors.put("box", new GameObject.Constructor(model, "box", new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f)), 1f));
        constructors.put("cone", new GameObject.Constructor(model, "cone", new btConeShape(0.5f, 2f), 1f));
        constructors.put("capsule", new GameObject.Constructor(model, "capsule", new btCapsuleShape(PlAYER_RADIUS, PLAYER_HEIGHT/2), PLAYER_MASS));
        constructors.put("cylinder", new GameObject.Constructor(model, "cylinder", new btCylinderShape(new Vector3(.5f, 1f, .5f)), 1f));
        constructors.put("square", new GameObject.Constructor(squareModel, "square", new btBoxShape(new Vector3(MAZE_WALL_WIDTH/2, MAZE_WALL_HEIGHT/2, MAZE_WALL_THICKNESS/2)), 0f));
    }

    /**
     * This is where we define the rendered models. I still have much learning to do here. The Usage areas currently
     * allow us to determine position (Position), shading (Normal), and apply a texture (TextureCoordinates). This
     * has become pretty tricky, and right now the texture coordinates are not working.
     *
     * Oddly, defining multiple nodes in the model lead to errors later when combining into a mesh. For now, we are just
     * using several different models instead.
     */
    private void defineModels(){

        //set a bunch of things the world will make randomly. These should be twice the size on each x, y, and z property we name for the constructor, due to bullet vs rendering dimensions
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.node().id = "ground";
        mb.part("ground", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, new Material())
                .box(GROUND_WIDTH, GROUND_THICKNESS, GROUND_HEIGHT);
//        mb.node().id = "sphere";
//        mb.part("sphere", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.GREEN)))
//                .sphere(1f, 1f, 1f, 10, 10);
//        mb.node().id = "box";
//        mb.part("box", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.BLUE)))
//                .box(1f, 1f, 1f);
//        mb.node().id = "cone";
//        mb.part("cone", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.YELLOW)))
//                .cone(1f, 2f, 1f, 10);
        mb.node().id = "capsule";
        mb.part("capsule", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates , new Material(ColorAttribute.createDiffuse(Color.CYAN)))
                .capsule(PlAYER_RADIUS, PLAYER_HEIGHT, PLAYER_DIVISIONS);

//        mb.node().id = "cylinder";
//        mb.part("cylinder", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.MAGENTA)))
//                .cylinder(1f, 2f, 1f, 10);


        model = mb.end();

        ModelBuilder mb2 = new ModelBuilder();
        mb2.begin();
        mb2.node().id = "square";
        mb2.part("square", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates , new Material(ColorAttribute.createDiffuse(Color.RED)))
                .box(MAZE_WALL_WIDTH, MAZE_WALL_HEIGHT, MAZE_WALL_THICKNESS);
        squareModel = mb2.end();

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

        //only draw the visible faces
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);


        //enable textures
        Gdx.gl20.glEnable(GL20.GL_TEXTURE_2D);

        //rendered drawing
        modelBatch.begin(cam);

        //We will add dynamic stuff later, and this will again be useful. I think.
        //render everything visible but the player (because the player is dynamic, and the rest is static)
//        visibleCount = 0;
//        for (final GameObject instance : instances) {//only render if it is visible
//            if (instance.isVisible(cam,instance)) {
//                modelBatch.render(instance, environment);
//                visibleCount++;
//            }
//        }
        //render the player
        modelBatch.render(characterObject, environment);

        modelBatch.end();

        //render the level, but only the parts that are in view.
        visibleCount = 0;
        renderContext.begin();
        shader.begin(cam, renderContext);
        for (final MyRenderable renderable : renderables) {
            if (cam.frustum.boundsInFrustum(renderable.boundingBox)) {
                shader.render(renderable);
                visibleCount++;
            }
        }
        shader.end();
        renderContext.end();

        //debug drawing
//		debugDrawer.begin(cam);
//		dynamicsWorld.debugDrawWorld();
//		debugDrawer.end();

        final float myDelta = Math.min(1f / 30f, delta);

        //move the ground up and down
//        angle = (angle + myDelta * speed) % 360f;
//        instances.get(0).transform.setTranslation(0, MathUtils.sinDeg(angle) * 2.5f, 0f);//activate TO MOVE BOX UP AND DOWN


        update();

        centerPlayerOnScreen();

        dynamicsWorld.stepSimulation(myDelta, 5, 1f/60f);

        //spawn crap in the middle
//        if ((spawnTimer -= myDelta) < 0) {
//            spawn();
//            spawnTimer = 1.5f;
//        }

        //these are used for testing
        stringBuilder.setLength(0);
        stringBuilder.append(" FPS: ").append(Gdx.graphics.getFramesPerSecond());
        stringBuilder.append(" Visible: ").append(visibleCount);
        //stringBuilder.append(" Selected: ").append(selected);
        label.setText(stringBuilder);
        stage.draw();

        //render the UI stage
        userInterfaceStage.act(delta);
        userInterfaceStage.draw();

    }


    //TODO make an input listener and myInput class separate
    public boolean forward = false;
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

            //move the camera appropriately
            //centerPlayerOnScreen();


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

            //move the camera appropriately
            //centerPlayerOnScreen();

        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {

            cam.rotateAround(characterObject.body.getCenterOfMassPosition(), Vector3.Y, 0.005f * rotateAngle);
            cam.update();
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {

            cam.rotateAround(characterObject.body.getCenterOfMassPosition(), Vector3.Y, -0.005f * rotateAngle);
            cam.update();
        }

        //this is temporary. Another class will edit this.
        if(forward){
            //get the angles we are facing
            xDirection = cam.direction.x;
            zDirection = cam.direction.z;

            //apply the force to the character
            characterObject.body.applyCentralForce(new Vector3(
                    characterObject.body.getAngularVelocity().x + xDirection*40,
                    characterObject.body.getAngularVelocity().z,
                    characterObject.body.getAngularVelocity().y + zDirection*40));
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
        Gdx.gl20.glDisable(GL20.GL_TEXTURE_2D);

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
