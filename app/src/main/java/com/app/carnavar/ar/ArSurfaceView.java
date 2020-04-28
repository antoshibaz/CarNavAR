package com.app.carnavar.ar;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.app.carnavar.R;
import com.app.carnavar.utils.math.Matrix3;
import com.app.carnavar.utils.math.Matrix4;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.math.Vector3;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ArSurfaceView implements GLSurfaceView.Renderer {

    public static final String TAG = ArSurfaceView.class.getSimpleName();

    private Activity context;
    private GLSurfaceView glSurfaceView;

    private Camera camera;
    private ArLane arLane;

    public ArSurfaceView() {
    }

    public void onCreate(Activity activity) {
        glSurfaceView = activity.findViewById(R.id.ar_glsurface_view);

        // Set up renderer.
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setEGLContextClientVersion(2);
        //glSurfaceView.setZOrderOnTop(true);
        //glSurfaceView.setZOrderMediaOverlay(true);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8,
                16, 0); // Alpha used for plane blending.
//        glSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
        glSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        //glSurfaceView.setWillNotDraw(false);
        glSurfaceView.setRenderer(this);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
//        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // init renderers models
        arLane = new ArLane(activity);
        initTestRoutePoints();
    }

    public void onResume() {
        glSurfaceView.onResume();
    }

    public void onPause() {
        glSurfaceView.onPause();
    }

    public void update(Frame frame) {
        camera = frame.getCamera();
    }

    private com.google.ar.sceneform.math.Vector3[] arLaneBezier = new com.google.ar.sceneform.math.Vector3[4];

    private com.google.ar.sceneform.math.Vector3 testLaneP1 = new com.google.ar.sceneform.math.Vector3();
    private com.google.ar.sceneform.math.Vector3 testLaneP2 = new com.google.ar.sceneform.math.Vector3();
    private com.google.ar.sceneform.math.Vector3 testLaneP3 = new com.google.ar.sceneform.math.Vector3();
    private com.google.ar.sceneform.math.Vector3 testLaneP4 = new Vector3();

    private void initTestRoutePoints() {
        testLaneP1.x = 0;
        testLaneP1.y = -2;
        testLaneP1.z = 0;

        testLaneP2.x = 0;
        testLaneP2.y = -2;
        testLaneP2.z = -10;

        testLaneP3.x = 2;
        testLaneP3.y = -2;
        testLaneP3.z = -15;

        testLaneP4.x = 5;
        testLaneP4.y = -2;
        testLaneP4.z = -20;

        arLaneBezier[0] = testLaneP1;
        arLaneBezier[1] = testLaneP2;
        arLaneBezier[2] = testLaneP3;
        arLaneBezier[3] = testLaneP4;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 0f);
//    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        arLane.onSurfaceChanged();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        if (camera == null) {
            return;
        }

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        try {

            Pose cameraPose = camera.getDisplayOrientedPose();
            // draw ar lane
            // note: may be for camera set x=0 and y=0
            float[] laneParams = new float[4 * 3];
            float deltaX = cameraPose.extractTranslation().tx() - arLaneBezier[0].x;
            //float deltaY = cameraPose.ty() - arLane[index].y;
            float deltaZ = cameraPose.extractTranslation().tz() - arLaneBezier[0].z;
            for (int index = 0; index < arLaneBezier.length; index++) {

                laneParams[index * 3] = arLaneBezier[index].x + deltaX;
                laneParams[index * 3 + 1] = arLaneBezier[index].y;
                laneParams[index * 3 + 2] = arLaneBezier[index].z + deltaZ;
            }
            Matrix4 viewProjMatrix = ArLane.Companion.getViewProjectionMatrix(camera);
            Matrix4 modelMatrix = new Matrix4();
            Matrix3 normMatrix = modelMatrix.toMatrix3();
            arLane.draw(viewProjMatrix, modelMatrix, normMatrix, laneParams);
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }
}
