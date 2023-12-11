// RenderServiceResources.aidl
package com.kdg.toast.plugin;

// Declare any non-default types here with import statements

interface RenderServiceResources {
    void SetRenderHardwareBuffer(in HardwareBuffer hwb, int width, int height);
    void SetReadyToStart(boolean ready);
    void TriggerThread();
}