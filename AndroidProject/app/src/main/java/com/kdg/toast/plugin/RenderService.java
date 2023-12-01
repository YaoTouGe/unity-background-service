package com.kdg.toast.plugin;

import android.app.Service;
import android.content.Intent;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.IBinder;
import android.opengl.EGL14;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Date;

public class RenderService extends Service {
    public static EGLContext mainContext = EGL14.EGL_NO_CONTEXT;
    public static int colorAttachmentFromUnity = 0;
    public static int colorWidth;
    public static int colorHeight;
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class RenderThread extends Thread
    {
        EGLDisplay display;

        int[] configAttribs = new int[]
                {
                        EGL14.EGL_RED_SIZE,
                        8,
                        EGL14.EGL_GREEN_SIZE,
                        8,
                        EGL14.EGL_BLUE_SIZE,
                        8,
                        EGL14.EGL_ALPHA_SIZE,
                        8,
                        EGL14.EGL_SURFACE_TYPE,
                        EGL14.EGL_WINDOW_BIT,
                        EGL14.EGL_COLOR_BUFFER_TYPE,
                        EGL14.EGL_RGB_BUFFER,
                        EGL14.EGL_RENDERABLE_TYPE,
                        EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_NONE
                };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        int[] version = new int[2];
        EGLContext context;
        EGLSurface pbuffer;

        int[] fbo = new int[1];
        int[] rbo = new int[1];

        int program;

        private Handler m_handler;
        public void Trigger()
        {
            Message.obtain(m_handler, 12345, null).sendToTarget();
        }

        public void Quit()
        {
            Message.obtain(m_handler, 12345, "quit").sendToTarget();
        }

        public boolean IsValid() {return m_handler != null;}

        private boolean InitEglContext()
        {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            boolean initialized = EGL14.eglInitialize(display, version, 0, version, 1);
            if (!initialized)
            {
                Log.e("[render service]","initialize egl failed!");
                return false;
            }


            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, configs.length, numConfigs, 0))
            {
                Log.e("[render service]", "eglChooseConfig failed");
                return false;
            }

