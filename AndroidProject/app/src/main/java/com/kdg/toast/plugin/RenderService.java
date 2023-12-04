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
    public static long mainContextHandle = 0;
    public static int colorAttachmentFromUnity = 0;
    public static int colorWidth;
    public static int colorHeight;
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class RenderThread extends Thread
    {
        int[] fbo = new int[1];
        int[] rbo = new int[1];

        int program;

        private Handler m_handler;
        public void Trigger()
        {
            if (IsValid())
                Message.obtain(m_handler, 12345, null).sendToTarget();
        }

        public void Quit()
        {
            Message.obtain(m_handler, 12345, "quit").sendToTarget();
        }

        public boolean IsValid() {return m_handler != null;}

        @Override
        public void run() {
            Looper.prepare();

            if (InitEGLContext(mainContextHandle) == 0)
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

                    loc = GLES30.glGetUniformLocation(program, "fForegroundColor");
                    GLES30.glUniform4f(loc, (float)Math.random(), (float)Math.random(),(float)Math.random(),1);

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
                        DestroyEGLContext();
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
                            "uniform vec4 fForegroundColor;\n"+
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
                            "gl_FragColor = mix(fForegroundColor, vec4(0.2314, 0.7176, 0.4745, 1.0), smoothdist)*fFade;\n"+
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

        private void println(String s) {
            System.out.println(s);
        }

    }

    RenderThread m_Thread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Render service running", Toast.LENGTH_SHORT).show();

        GLResources resources = intent.getParcelableExtra("resource");
        mainContextHandle = resources.GetMainCtx();
        colorWidth = resources.GetWidth();
        colorHeight = resources.GetHeight();
        colorAttachmentFromUnity = resources.GetColorTex();

        Log.i("[render service]", "receive params tex handle " + RenderService.colorAttachmentFromUnity + " ctx handle " + RenderService.mainContextHandle + " extents " + RenderService.colorWidth + " " + RenderService.colorHeight);

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

        return START_NOT_STICKY;
    }

    private native int InitEGLContext(long mainContextHandle);
    private native void DestroyEGLContext();

    @Override
    public void onCreate() {
        super.onCreate();
        System.loadLibrary("EGLNative");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        m_Thread.Quit();
    }
}
