package com.app.carnavar.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

public class ImageUtils {

    public static final String TAG = "ImageUtils";

    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
    // are normalized to eight bits.
    static final int kMaxChannelValue = 262143;

    // Always prefer the native implementation if available.
    private static boolean useNativeConversion = false;

    static {
        try {
            System.loadLibrary("tensorflow_demo");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native library not found, native RGB -> YUV conversion may be unavailable.");
        }
    }

    /**
     * Utility method to compute the allocated size in bytes of a YUV420SP image of the given
     * dimensions.
     */
    public static int getYUVByteSize(final int width, final int height) {
        // The luminance plane requires 1 byte per pixel.
        final int ySize = width * height;

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        final int uvSize = ((width + 1) / 2) * ((height + 1) / 2) * 2;

        return ySize + uvSize;
    }

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     */
    public static void saveBitmap(final Bitmap bitmap) {
        saveBitmap(bitmap, "preview.png");
    }

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap   The bitmap to save.
     * @param filename The location to save the bitmap to.
     */
    public static void saveBitmap(final Bitmap bitmap, final String filename) {
        final String root =
                Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";
//        LOGGER.i("Saving %dx%d bitmap to %s.", bitmap.getWidth(), bitmap.getHeight(), root);
        final File myDir = new File(root);

        if (!myDir.mkdirs()) {
//            LOGGER.i("Make dir failed");
        }

        final String fname = filename;
        final File file = new File(myDir, fname);
        if (file.exists()) {
            file.delete();
        }
        try {
            final FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out);
            out.flush();
            out.close();
        } catch (final Exception e) {
//            LOGGER.e(e, "Exception!");
        }
    }

    public static void convertYUV420SPToARGB8888(byte[] input, int width, int height, int[] output) {
        if (useNativeConversion) {
            try {
                ImageUtils.convertYUV420SPToARGB8888(input, output, width, height, false);
                return;
            } catch (UnsatisfiedLinkError e) {
//                LOGGER.w(
//                        "Native YUV420SP -> RGB implementation not found, falling back to Java implementation");
                useNativeConversion = false;
            }
        }

        // Java implementation of YUV420SP to ARGB8888 converting
        final int frameSize = width * height;
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width;
            int u = 0;
            int v = 0;

            for (int i = 0; i < width; i++, yp++) {
                int y = 0xff & input[yp];
                if ((i & 1) == 0) {
                    v = 0xff & input[uvp++];
                    u = 0xff & input[uvp++];
                }

                output[yp] = YUV2RGB(y, u, v);
            }
        }
    }

    private static int YUV2RGB(int y, int u, int v) {
        // Adjust and check YUV values
        y = (y - 16) < 0 ? 0 : (y - 16);
        u -= 128;
        v -= 128;

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);
        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
        g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
        b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

        return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    }

    public static void convertYUV420ToARGB8888(
            byte[] yData,
            byte[] uData,
            byte[] vData,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            int[] out) {
        if (useNativeConversion) {
            try {
                convertYUV420ToARGB8888(
                        yData, uData, vData, out, width, height, yRowStride, uvRowStride, uvPixelStride, false);
                return;
            } catch (UnsatisfiedLinkError e) {
//                LOGGER.w(
//                        "Native YUV420 -> RGB implementation not found, falling back to Java implementation");
                useNativeConversion = false;
            }
        }

        int yp = 0;
        for (int j = 0; j < height; j++) {
            int pY = yRowStride * j;
            int pUV = uvRowStride * (j >> 1);

            for (int i = 0; i < width; i++) {
                int uv_offset = pUV + (i >> 1) * uvPixelStride;

                out[yp++] = YUV2RGB(0xff & yData[pY + i], 0xff & uData[uv_offset], 0xff & vData[uv_offset]);
            }
        }
    }

    /**
     * Converts YUV420 semi-planar data to ARGB 8888 data using the supplied width and height. The
     * input and output must already be allocated and non-null. For efficiency, no error checking is
     * performed.
     *
     * @param input    The array of YUV 4:2:0 input data.
     * @param output   A pre-allocated array for the ARGB 8:8:8:8 output data.
     * @param width    The width of the input image.
     * @param height   The height of the input image.
     * @param halfSize If true, downsample to 50% in each dimension, otherwise not.
     */
    private static native void convertYUV420SPToARGB8888(
            byte[] input, int[] output, int width, int height, boolean halfSize);

    /**
     * Converts YUV420 semi-planar data to ARGB 8888 data using the supplied width and height. The
     * input and output must already be allocated and non-null. For efficiency, no error checking is
     * performed.
     *
     * @param y
     * @param u
     * @param v
     * @param uvPixelStride
     * @param width         The width of the input image.
     * @param height        The height of the input image.
     * @param halfSize      If true, downsample to 50% in each dimension, otherwise not.
     * @param output        A pre-allocated array for the ARGB 8:8:8:8 output data.
     */
    private static native void convertYUV420ToARGB8888(
            byte[] y,
            byte[] u,
            byte[] v,
            int[] output,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            boolean halfSize);

    /**
     * Converts YUV420 semi-planar data to RGB 565 data using the supplied width and height. The input
     * and output must already be allocated and non-null. For efficiency, no error checking is
     * performed.
     *
     * @param input  The array of YUV 4:2:0 input data.
     * @param output A pre-allocated array for the RGB 5:6:5 output data.
     * @param width  The width of the input image.
     * @param height The height of the input image.
     */
    private static native void convertYUV420SPToRGB565(
            byte[] input, byte[] output, int width, int height);

    /**
     * Converts 32-bit ARGB8888 image data to YUV420SP data. This is useful, for instance, in creating
     * data to feed the classes that rely on raw camera preview frames.
     *
     * @param input  An array of input pixels in ARGB8888 format.
     * @param output A pre-allocated array for the YUV420SP output data.
     * @param width  The width of the input image.
     * @param height The height of the input image.
     */
    private static native void convertARGB8888ToYUV420SP(
            int[] input, byte[] output, int width, int height);

    /**
     * Converts 16-bit RGB565 image data to YUV420SP data. This is useful, for instance, in creating
     * data to feed the classes that rely on raw camera preview frames.
     *
     * @param input  An array of input pixels in RGB565 format.
     * @param output A pre-allocated array for the YUV420SP output data.
     * @param width  The width of the input image.
     * @param height The height of the input image.
     */
    private static native void convertRGB565ToYUV420SP(
            byte[] input, byte[] output, int width, int height);

    /**
     * Returns a transformation matrix from one reference frame into another. Handles cropping (if
     * maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth            Width of source frame.
     * @param srcHeight           Height of source frame.
     * @param dstWidth            Width of destination frame.
     * @param dstHeight           Height of destination frame.
     * @param applyRotation       Amount of rotation to apply from one frame to another. Must be a multiple
     *                            of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     *                            cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    public static Matrix getTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean maintainAspectRatio) {
        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
//                LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }

    public static Bitmap getColoredMasks(Bitmap inputImage, int[] masks, int[] classColors) {
        Bitmap imgColoredMasks = Bitmap.createBitmap(inputImage, 0, 0, inputImage.getWidth(), inputImage.getHeight());
        int[] coloredMasks = new int[masks.length];
        for (int i = 0; i < masks.length; i++) {
            coloredMasks[i] = classColors[masks[i]];
        }

        imgColoredMasks.setPixels(coloredMasks, 0, inputImage.getWidth(),
                0, 0, inputImage.getWidth(), inputImage.getHeight());

        return imgColoredMasks;
    }

    // TODO: implement getImageWithOverlayedMasks(...)
    public static Bitmap getImageWithOverlayedMasksRGB(Bitmap inputImage, int[] masks, int[] classColors, float masksAlphaVal) {
        Bitmap outputImg = Bitmap.createBitmap(inputImage, 0, 0, inputImage.getWidth(), inputImage.getHeight());
        int[] coloredMasks = new int[masks.length];
        for (int i = 0; i < masks.length; i++) {
            coloredMasks[i] = classColors[masks[i]];
        }

        int[] outputImgBuff = new int[coloredMasks.length];
        for (int y = 0; y < outputImg.getHeight(); y++) {
            for (int x = 0; x < outputImg.getWidth(); x++) {
                final int imgPx = inputImage.getPixel(x, y);
                final int maskPx = coloredMasks[y * outputImg.getWidth() + x];

                int imgA = ((imgPx >> 24) & 0xFF);
                int imgB = ((imgPx >> 16) & 0xFF);
                int imgG = ((imgPx >> 8) & 0xFF);
                int imgR = ((imgPx) & 0xFF);
                int maskA = ((maskPx >> 24) & 0xFF);
                if (maskA == 0) maskA = 255;
                int maskB = ((maskPx >> 16) & 0xFF);
                int maskG = ((maskPx >> 8) & 0xFF);
                int maskR = ((maskPx) & 0xFF);

                float beta = 1.0f - masksAlphaVal;
                int dstA = (int) (masksAlphaVal * maskA + beta * imgA);
                int dstB = (maskB == 0) ? imgB : (int) (masksAlphaVal * maskB + beta * imgB);
                int dstG = (maskG == 0) ? imgG : (int) (masksAlphaVal * maskG + beta * imgG);
                int dstR = (maskR == 0) ? imgR : (int) (masksAlphaVal * maskR + beta * imgR);

                final int dstPx = (dstA & 0xff) << 24 | (dstB & 0xff) << 16 | (dstG & 0xff) << 8 | (dstR & 0xff);
                outputImgBuff[y * outputImg.getWidth() + x] = dstPx;
            }
        }

        outputImg.setPixels(outputImgBuff, 0, outputImg.getWidth(),
                0, 0, outputImg.getWidth(), outputImg.getHeight());
        return outputImg;
    }

    public static int[] getRandomColorsForClasses(int numClasses, int alphaChannelVal) {
        Random rand = new Random(System.currentTimeMillis());
        int[] colors = new int[numClasses];
        for (int i = 0; i < numClasses; i++) {
            colors[i] = Color.argb(
                    alphaChannelVal,
                    (int) (255 * rand.nextFloat()),
                    (int) (255 * rand.nextFloat()),
                    (int) (255 * rand.nextFloat()));
        }

        return colors;
    }
}