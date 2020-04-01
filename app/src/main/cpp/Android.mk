LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_INSTALL_MODULES := on
OPENCV_CAMERA_MODULES := on
OPENCV_LIB_TYPE := SHARED
OPENCV_MY_PATH := /home/tony/OpenCV-android-sdk
include $(OPENCV_MY_PATH)/sdk/native/jni/OpenCV.mk

LOCAL_MODULE := native-lib
LOCAL_SRC_FILES := native-lib.cpp
include $(BUILD_SHARED_LIBRARY)