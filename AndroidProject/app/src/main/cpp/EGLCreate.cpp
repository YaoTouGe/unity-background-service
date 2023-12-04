#include <EGL/egl.h>
#include <jni.h>
#include <string>
#include <android/log.h>

#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, "[render service]", __VA_ARGS__))
#define LOGW(...) \
  ((void)__android_log_print(ANDROID_LOG_WARN, "[render service]", __VA_ARGS__))
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, "[render service]", __VA_ARGS__))
static EGLDisplay display;
static EGLContext context;
static EGLSurface pbuffer;

int configAttribs[] =
        {
            EGL_RED_SIZE,
            8,
            EGL_GREEN_SIZE,
            8,
            EGL_BLUE_SIZE,
            8,
            EGL_ALPHA_SIZE,
            8,
            EGL_SURFACE_TYPE,
            EGL_WINDOW_BIT,
            EGL_COLOR_BUFFER_TYPE,
            EGL_RGB_BUFFER,
            EGL_RENDERABLE_TYPE,
            EGL_OPENGL_ES3_BIT,
            EGL_NONE
        };
EGLConfig config;

const char* getError() {
    int errorCode = eglGetError();
    switch (errorCode) {
        case EGL_SUCCESS:
            return "函数执行成功，无错误---没有错误";
        case EGL_NOT_INITIALIZED:
            return "对于特定的 Display, EGL 未初始化，或者不能初始化---没有初始化";

        case EGL_BAD_ACCESS:
            return "EGL 无法访问资源(如 Context 绑定在了其他线程)---访问失败";

        case EGL_BAD_ALLOC:
            return "对于请求的操作，EGL 分配资源失败---分配失败";

        case EGL_BAD_ATTRIBUTE:
            return "未知的属性，或者属性已失效---错误的属性";

        case EGL_BAD_CONTEXT:
            return "EGLContext(上下文) 错误或无效---错误的上下文";

        case EGL_BAD_CONFIG:
            return "EGLConfig(配置) 错误或无效---错误的配置";

        case EGL_BAD_DISPLAY:
            return "EGLDisplay(显示) 错误或无效---错误的显示设备对象";

        case EGL_BAD_SURFACE:
            return ("未知的属性，或者属性已失效---错误的Surface对象");

        case EGL_BAD_CURRENT_SURFACE:
            return ("窗口，缓冲和像素图(三种 Surface)的调用线程的 Surface 错误或无效---当前Surface对象错误");

        case EGL_BAD_MATCH:
            return ("参数不符(如有效的 Context 申请缓冲，但缓冲不是有效的 Surface 提供)---无法匹配");

        case EGL_BAD_PARAMETER:
            return ("错误的参数");

        case EGL_BAD_NATIVE_PIXMAP:
            return ("NativePixmapType 对象未指向有效的本地像素图对象---错误的像素图");

        case EGL_BAD_NATIVE_WINDOW:
            return ("NativeWindowType 对象未指向有效的本地窗口对象---错误的本地窗口对象");

        case EGL_CONTEXT_LOST:
            return ("电源错误事件发生，Open GL重新初始化，上下文等状态重置---上下文丢失");

        default:
            break;
    }

    return "";
}

extern "C" JNIEXPORT int JNICALL
Java_com_kdg_toast_plugin_RenderService_InitEGLContext(
        JNIEnv* env,
        jobject /* this */,
        jlong mainContext) {
    display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    int major, minor;
    bool initialized = eglInitialize(display, &major, &minor);
    if (!initialized)
    {
        LOGE("initialize egl failed!");
        return false;
    }

    int configNum;
    if (!eglChooseConfig(display, configAttribs, &config, 1, &configNum))
    {
        LOGE("eglChooseConfig failed");
        return false;
    }

    int ctxAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    context = eglCreateContext(display, config, (EGLContext)mainContext, ctxAttribs);

    if (context == EGL_NO_CONTEXT)
    {
        LOGE("eglCreateContext failed! %s %lld", getError(), (long long)mainContext);
        return false;
    }

    // We do offscreen background rendering, use EGL PBuffer now (use hardware buffer later).
    int attribs[] =
            {
            EGL_WIDTH,
            1024,
            EGL_HEIGHT,
            1024,
            EGL_TEXTURE_FORMAT,
            EGL_TEXTURE_RGBA,
            EGL_TEXTURE_TARGET,
            EGL_TEXTURE_2D,
            EGL_NONE
            };
    pbuffer = eglCreatePbufferSurface(display, config, attribs);
    if (pbuffer == EGL_NO_SURFACE)
    {
        LOGE("pbuffer create failed!");
        return false;
    }

    if (!eglMakeCurrent(display, pbuffer, pbuffer, context))
    {
        LOGE("eglMakeCurrent failed!");
        return false;
    }
    // Finally bind eglContext with surface
    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kdg_toast_plugin_RenderService_DestroyEGLContext(
        JNIEnv* env,
        jobject /* this */)
{
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, pbuffer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kdg_toast_plugin_Bridge_GetCurrentContext(
        JNIEnv* env,
        jclass /* this */)
{
    EGLContext curCtx = eglGetCurrentContext();
    EGLDisplay dsp = eglGetCurrentDisplay();
    EGLConfig cfg;
    int numCfg;
    eglGetConfigs(dsp, &cfg, 1, &numCfg);
    /*
        EGL_RED_SIZE,
        8,
        EGL_GREEN_SIZE,
        8,
        EGL_BLUE_SIZE,
        8,
        EGL_ALPHA_SIZE,
        8,
        EGL_SURFACE_TYPE,
        EGL_WINDOW_BIT,
        EGL_COLOR_BUFFER_TYPE,
        EGL_RGB_BUFFER,
        EGL_RENDERABLE_TYPE,
        EGL_OPENGL_ES3_BIT,
        EGL_NONE
     */
    int redSize, greenSize, blueSize, alphaSize, sfType, bufType, renderableType;
    eglGetConfigAttrib(dsp, cfg, EGL_RED_SIZE, &redSize);
    eglGetConfigAttrib(dsp, cfg, EGL_GREEN_SIZE, &greenSize);
    eglGetConfigAttrib(dsp, cfg, EGL_BLUE_SIZE, &blueSize);
    eglGetConfigAttrib(dsp, cfg, EGL_ALPHA_SIZE, &alphaSize);
    eglGetConfigAttrib(dsp, cfg, EGL_SURFACE_TYPE, &sfType);
    eglGetConfigAttrib(dsp, cfg, EGL_COLOR_BUFFER_TYPE, &bufType);
    eglGetConfigAttrib(dsp, cfg, EGL_RENDERABLE_TYPE, &renderableType);

    LOGE("GetCurrentContext %x %x %x %x %x %x %x", redSize, greenSize, blueSize, alphaSize, sfType, bufType, renderableType);
    return (long long)curCtx;
}