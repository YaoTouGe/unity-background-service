// RenderServiceResources.aidl
package com.kdg.toast.plugin;

// Declare any non-default types here with import statements
import com.kdg.toast.plugin.HardwareBufferCallback;

interface RenderServiceResources {
    void RegisterHardwareBufferCallback(HardwareBufferCallback callback);
    void UnregisterHardwareBufferCallback(HardwareBufferCallback callback);
}