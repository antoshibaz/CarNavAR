package com.app.carnavar.cv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.app.carnavar.utils.ImageUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class CvInferenceThread extends HandlerThread {

    public static final String TAG = CvInferenceThread.class.getSimpleName();

    private Context context;
    private Handler handler;
    private Handler callbackHandler;

    boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;

    private Runnable imageConverter = null;
    private Runnable postInferenceCallback = null;

    private TFLiteImageSemanticSegmenter imageSegmenter;
    private String[] classes;
    private int[] coloredMaskClasses;
    private Bitmap rgbCameraFrameBitmap = null;
    private Bitmap segmentedFrameBitmap = null;
    private Matrix transformMat;

    private InferenceCallback inferenceCallback;

    public interface InferenceCallback {
        void inferenceCompleted(Bitmap inferencedImage);
    }

    public void setInferenceCallback(InferenceCallback inferenceCallback) {
        this.inferenceCallback = inferenceCallback;
    }

    private CvInferenceThread(Context context, String name) {
        super(name);
        this.context = context;
    }

    private CvInferenceThread(Context context, String name, int priority) {
        super(name, priority);
        this.context = context;
    }

    public void processFrame(Image image) {
        if (image == null || imageSegmenter == null) {
            return;
        }

        if (rgbBytes == null) {
            rgbBytes = new int[image.getWidth() * image.getHeight()];
        }

        if (isProcessingFrame) {
            image.close();
            return;
        }

        isProcessingFrame = true;
        final Image.Plane[] planes = image.getPlanes();
        fillBytes(planes, yuvBytes);
        yRowStride = planes[0].getRowStride();
        final int uvRowStride = planes[1].getRowStride();
        final int uvPixelStride = planes[1].getPixelStride();

        imageConverter = () -> ImageUtils.convertYUV420ToARGB8888(
                yuvBytes[0],
                yuvBytes[1],
                yuvBytes[2],
                image.getWidth(),
                image.getHeight(),
                yRowStride,
                uvRowStride,
                uvPixelStride,
                rgbBytes
        );

        postInferenceCallback = () -> {
            image.close();
            isProcessingFrame = false;
        };

        // process inference
        if (rgbCameraFrameBitmap == null) {
            rgbCameraFrameBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        }
        rgbCameraFrameBitmap.setPixels(getRgbBytes(), 0, image.getWidth(), 0, 0,
                image.getWidth(), image.getHeight());
        if (transformMat == null) {
            transformMat = new Matrix();
            transformMat.postRotate(-90);
        }
        final Bitmap rotCamFrame = Bitmap.createBitmap(rgbCameraFrameBitmap, 0, 0, image.getWidth(), image.getHeight(),
                transformMat, true);

        if (coloredMaskClasses == null) {
            coloredMaskClasses = ImageUtils.getRandomColorsForClasses(imageSegmenter.getNumLabelClasses(), 200);
            coloredMaskClasses[0] = Color.TRANSPARENT;
        }

        handler.post(() -> {
            long startTime = SystemClock.uptimeMillis();
            segmentedFrameBitmap = imageSegmenter.predictSegmentation(rotCamFrame, coloredMaskClasses);
            long endTime = SystemClock.uptimeMillis();
            Log.i(TAG, "Segmentation inference time(ms): " + String.valueOf(endTime - startTime));
            StringBuilder sbDetCl = new StringBuilder();
            for (Integer cl : imageSegmenter.getHitClassIdxArray()) {
                sbDetCl.append(classes[cl] + " ");
            }
            Log.i(TAG, "Detected classes: " + sbDetCl);

            if (inferenceCallback != null) {
                inferenceCallback.inferenceCompleted(segmentedFrameBitmap);
            }
            readyForNextImage();
        });
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    private void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    private void shutdown() {
    }

    @Override
    public boolean quit() {
        shutdown();
        return super.quit();
    }

    @Override
    public boolean quitSafely() {
        shutdown();
        return super.quitSafely();
    }

    public void close() {
        this.quitSafely();
        try {
            this.join();
        } catch (final InterruptedException e) {
            Log.e(TAG, CvInferenceThread.TAG + " throw InterruptedException");
        }
    }

    public static CvInferenceThread createAndStart(Context context) {
        CvInferenceThread cvInferenceThread = new CvInferenceThread(context, TAG, Process.THREAD_PRIORITY_DEFAULT);
        initAndStart(cvInferenceThread);
        return cvInferenceThread;
    }

    public static CvInferenceThread createAndStart(Context context, String name) {
        CvInferenceThread cvInferenceThread = new CvInferenceThread(context, name, Process.THREAD_PRIORITY_DEFAULT);
        initAndStart(cvInferenceThread);
        return cvInferenceThread;
    }

    public static CvInferenceThread createAndStart(Context context, String name, int priority) {
        CvInferenceThread cvInferenceThread = new CvInferenceThread(context, name, priority);
        initAndStart(cvInferenceThread);
        return cvInferenceThread;
    }

    private static void initAndStart(CvInferenceThread cvInferenceThread) {
        cvInferenceThread.start();
        cvInferenceThread.handler = new Handler(cvInferenceThread.getLooper());
        cvInferenceThread.handler.post(() -> {});
        try {
            cvInferenceThread.imageSegmenter = new MobileNetDeepLabV3Float(cvInferenceThread.context);
            cvInferenceThread.imageSegmenter.setNumThreads(2);
            cvInferenceThread.imageSegmenter.useCpu();
            //cvInferenceThread.imageSegmenter.useNNAPI();
            cvInferenceThread.classes = cvInferenceThread.imageSegmenter.getClassLabels();
            cvInferenceThread.callbackHandler = new Handler(Looper.myLooper());
        } catch (IOException e) {
            e.printStackTrace();
            cvInferenceThread.close();
        }
    }
}
