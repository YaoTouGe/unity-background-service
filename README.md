This project is forked from [unity-background-service](https://github.com/nintendaii/unity-background-service). The original doc is [here](/old-doc.md).

In order to test renderdoc capture background render service, I modify this project to draw the renderdoc logo with shader in a background service. The result is shared with unity player process with hardware buffer, it's not as easy as I thought:)

Firstly I test sharing texture with shared EGLContext, I need to use native cpp to do some EGLContext stuff. But it turns out only contexts in the same process is allow to be shared. My render service is a standalone process, and even I made it the same process with unity player, renderdoc can capture the drawcalls in service thread, since all the gles functions are injected when unity player launches.

After searching I find that the only way to share texture between processes is hardware buffer, so I create a hardware buffer and pass to the service process through Parcelable, but again after testing it doesn't work, hardware buffer can marshel correctly when using Parcelable.writeValue.

I search again and also asked gpt many times, it seems that AIDL can solve this problem and there are some examples that use AIDL to share HardwareBuffer between processes. I thought I find the correct way but when create external texture and sample it in Unity, nothing shows up.

After final search and read the code of Vuplex WebView, when using a hardware buffer converted texture in unity, I should use the GL_TEXTURE_EXTERNAL_OES, and also Sample it with this [extension](https://registry.khronos.org/OpenGL/extensions/OES/OES_EGL_image_external.txt).

Finally the renderdoc logo shows up in unity player, now I can move on how to made renderdoc launch and inject a service and capture frames! Good luck with me!