// HardwareBufferCallback.aidl
package com.kdg.toast.plugin;

// Declare any non-default types here with import statements

oneway interface HardwareBufferCallback {
    void ObtainHardwareBuffer(in HardwareBuffer hwb, int width ,int height);
}