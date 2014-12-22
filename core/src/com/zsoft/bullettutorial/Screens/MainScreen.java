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
import com.zsoft.bullettutorial.Helpers.MyRenderable;
import com.zsoft.bullettutorial.MainGame;

import java.util.AbstractList;
import java.util.ArrayList;

/**
 * Created by zac520 on 12/10/14.
 */
public class MainScreen implements Screen {


    MainGame game;

    PerspectiveCamera cam;
    CameraInputController camController;
    ModelBatch modelBatch;
    Environment environment;

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

    private BoundingBox bounds = new BoundingBox();


    Model model;
    Model squareModel;
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
    GameObject playerInstance;
    ArrayMap<String, GameObject.Constructor> constructors;
    float spawnTimer;

    MyContactListener contactListener;
    btBroadphaseInterface broadphase;
    btCollisionWorld collisionWorld;

    final static short GROUND_FLAG = 1<<8;
    final static short OBJECT_FLAG = 1<<9;
    final static short ALL_FLAG = -1;

    protected Stage stage;
    protected Label label;
    protected BitmapFont font;
    private int visibleCount;
    private Vector3 position = new Vector3();


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
    GameObject myGroundObject;
    DebugDrawer debugDrawer;
    private final Vector3 tmpV1 = new Vector3();

    public float rotateAngle = 360f;

    private Vector3 originForVerticalMazeWall;
    private Vector3 originForHorizontalMazeWall;

    protected StringBuilder stringBuilder;

    HUD userInterfaceStage;
    OrthographicCamera HUDCam;

    private Texture texture;

    public Array<MyRenderable> renderables;
    public DefaultShader shader;
    public RenderContext renderContext;

    Mesh [][] meshes;
    Model myFinishedModel;
    ModelInstance finishedInstance;
    Mesh[] testMeshes;
    Matrix4 []testTransforms;



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
        cam.far = 75f;
        cam.update();




        //set up the texture for the walls
        FileHandle imageFileHandle = Gdx.files.internal("assets/concrete.jpg");
        texture = new Texture(imageFileHandle);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Nearest);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        defineModels();

        applyModels();

        makeWorld();




        //this is used to set the color of the objects when they hit the ground
        contactListener = new MyContactListener();


        makeGround();

        createMazeBorder();

        //initialize the visited array
        visitedSquares = new int[blocksWide][blocksHigh];
        zeroOutVisitedArray();

        initializeMazeWallArrays();

        createMazeWithoutRecursion();

        createAllMazeWalls();

        optimizeByMeshing();

        makePlayer();

        //debug drawing
        debugDrawer = new DebugDrawer();
        debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_MAX_DEBUG_DRAW_MODE);
        dynamicsWorld.setDebugDrawer(debugDrawer);

        //set up some stuff for the frustrum culling
        stage = new Stage();
        font = new BitmapFont();
        label = new Label(" ", new Label.LabelStyle(font, Color.WHITE));
        stage.addActor(label);
        stringBuilder = new StringBuilder();

        //set up the UI cam with its own separate stage

        HUDCam =new OrthographicCamera();
        HUDCam.setToOrtho(false, game.SCREEN_WIDTH, game.SCREEN_HEIGHT);
        userInterfaceStage =new HUD(game, this);
        //game.userInterfaceStage = userInterfaceStage;//we have to set it here because it needs a copy of this gamescreen
        userInterfaceStage.getViewport().setCamera(HUDCam);


        //allow for user control (user can fly the camera)
        camController = new CameraInputController(cam);
        Gdx.input.setInputProcessor(new InputMultiplexer(userInterfaceStage, camController));



    }


    private void optimizeByMeshing(){
        //from http://www.badlogicgames.com/forum/viewtopic.php?f=11&t=10842&hilit=merge+3D&start=20
        ArrayList<Mesh> meshesToMerge = new ArrayList<com.badlogic.gdx.graphics.Mesh>();
        ArrayList<Matrix4> transforms = new ArrayList<Matrix4>();
        testMeshes = new Mesh[instances.size];
        testTransforms = new Matrix4[instances.size];

        //we are going to divide the meshes into blocks of the size of cam.far. This we way will only render the meshes we can see
        float divisions = cam.far;

        //since the ground is at the origin, 0, a lot of values are negative. We will shift the locations so we can put into proper indexes
        float xMin = (GROUND_WIDTH/2);
        float yMin = (GROUND_HEIGHT/2);

        meshes = new Mesh[(int)(GROUND_HEIGHT/divisions)+1][(int)(GROUND_WIDTH/divisions)+1];

        Array<Array<Array<GameObject>>> tempInstances = new Array<Array<Array<GameObject>>>();
        //initialize the 3d array.
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
                meshes[x][y]=mergeMeshes(meshesToMerge,transforms);
                meshesToMerge.clear();
                transforms.clear();
            }


        }

