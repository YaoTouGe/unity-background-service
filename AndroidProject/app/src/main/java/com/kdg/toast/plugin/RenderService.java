package com.kdg.toast.plugin;

import android.app.Service;
import android.content.Intent;
import android.opengl.EGLDisplay;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.opengl.EGL14;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class RenderService extends Service {

    public IBinder onBind(Intent intent) {
        return null;
    }

    private class RenderThread extends Thread
    {
        EGLDisplay m_display;
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
        @Override
        public void run() {
            Looper.prepare();
            m_handler = new Handler(Looper.myLooper(), new Handler.Callback()
            {
                @Override
                public boolean handleMessage(@NonNull Message msg)
                {
                    if (msg.what != 12345)
                        return false;

                    Toast.makeText(RenderService.this, "5 secs passed", Toast.LENGTH_SHORT).show();
                    //android.util.Log.e("[render service]", "5 secs passed");
                    try
                    {
                        Thread.sleep(5000);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    // trigger next loop
                    Message.obtain(m_handler, 12345, null).sendToTarget();


                    if (msg.obj != null)
                        Looper.myLooper().quit();
                    return true;
                }
            });
            Looper.loop();
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