            int[] ctxAttribs = new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE};
            context = EGL14.eglCreateContext(display, configs[0], mainContext, ctxAttribs, 0);

            if (context == EGL14.EGL_NO_CONTEXT)
            {
                Log.e("[render service", "eglCreateContext failed!");
                return false;
            }

            // We do offscreen background rendering, use EGL PBuffer now (use hardware buffer later).
            int[] attribs = new int[]
                    {
                            EGL14.EGL_WIDTH,
                            1024,
                            EGL14.EGL_HEIGHT,
                            1024,
                            EGL14.EGL_TEXTURE_FORMAT,
                            EGL14.EGL_TEXTURE_RGBA,
                            EGL14.EGL_TEXTURE_TARGET,
                            EGL14.EGL_TEXTURE_2D,
                            EGL14.EGL_NONE
                    };
            pbuffer = EGL14.eglCreatePbufferSurface(display, configs[0], attribs, 0);
            if (pbuffer == EGL14.EGL_NO_SURFACE)
            {
                Log.e("[render service]", "pbuffer create failed!" + getError());
                return false;
            }

            if (!EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context))
            {
                Log.e("[render service]", "eglMakeCurrent failed!");
                return false;
            }
            // Finally bind eglContext with surface
            return true;
        }

        @Override
        public void run() {
            Looper.prepare();

            if (!InitEglContext())
            {
                Toast.makeText(RenderService.this, "Egl init failed!", Toast.LENGTH_SHORT).show();
                Looper.myLooper().quit();
                return;
            }
            else
            {
                Toast.makeText(RenderService.this, "Egl init successful!", Toast.LENGTH_SHORT).show();
            }

            // Create FBO from unity color texture
            GLES30.glGenFramebuffers(1, fbo, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0]);

            //GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorAttachmentFromUnity);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, colorAttachmentFromUnity,0);

            GLES30.glGenRenderbuffers(1, rbo, 0);
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbo[0]);
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH24_STENCIL8, colorWidth, colorHeight);
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_STENCIL_ATTACHMENT, GLES30.GL_RENDERBUFFER, rbo[0]);

            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE)
                Log.e("[render service]", "framebuffer incomplete!");

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

            InitShaders();

            m_handler = new Handler(Looper.myLooper(), new Handler.Callback()
            {
                @Override
                public boolean handleMessage(@NonNull Message msg)
                {
                    if (msg.what != 12345)
                        return false;

                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0]);
                    GLES30.glColorMask(true, true, true, true);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

                    // draw off screen
                    GLES30.glClearColor(0, 0, 0, 1);

                    GLES30.glUseProgram(program);

                    // set the resolution
                    int loc = GLES30.glGetUniformLocation(program, "iResolution");
                    GLES30.glUniform2f(loc, colorWidth, colorHeight);

                    double splashFadePeriod = 45 * 60.0f;
                    Date date = new Date();

                    // loop every 15 minutes, with the fade at the end of the period
                    double x = splashFadePeriod - (date.getTime() / 1000.f % splashFadePeriod);

                    // multiply by 4 so the fade happens over a little more than a second instead of
                    // 6.28 seconds.
                    x = x * 4;

                    // do one cos loop and finish
                    if(x >= Math.PI * 2.0f)
                        x = Math.PI * 2.0f;

                    double fade = Math.cos(x);

                    // set the fade
                    loc = GLES30.glGetUniformLocation(program, "fFade");
                    GLES30.glUniform1f(loc, (float)fade);

                    // fullscreen triangle
                    float[] verts = {
                            -1.0f, -1.0f,    // vertex 0
                            3.0f,  -1.0f,    // vertex 1
                            -1.0f, 3.0f,     // vertex 2
                    };
                    ByteBuffer vertsBuffer = ByteBuffer.allocateDirect(24);
                    vertsBuffer.order(ByteOrder.nativeOrder());
                    FloatBuffer floatBuffer = vertsBuffer.asFloatBuffer();
                    floatBuffer.put(verts);

                    GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, vertsBuffer);
                    GLES30.glEnableVertexAttribArray(0);

                    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);

                    GLES30.glFinish();
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

                    // debug read back
                    //ReadPixelAndCheckValue();
                    try
                    {
                        Thread.sleep(33);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    // trigger next loop
                    Message.obtain(m_handler, 12345, null).sendToTarget();
                    // Log.i("[render service]", "render service tick");

                    if (msg.obj != null)
                    {
                        Toast.makeText(RenderService.this, "Egl destroy!", Toast.LENGTH_SHORT).show();
                        // Destroy everything before quit
                        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                        EGL14.eglDestroyContext(display, context);
                        EGL14.eglDestroySurface(display, pbuffer);
                        Looper.myLooper().quit();
                    }
                    return true;
                }
            });
            Looper.loop();
        }

        private void InitShaders()
        {
            // simple pass through shader for the fullscreen triangle verts
            String vertex =
                    "attribute vec2 pos;\n"+
                            "void main() { gl_Position = vec4(pos, 0.5, 1.0); }";

            String fragment =
                    "precision highp float;\n"+
                            "float circle(in vec2 uv, in vec2 centre, in float radius)\n"+
                            "{\n"+
                            "return length(uv - centre) - radius;\n"+
                            "}\n"+
                            "// distance field for RenderDoc logo\n"+
                            "float logo(in vec2 uv)\n"+
                            "{\n"+
                            "// add the outer ring\n"+
                            "float ret = circle(uv, vec2(0.5, 0.5), 0.275);\n"+
                            "// add the upper arc\n"+
                            "ret = min(ret, circle(uv, vec2(0.5, -0.37), 0.71));\n"+
                            "// subtract the inner ring\n"+
                            "ret = max(ret, -circle(uv, vec2(0.5, 0.5), 0.16));\n"+
                            "// subtract the lower arc\n"+
                            "ret = max(ret, -circle(uv, vec2(0.5, -1.085), 1.3));\n"+
                            "return ret;\n"+
                            "}\n"+
                            "uniform vec2 iResolution;\n"+
                            "uniform float fFade;\n"+
                            "void main()\n"+
                            "{\n"+
                            "vec2 uv = gl_FragCoord.xy / iResolution.xy;\n"+
                            "// centre the UVs in a square. This assumes a landscape layout.\n"+
                            "uv.x = 0.5 - (uv.x - 0.5) * (iResolution.x / iResolution.y);\n"+
                            "// this constant here can be tuned depending on DPI to increase AA\n"+
                            "float edgeWidth = 10.0/max(iResolution.x, iResolution.y);\n"+
                            "float smoothdist = smoothstep(0.0, edgeWidth, clamp(logo(uv), 0.0, 1.0));\n"+
                            "// the green is #3bb779\n"+
                            "gl_FragColor = mix(vec4(1.0), vec4(0.2314, 0.7176, 0.4745, 1.0), smoothdist)*fFade;\n"+
                            "}\n";

            // compile the shaders and link into a program
            int vs = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER);
            GLES30.glShaderSource(vs, vertex);
            GLES30.glCompileShader(vs);

            IntBuffer status = IntBuffer.allocate(1);
            GLES30.glGetShaderiv(vs, GLES30.GL_COMPILE_STATUS, status);
            if(status.get(0) == 0)
            {
                String log = GLES30.glGetShaderInfoLog(vs);
                Log.e("[render service]", "VS error: " + log);
            }

            int fs = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER);
            GLES30.glShaderSource(fs, fragment);
            GLES30.glCompileShader(fs);

            GLES30.glGetShaderiv(fs, GLES30.GL_COMPILE_STATUS, status);
            if(status.get(0) == 0)
            {
                String log = GLES30.glGetShaderInfoLog(fs);
                Log.e("[render service]", "fs error: " + log);
            }

            program = GLES30.glCreateProgram();
            GLES30.glAttachShader(program, vs);
            GLES30.glAttachShader(program, fs);
            GLES30.glLinkProgram(program);

            GLES30.glGetShaderiv(program, GLES30.GL_LINK_STATUS, status);
            if(status.get(0) == 0)
            {
                String log = GLES30.glGetProgramInfoLog(program);
                Log.e("[render service]", "Program error: " + log);
            }
        }

        private void ReadPixelAndCheckValue()
        {
            ByteBuffer pixelBuf = ByteBuffer.allocate(colorWidth * colorHeight * 4);
            pixelBuf.order(ByteOrder.nativeOrder());

            GLES30.glReadPixels(0, 0, colorWidth, colorWidth, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuf);
            byte[] bytes = pixelBuf.array();
            Log.e("[render service]", "Color value(" + bytes[0] +" " + bytes[1] +" " + bytes[2] +" " + bytes[3] + ")");
        }

        private String getError() {
            int errorCode = EGL14.eglGetError();
            switch (errorCode) {
                case EGL14.EGL_SUCCESS:
                    return "函数执行成功，无错误---没有错误";
                case EGL14.EGL_NOT_INITIALIZED:
                    return "对于特定的 Display, EGL 未初始化，或者不能初始化---没有初始化";

                case EGL14.EGL_BAD_ACCESS:
                    return "EGL 无法访问资源(如 Context 绑定在了其他线程)---访问失败";

                case EGL14.EGL_BAD_ALLOC:
                    return "对于请求的操作，EGL 分配资源失败---分配失败";

                case EGL14.EGL_BAD_ATTRIBUTE:
                    return "未知的属性，或者属性已失效---错误的属性";

                case EGL14.EGL_BAD_CONTEXT:
                    return "EGLContext(上下文) 错误或无效---错误的上下文";

                case EGL14.EGL_BAD_CONFIG:
                    return "EGLConfig(配置) 错误或无效---错误的配置";

                case EGL14.EGL_BAD_DISPLAY:
                    return "EGLDisplay(显示) 错误或无效---错误的显示设备对象";

                case EGL14.EGL_BAD_SURFACE:
                    return ("未知的属性，或者属性已失效---错误的Surface对象");

                case EGL14.EGL_BAD_CURRENT_SURFACE:
                    return ("窗口，缓冲和像素图(三种 Surface)的调用线程的 Surface 错误或无效---当前Surface对象错误");

                case EGL14.EGL_BAD_MATCH:
                    return ("参数不符(如有效的 Context 申请缓冲，但缓冲不是有效的 Surface 提供)---无法匹配");

                case EGL14.EGL_BAD_PARAMETER:
                    return ("错误的参数");

                case EGL14.EGL_BAD_NATIVE_PIXMAP:
                    return ("NativePixmapType 对象未指向有效的本地像素图对象---错误的像素图");

                case EGL14.EGL_BAD_NATIVE_WINDOW:
                    return ("NativeWindowType 对象未指向有效的本地窗口对象---错误的本地窗口对象");

                case EGL14.EGL_CONTEXT_LOST:
                    return ("电源错误事件发生，Open GL重新初始化，上下文等状态重置---上下文丢失");

                default:
                    break;
            }

            return "";
        }

        private void println(String s) {
            System.out.println(s);
        }

    }

    RenderThread m_Thread;

    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(this, "Render service running", Toast.LENGTH_SHORT).show();
        m_Thread = new RenderThread();
        m_Thread.start();

        try {
            for (int i = 0; i < 10; ++i) {
                Thread.sleep(10);
                if (m_Thread.IsValid())
                    break;
            }
        }catch (InterruptedException e)
        {
            e.printStackTrace();;
        }

        m_Thread.Trigger();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        m_Thread.Quit();
    }
}