//        for (int i = 0; i < instances.size; ++i)
//        {
//            ModelInstance mi = instances.get(i);
//
//            System.out.println("x: " + mi.transform.getTranslation(new Vector3()).x + "z: " + mi.transform.getTranslation(new Vector3()).z);
//            com.badlogic.gdx.graphics.Mesh mesh = mi.model.meshes.get(0);
//
//            if(mesh == null) continue;
//
//            meshesToMerge.add(mesh);
//
//            transforms.add(mi.transform);
//            testMeshes[i]= mesh;
//            testTransforms[i] = mi.transform;
//        }


        Model tempModel = new Model();
        ModelInstance tempInstance;
        renderables = new Array<MyRenderable>();
        for(int x = 0; x< meshes.length-1;x++){
            for(int y = 0; y<meshes[x].length-1;y++){
                System.out.println("x: " + x + "y: "+ y);
                tempModel = convertMeshToModel("node1", meshes[x][y]);
                tempInstance = new ModelInstance(tempModel, "node1");
                //set the texture
                //set the texture up
                NodePart blockPart = tempModel.getNode("node1").parts.get(0);
                MyRenderable myTempRenderable = new MyRenderable();
                blockPart.setRenderable(myTempRenderable);
                myTempRenderable.boundingBox = myTempRenderable.mesh.calculateBoundingBox();
                myTempRenderable.material = new Material( new TextureAttribute(TextureAttribute.Diffuse, texture));
                myTempRenderable.environment = environment;
                myTempRenderable.worldTransform.idt();
                renderables.add(myTempRenderable);


                //renderable.primitiveType = GL20.GL_POINTS;

            }
        }
        renderContext = new RenderContext(new DefaultTextureBinder(DefaultTextureBinder.WEIGHTED, 1));
        shader = new DefaultShader(renderables.first());
        shader.init();
        //meshes[0][0] = mergeMeshes(meshesToMerge, transforms);
        //meshes[0][0] = Mesh.create(true, testMeshes,testTransforms);
//        myFinishedModel = convertMeshToModel("node1", meshes[0][0]);
//        finishedInstance = new ModelInstance(myFinishedModel, "node1");

