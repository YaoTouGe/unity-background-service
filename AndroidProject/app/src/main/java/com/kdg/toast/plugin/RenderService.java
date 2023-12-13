package com.kdg.toast.plugin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.HardwareBuffer;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.opengl.EGL14;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Date;

public class RenderService extends Service{
    public HardwareBuffer colorBuffer = null;
    public int colorTextureId;
    public int colorWidth;
    public int colorHeight;
    private final RemoteCallbackList<HardwareBufferCallback> mCallbacks = new RemoteCallbackList<HardwareBufferCallback>();
    public IBinder onBind(Intent intent) {
        return new RenderServiceResources.Stub() {
            @Override
            public void RegisterHardwareBufferCallback(HardwareBufferCallback callback)
            {
                mCallbacks.register(callback);
                m_Thread.needsCallback[0] = true;
            }

            @Override
            public void UnregisterHardwareBufferCallback(HardwareBufferCallback callback)
            {
                mCallbacks.unregister(callback);
            }
        };
    }

    private class RenderThread extends Thread
    {
        int[] fbo = new int[1];
        int[] rbo = new int[1];

        int program;

        private Handler m_handler;

        public boolean[] needsCallback = new boolean[] {false};

        public void Trigger()
        {
            if (IsValid())
            {
                Log.e("[render service]", "Trigger thread");
                Message.obtain(m_handler, 12345, null).sendToTarget();
            }
        }

        public void Quit()
        {
            Message.obtain(m_handler, 12345, "quit").sendToTarget();
        }

        public boolean IsValid() {return m_handler != null;}

        @Override
        public void run() {
            Looper.prepare();

            if (InitEGLContext() == 0)
            {
                Log.e("[render service]", "Egl init failed!");
                Looper.myLooper().quit();
                return;
            }
            else
            {
                Log.e("[render service]", "Egl init success!");
            }
            colorBuffer = CreateHardwareBuffer(colorWidth, colorHeight);
            colorTextureId = HardwareBufferToGLTexture(colorBuffer);

            // Create FBO from unity color texture
            GLES30.glGenFramebuffers(1, fbo, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0]);

            //GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorAttachmentFromUnity);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, colorTextureId,0);

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
                    if (needsCallback[0])
                    {
                        Log.e("[render service]", "Send call back hardware buffer to client " + colorBuffer + " size " + colorWidth + " " + colorHeight);
                        int cbCount = mCallbacks.beginBroadcast();
                        for (int i = 0; i < cbCount; ++i)
                        {
                            try
                            {
                                mCallbacks.getBroadcastItem(i).ObtainHardwareBuffer(colorBuffer, colorWidth, colorHeight);
                            }
                            catch (RemoteException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        mCallbacks.finishBroadcast();
                        needsCallback[0] = false;
                    }
                    //Log.e("[render service]", "render tick");
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

                    // debug read back
                    ReadPixelAndCheckValue();
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
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
                        Log.e("[render service]", "Egl destroy!");
                        // Destroy everything before quit
                        DestroyEGLContext();
                        Looper.myLooper().quit();
                    }
                    return true;
                }
            });
            Log.e("[render service]", "before Looper.loop()");

            m_Thread.Trigger();
            Looper.loop();
            Log.e("[render service]", "after Looper.loop()");
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
            Log.e("[render service]", "InitShaders success!");
        }

        private void ReadPixelAndCheckValue()
        {
            ByteBuffer pixelBuf = ByteBuffer.allocate(colorWidth * colorHeight * 4);
            pixelBuf.order(ByteOrder.nativeOrder());

            GLES30.glReadPixels(0, 0, colorWidth, colorHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuf);
            byte[] bytes = pixelBuf.array();
            Log.e("[render service]", "Color value(" + bytes[0] +" " + bytes[1] +" " + bytes[2] +" " + bytes[3] + ")");
        }

        private void println(String s) {
            System.out.println(s);
        }

    }

    RenderThread m_Thread;
    private static native int HardwareBufferToGLTexture(HardwareBuffer buffer);

    private static native HardwareBuffer CreateHardwareBuffer(int width, int height);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startNotification();

        colorWidth = 1000;
        colorHeight = 1000;
        m_Thread = new RenderThread();
        m_Thread.start();
        Log.e("[render service]", "thread started");

        return START_NOT_STICKY;
    }

    private native int InitEGLContext();
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
        mCallbacks.kill();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel notificationChannel = new NotificationChannel(
                    "PedometerLib",
                    "Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    private void startNotification(){
        String input = "rendering in background...";
        Notification notification = new NotificationCompat.Builder(this, "PedometerLib")
                .setContentTitle("Background Render Service")
                .setContentText(input)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(112, notification);
    }

}
