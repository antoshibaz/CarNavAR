#include <jni.h>
#include <string>

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>

using namespace std;
using namespace cv;

extern "C" JNIEXPORT jstring JNICALL
Java_com_app_opencvndkapp_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_app_opencvndkapp_MainActivity_salt(JNIEnv *env, jobject thiz, jlong mat_addr_gray,
                                            jint nbr_elem) {
    Mat &mGr = *(Mat *) mat_addr_gray;
    for (int k = 0; k < nbr_elem; k++) {
        int i = rand() % mGr.cols;
        int j = rand() % mGr.rows;
        mGr.at<uchar>(j, i) = 255;
    }
}