//        //set the texture
//        //set the texture up
//        NodePart blockPart = myFinishedModel.getNode("node1").parts.get(0);
//        renderable = new MyRenderable();
//        blockPart.setRenderable(renderable);
//        renderable.boundingBox = renderable.mesh.calculateBoundingBox();
//        renderable.material = new Material( new TextureAttribute(TextureAttribute.Diffuse, texture));
//        renderable.environment = environment;
//        renderable.worldTransform.idt();
//        renderContext = new RenderContext(new DefaultTextureBinder(DefaultTextureBinder.WEIGHTED, 1));
//        shader = new DefaultShader(renderable);
//        shader.init();
//        //renderable.primitiveType = GL20.GL_POINTS;
    }

    public Model convertMeshToModel(final String id, final Mesh mesh) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        builder.part(id, mesh, GL20.GL_TRIANGLES, new Material(new TextureAttribute(TextureAttribute.Ambient, texture)));
        return builder.end();
    }

    public static Mesh mergeMeshes(AbstractList<Mesh> meshes, AbstractList<Matrix4> transformations)
    {
        if(meshes.size() == 0) return null;

        int vertexArrayTotalSize = 0;
        int indexArrayTotalSize = 0;

        VertexAttributes va = meshes.get(0).getVertexAttributes();
        int vaA[] = new int [va.size()];
        for(int i=0; i<va.size(); i++)
        {
            vaA[i] = va.get(i).usage;
        }

        for(int i=0; i<meshes.size(); i++)
        {
            Mesh mesh = meshes.get(i);
            if(mesh.getVertexAttributes().size() != va.size())
            {
                meshes.set(i, copyMesh(mesh, true, false, vaA));
            }

            vertexArrayTotalSize += mesh.getNumVertices() * mesh.getVertexSize() / 4;
            indexArrayTotalSize += mesh.getNumIndices();
        }

        final float vertices[] = new float[vertexArrayTotalSize];
        final short indices[] = new short[indexArrayTotalSize];

        int indexOffset = 0;
        int vertexOffset = 0;
        int vertexSizeOffset = 0;
        int vertexSize = 0;

        for(int i=0; i<meshes.size(); i++)
        {
            Mesh mesh = meshes.get(i);

            int numIndices = mesh.getNumIndices();
            int numVertices = mesh.getNumVertices();
            vertexSize = mesh.getVertexSize() / 4;
            int baseSize = numVertices * vertexSize;
            VertexAttribute posAttr = mesh.getVertexAttribute(mesh.getVertexAttributes().get(0).usage);
            int offset = posAttr.offset / 4;
            int numComponents = posAttr.numComponents;

            { //uzupelnianie tablicy indeksow
                mesh.getIndices(indices, indexOffset);
                for(int c = indexOffset; c < (indexOffset + numIndices); c++)
                {
                    indices[c] += vertexOffset;
                }
                indexOffset += numIndices;
            }

            mesh.getVertices(0, baseSize, vertices, vertexSizeOffset);
            Mesh.transform(transformations.get(i), vertices, vertexSize, offset, numComponents, vertexOffset, numVertices);
            vertexOffset += numVertices;
            vertexSizeOffset += baseSize;
        }

        Mesh result = new Mesh(true, vertexOffset, indices.length, meshes.get(0).getVertexAttributes());
        result.setVertices(vertices);
        result.setIndices(indices);
        return result;
    }

    public static Mesh copyMesh(Mesh meshToCopy, boolean isStatic, boolean removeDuplicates, final int[] usage) {
        // TODO move this to a copy constructor?
        // TODO duplicate the buffers without double copying the data if possible.
        // TODO perhaps move this code to JNI if it turns out being too slow.
        final int vertexSize = meshToCopy.getVertexSize() / 4;
        int numVertices = meshToCopy.getNumVertices();
        float[] vertices = new float[numVertices * vertexSize];
        meshToCopy.getVertices(0, vertices.length, vertices);
        short[] checks = null;
        VertexAttribute[] attrs = null;
        int newVertexSize = 0;
        if (usage != null) {
            int size = 0;
            int as = 0;
            for (int i = 0; i < usage.length; i++)
                if (meshToCopy.getVertexAttribute(usage[i]) != null) {
                    size += meshToCopy.getVertexAttribute(usage[i]).numComponents;
                    as++;
                }
            if (size > 0) {
                attrs = new VertexAttribute[as];
                checks = new short[size];
                int idx = -1;
                int ai = -1;
                for (int i = 0; i < usage.length; i++) {
                    VertexAttribute a = meshToCopy.getVertexAttribute(usage[i]);
                    if (a == null)
                        continue;
                    for (int j = 0; j < a.numComponents; j++)
                        checks[++idx] = (short)(a.offset/4 + j);
                    attrs[++ai] = new VertexAttribute(a.usage, a.numComponents, a.alias);
                    newVertexSize += a.numComponents;
                }
            }
        }
        if (checks == null) {
            checks = new short[vertexSize];
            for (short i = 0; i < vertexSize; i++)
                checks[i] = i;
            newVertexSize = vertexSize;
        }

        int numIndices = meshToCopy.getNumIndices();
        short[] indices = null;
        if (numIndices > 0) {
            indices = new short[numIndices];
            meshToCopy.getIndices(indices);
            if (removeDuplicates || newVertexSize != vertexSize) {
                float[] tmp = new float[vertices.length];
                int size = 0;
                for (int i = 0; i < numIndices; i++) {
                    final int idx1 = indices[i] * vertexSize;
                    short newIndex = -1;
                    if (removeDuplicates) {
                        for (short j = 0; j < size && newIndex < 0; j++) {
                            final int idx2 = j*newVertexSize;
                            boolean found = true;
                            for (int k = 0; k < checks.length && found; k++) {
                                if (tmp[idx2+k] != vertices[idx1+checks[k]])
                                    found = false;
                            }
                            if (found)
                                newIndex = j;
                        }
                    }
                    if (newIndex > 0)
                        indices[i] = newIndex;
                    else {
                        final int idx = size * newVertexSize;
                        for (int j = 0; j < checks.length; j++)
                            tmp[idx+j] = vertices[idx1+checks[j]];
                        indices[i] = (short)size;
                        size++;
                    }
                }
                vertices = tmp;
                numVertices = size;
            }
        }

        Mesh result;
        if (attrs == null)
            result = new Mesh(isStatic, numVertices, indices == null ? 0 : indices.length, meshToCopy.getVertexAttributes());
        else
            result = new Mesh(isStatic, numVertices, indices == null ? 0 : indices.length, attrs);
        result.setVertices(vertices, 0, numVertices * newVertexSize);
        result.setIndices(indices);
        return result;
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
        //create a cone character

        Texture tex = new Texture(Gdx.files.internal("assets/badlogic.jpg"));
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);

        characterObject = constructors.get("capsule").construct();
        characterObject.nodes.get(0).parts.get(0).material.clear();
        characterObject.nodes.get(0).parts.get(0).material.set(new Material( new TextureAttribute(TextureAttribute.Ambient, tex)));

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

        instances = new Array<GameObject>();
    }
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
        //Gdx.gl20.glEnable(GL20.GL_TEXTURE_2D);

        //rendered drawing
        modelBatch.begin(cam);
        //modelBatch.render(instances, environment);

        //render everything visible but the player (because the player is dynamic, and the rest is static)
//        visibleCount = 0;
//        for (final GameObject instance : instances) {//only render if it is visible
//            if (instance.isVisible(cam,instance)) {
//                modelBatch.render(instance, environment);
//                visibleCount++;
//            }
//        }


        //render the player
        //modelBatch.render(finishedInstance, environment);
        modelBatch.render(characterObject, environment);

        modelBatch.end();

//        texture.bind();
//        oneMesh.render(shader.program,0,3,3,true);


        renderContext.begin();
        shader.begin(cam, renderContext);
        for (final MyRenderable renderable : renderables) {
            if (cam.frustum.boundsInFrustum(renderable.boundingBox)) {
                shader.render(renderable);
            }
        }
        shader.end();
        renderContext.end();

        //debug drawing
//		debugDrawer.begin(cam);
//		dynamicsWorld.debugDrawWorld();
//		debugDrawer.end();

        final float myDelta = Math.min(1f / 30f, delta);

        //move the ground
//        angle = (angle + myDelta * speed) % 360f;
//        instances.get(0).transform.setTranslation(0, MathUtils.sinDeg(angle) * 2.5f, 0f);//activate TO MOVE BOX UP AND DOWN


        update();

        centerPlayerOnScreen();

        dynamicsWorld.stepSimulation(myDelta, 5, 1f/60f);

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
